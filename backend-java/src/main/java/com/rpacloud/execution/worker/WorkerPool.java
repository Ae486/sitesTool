package com.rpacloud.execution.worker;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.rpacloud.common.config.RpaProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Browser Worker pool modeled after MyBatis PooledDataSource.
 * <p>
 * Worker = long-lived subprocess holding Playwright + Browser instance.
 * Per-WorkerKey sub-pools with borrow/return/evict lifecycle.
 * <p>
 * Phase 1: pool mechanics only (no real subprocess). Phase 2+ adds actual Process management.
 */
@Slf4j
public class WorkerPool {

    private final Map<WorkerKey, SubPool> pools = new ConcurrentHashMap<>();
    private final Semaphore globalSemaphore;
    private final WorkerFactory factory;
    private final RpaProperties.WorkerPool config;

    // Warm-up targets: key -> minimum idle count to preserve during eviction
    private final Map<WorkerKey, Integer> warmUpTargets = new ConcurrentHashMap<>();

    public WorkerPool(WorkerFactory factory, RpaProperties.WorkerPool config) {
        this.factory = factory;
        this.config = config;
        this.globalSemaphore = new Semaphore(config.getGlobalMaxActive());
    }

    /**
     * Borrow a Worker for the given key. Three-tier strategy:
     * 1. Idle pool has one → take it (0 delay)
     * 2. Sub-pool not full → create new (cold start cost)
     * 3. Sub-pool full → wait for return with timeout
     */
    public WorkerHandle borrow(WorkerKey key, Duration timeout) {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();

        // Global cap check
        boolean globalAcquired;
        try {
            long waitNanos = deadlineNanos - System.nanoTime();
            globalAcquired = globalSemaphore.tryAcquire(Math.max(0, waitNanos), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkerAcquireTimeoutException("Worker borrow interrupted for key " + key);
        }
        if (!globalAcquired) {
            throw new WorkerAcquireTimeoutException(
                    "Global worker limit reached (" + config.getGlobalMaxActive() + "), key=" + key);
        }

        SubPool sub = pools.computeIfAbsent(key, k -> new SubPool(config.getMaxActivePerKey()));
        sub.lock.lock();
        try {
            while (true) {
                // 1. Try idle
                WorkerHandle w = sub.idle.pollFirst();
                if (w != null) {
                    if (w.isHealthy()) {
                        sub.active.add(w);
                        log.info("Worker {} borrowed from idle for key {}, idle={} active={}",
                                w.getId(), key, sub.idle.size(), sub.active.size());
                        return w;
                    }
                    // Unhealthy idle worker — destroy and retry
                    w.destroy();
                    globalSemaphore.release();
                    // Re-acquire global permit for the next attempt
                    if (!tryReacquireGlobal(deadlineNanos)) {
                        throw new WorkerAcquireTimeoutException("Global limit after destroying unhealthy worker, key=" + key);
                    }
                    continue;
                }

                // 2. Create new if sub-pool not full
                if (sub.active.size() < sub.maxActive) {
                    WorkerHandle fresh = factory.create(key);
                    sub.active.add(fresh);
                    log.info("Worker {} created for key {}, idle={} active={}",
                            fresh.getId(), key, sub.idle.size(), sub.active.size());
                    return fresh;
                }

                // 3. Wait for return
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    globalSemaphore.release();
                    throw new WorkerAcquireTimeoutException(
                            "No worker available within " + timeout.toMillis() + "ms for key " + key
                                    + ", active=" + sub.active.size());
                }
                try {
                    sub.notEmpty.awaitNanos(remainingNanos);
                } catch (InterruptedException e) {
                    globalSemaphore.release();
                    Thread.currentThread().interrupt();
                    throw new WorkerAcquireTimeoutException("Worker borrow interrupted for key " + key);
                }
            }
        } finally {
            sub.lock.unlock();
        }
    }

    /**
     * Return a Worker after task completion. Eviction check on return:
     * - taskCount >= maxTasksPerWorker → destroy
     * - ageMinutes >= maxLifetimeMinutes → destroy
     * - !isHealthy() → destroy
     * Otherwise returns to idle pool.
     */
    public void returnWorker(WorkerHandle worker) {
        SubPool sub = pools.get(worker.getKey());
        if (sub == null) {
            worker.destroy();
            globalSemaphore.release();
            return;
        }

        sub.lock.lock();
        try {
            sub.active.remove(worker);
            worker.incrementTaskCount();
            worker.setLastReturnAt(System.currentTimeMillis());

            boolean shouldEvict = worker.getTaskCount() >= config.getMaxTasksPerWorker()
                    || worker.ageMinutes() >= config.getMaxLifetimeMinutes()
                    || !worker.isHealthy();

            if (shouldEvict) {
                worker.destroy();
                globalSemaphore.release();
                log.info("Worker {} evicted on return (tasks={}, age={}min, healthy={}), key={}",
                        worker.getId(), worker.getTaskCount(), worker.ageMinutes(),
                        worker.isHealthy(), worker.getKey());
            } else {
                sub.idle.addFirst(worker);
                log.info("Worker {} returned to idle, key={}, idle={} active={}",
                        worker.getId(), worker.getKey(), sub.idle.size(), sub.active.size());
            }
            sub.notEmpty.signal();
        } finally {
            sub.lock.unlock();
        }
    }

    /** Force-destroy a worker (e.g. on crash detection). */
    public void destroyWorker(WorkerHandle worker) {
        SubPool sub = pools.get(worker.getKey());
        if (sub != null) {
            sub.lock.lock();
            try {
                sub.active.remove(worker);
                sub.idle.remove(worker);
                sub.notEmpty.signal();
            } finally {
                sub.lock.unlock();
            }
        }
        worker.destroy();
        globalSemaphore.release();
    }

    /**
     * Periodic eviction of idle workers that exceed maxIdleMinutes.
     * Respects warm-up minimum: won't evict below warmUpTarget for each key.
     */
    public void evictIdle() {
        for (var entry : pools.entrySet()) {
            WorkerKey key = entry.getKey();
            SubPool sub = entry.getValue();
            int warmUpMin = warmUpTargets.getOrDefault(key, 0);
            List<WorkerHandle> toDestroy = new ArrayList<>();

            sub.lock.lock();
            try {
                int currentIdle = sub.idle.size();
                Iterator<WorkerHandle> it = sub.idle.iterator();
                while (it.hasNext()) {
                    WorkerHandle w = it.next();
                    boolean idleTooLong = w.idleMinutes() >= config.getMaxIdleMinutes();
                    if (idleTooLong && currentIdle > warmUpMin) {
                        it.remove();
                        toDestroy.add(w);
                        currentIdle--;
                    }
                }
            } finally {
                sub.lock.unlock();
            }

            for (WorkerHandle w : toDestroy) {
                w.destroy();
                globalSemaphore.release();
                log.info("Worker {} evicted (idle too long), key={}", w.getId(), key);
            }
        }
    }

    /** Pre-create workers into idle pool. */
    public void warmUp(WorkerKey key, int count) {
        warmUpTargets.merge(key, count, Integer::max);
        SubPool sub = pools.computeIfAbsent(key, k -> new SubPool(config.getMaxActivePerKey()));
        int created = 0;
        for (int i = 0; i < count; i++) {
            if (!globalSemaphore.tryAcquire()) {
                log.warn("warmUp: global limit reached after creating {} workers for key {}", created, key);
                break;
            }
            WorkerHandle w = factory.create(key);
            w.setLastReturnAt(System.currentTimeMillis());
            sub.lock.lock();
            try {
                sub.idle.addLast(w);
            } finally {
                sub.lock.unlock();
            }
            created++;
        }
        log.info("Warmed up {} workers for key {}", created, key);
    }

    public int idleCount(WorkerKey key) {
        SubPool sub = pools.get(key);
        if (sub == null) return 0;
        sub.lock.lock();
        try { return sub.idle.size(); } finally { sub.lock.unlock(); }
    }

    public int activeCount(WorkerKey key) {
        SubPool sub = pools.get(key);
        if (sub == null) return 0;
        sub.lock.lock();
        try { return sub.active.size(); } finally { sub.lock.unlock(); }
    }

    public int totalIdle() {
        int total = 0;
        for (SubPool sub : pools.values()) {
            sub.lock.lock();
            try { total += sub.idle.size(); } finally { sub.lock.unlock(); }
        }
        return total;
    }

    public int totalActive() {
        int total = 0;
        for (SubPool sub : pools.values()) {
            sub.lock.lock();
            try { total += sub.active.size(); } finally { sub.lock.unlock(); }
        }
        return total;
    }

    @PreDestroy
    public void shutdown() {
        for (var entry : pools.entrySet()) {
            SubPool sub = entry.getValue();
            sub.lock.lock();
            try {
                for (WorkerHandle w : sub.idle) { w.destroy(); globalSemaphore.release(); }
                for (WorkerHandle w : sub.active) { w.destroy(); globalSemaphore.release(); }
                sub.idle.clear();
                sub.active.clear();
            } finally {
                sub.lock.unlock();
            }
        }
        pools.clear();
        log.info("WorkerPool shutdown complete");
    }

    private boolean tryReacquireGlobal(long deadlineNanos) {
        long waitNanos = deadlineNanos - System.nanoTime();
        if (waitNanos <= 0) return false;
        try {
            return globalSemaphore.tryAcquire(waitNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Per-WorkerKey sub-pool with its own lock and idle/active sets. */
    static class SubPool {
        final Deque<WorkerHandle> idle = new ArrayDeque<>();
        final Set<WorkerHandle> active = new HashSet<>();
        final int maxActive;
        final ReentrantLock lock = new ReentrantLock();
        final Condition notEmpty = lock.newCondition();

        SubPool(int maxActive) {
            this.maxActive = maxActive;
        }
    }
}
