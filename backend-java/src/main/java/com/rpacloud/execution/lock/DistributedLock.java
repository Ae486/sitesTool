package com.rpacloud.execution.lock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class DistributedLock {

    private final StringRedisTemplate redis;
    private final ScheduledExecutorService watchdogPool;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> watchdogs = new ConcurrentHashMap<>();

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final String RENEW_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end";

    public DistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1);
        pool.setRemoveOnCancelPolicy(true);
        this.watchdogPool = pool;
    }

    public boolean tryLock(String key, String value, int seconds) {
        Boolean acquired = redis.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(seconds));
        if (Boolean.TRUE.equals(acquired)) {
            startWatchdog(key, value, seconds);
            return true;
        }
        return false;
    }

    public boolean unlock(String key, String value) {
        cancelWatchdog(key);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        Long result = redis.execute(script, List.of(key), value);
        return result != null && result == 1;
    }

    private void startWatchdog(String key, String value, int seconds) {
        long renewInterval = Math.max(seconds * 1000L / 3, 1000);
        long ttlMs = seconds * 1000L;
        ScheduledFuture<?> future = watchdogPool.scheduleAtFixedRate(() -> {
            try {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>(RENEW_SCRIPT, Long.class);
                Long result = redis.execute(script, List.of(key), value, String.valueOf(ttlMs));
                if (result == null || result == 0) {
                    log.warn("Watchdog renewal failed for key={}, lock may have been released", key);
                    cancelWatchdog(key);
                }
            } catch (Exception e) {
                log.error("Watchdog error for key={}: {}", key, e.getMessage());
            }
        }, renewInterval, renewInterval, TimeUnit.MILLISECONDS);
        watchdogs.put(key, future);
    }

    private void cancelWatchdog(String key) {
        ScheduledFuture<?> future = watchdogs.remove(key);
        if (future != null) {
            future.cancel(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        watchdogs.values().forEach(f -> f.cancel(false));
        watchdogs.clear();
        watchdogPool.shutdown();
    }
}
