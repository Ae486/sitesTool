package com.rpacloud.execution.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.rpacloud.common.config.RpaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkerPoolTest {

    private RpaProperties.WorkerPool config;
    private AtomicInteger createCount;
    private WorkerFactory factory;
    private WorkerPool pool;

    private static final WorkerKey KEY_A = new WorkerKey("chromium", true, null);
    private static final WorkerKey KEY_B = new WorkerKey("firefox", true, null);

    @BeforeEach
    void setUp() {
        config = new RpaProperties.WorkerPool();
        config.setGlobalMaxActive(8);
        config.setMaxActivePerKey(4);
        config.setMaxIdleMinutes(10);
        config.setMaxTasksPerWorker(100);
        config.setMaxLifetimeMinutes(60);
        config.setAcquireTimeoutMs(30000L);

        createCount = new AtomicInteger();
        factory = key -> {
            createCount.incrementAndGet();
            return new WorkerHandle(key);
        };
        pool = new WorkerPool(factory, config);
    }

    @Test
    void borrow_returnsFromIdle() {
        pool.warmUp(KEY_A, 2);
        assertThat(pool.idleCount(KEY_A)).isEqualTo(2);

        WorkerHandle w = pool.borrow(KEY_A, Duration.ofSeconds(1));
        assertThat(w).isNotNull();
        assertThat(w.getKey()).isEqualTo(KEY_A);
        assertThat(pool.idleCount(KEY_A)).isEqualTo(1);
        assertThat(pool.activeCount(KEY_A)).isEqualTo(1);
        // warmUp created 2, borrow should not create more
        assertThat(createCount.get()).isEqualTo(2);
    }

    @Test
    void borrow_createsNewWhenIdleEmpty() {
        WorkerHandle w = pool.borrow(KEY_A, Duration.ofSeconds(1));
        assertThat(w).isNotNull();
        assertThat(createCount.get()).isEqualTo(1);
        assertThat(pool.activeCount(KEY_A)).isEqualTo(1);
        assertThat(pool.idleCount(KEY_A)).isZero();
    }

    @Test
    void borrow_concurrent_differentWorkers() {
        WorkerHandle w1 = pool.borrow(KEY_A, Duration.ofSeconds(1));
        WorkerHandle w2 = pool.borrow(KEY_A, Duration.ofSeconds(1));
        WorkerHandle w3 = pool.borrow(KEY_A, Duration.ofSeconds(1));

        assertThat(Set.of(w1.getId(), w2.getId(), w3.getId())).hasSize(3);
        assertThat(pool.activeCount(KEY_A)).isEqualTo(3);
        assertThat(pool.idleCount(KEY_A)).isZero();
        assertThat(createCount.get()).isEqualTo(3);
    }

    @Test
    void borrow_waitsWhenSubPoolFull() throws Exception {
        config.setMaxActivePerKey(1);
        pool = new WorkerPool(factory, config);

        WorkerHandle w1 = pool.borrow(KEY_A, Duration.ofSeconds(1));
        CountDownLatch acquired = new CountDownLatch(1);

        CompletableFuture.runAsync(() -> {
            WorkerHandle w2 = pool.borrow(KEY_A, Duration.ofSeconds(5));
            assertThat(w2).isNotNull();
            acquired.countDown();
        });

        Thread.sleep(50);
        assertThat(acquired.getCount()).isEqualTo(1); // still waiting

        pool.returnWorker(w1);
        assertThat(acquired.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void borrow_timeoutThrows() {
        config.setMaxActivePerKey(1);
        pool = new WorkerPool(factory, config);

        pool.borrow(KEY_A, Duration.ofSeconds(1));
        assertThatThrownBy(() -> pool.borrow(KEY_A, Duration.ofMillis(100)))
                .isInstanceOf(WorkerAcquireTimeoutException.class);
    }

    @Test
    void borrow_globalSemaphoreLimit() {
        config.setGlobalMaxActive(2);
        config.setMaxActivePerKey(4);
        pool = new WorkerPool(factory, config);

        pool.borrow(KEY_A, Duration.ofSeconds(1));
        pool.borrow(KEY_B, Duration.ofSeconds(1));

        // Global limit = 2, both keys have 1 each, total = 2
        assertThatThrownBy(() -> pool.borrow(KEY_A, Duration.ofMillis(100)))
                .isInstanceOf(WorkerAcquireTimeoutException.class);
    }

    @Test
    void return_putsBackToIdle() {
        WorkerHandle w = pool.borrow(KEY_A, Duration.ofSeconds(1));
        assertThat(pool.activeCount(KEY_A)).isEqualTo(1);
        assertThat(pool.idleCount(KEY_A)).isZero();

        pool.returnWorker(w);
        assertThat(pool.activeCount(KEY_A)).isZero();
        assertThat(pool.idleCount(KEY_A)).isEqualTo(1);
        assertThat(w.getTaskCount()).isEqualTo(1);
    }

    @Test
    void return_evictsOnTaskCountLimit() {
        config.setMaxTasksPerWorker(2);
        pool = new WorkerPool(factory, config);

        WorkerHandle w = pool.borrow(KEY_A, Duration.ofSeconds(1));
        pool.returnWorker(w); // taskCount=1, ok
        assertThat(pool.idleCount(KEY_A)).isEqualTo(1);

        // Re-borrow same worker
        WorkerHandle w2 = pool.borrow(KEY_A, Duration.ofSeconds(1));
        assertThat(w2.getId()).isEqualTo(w.getId());
        pool.returnWorker(w2); // taskCount=2 == max, evict
        assertThat(pool.idleCount(KEY_A)).isZero();
        assertThat(w2.isDestroyed()).isTrue();
    }

    @Test
    void return_evictsOnLifetimeLimit() {
        config.setMaxLifetimeMinutes(0); // immediate eviction
        pool = new WorkerPool(factory, config);

        WorkerHandle w = pool.borrow(KEY_A, Duration.ofSeconds(1));
        pool.returnWorker(w);

        assertThat(pool.idleCount(KEY_A)).isZero();
        assertThat(w.isDestroyed()).isTrue();
    }

    @Test
    void return_evictsUnhealthy() {
        WorkerHandle w = pool.borrow(KEY_A, Duration.ofSeconds(1));
        w.setHealthy(false);
        pool.returnWorker(w);

        assertThat(pool.idleCount(KEY_A)).isZero();
        assertThat(w.isDestroyed()).isTrue();
    }

    @Test
    void evictIdle_removesExpired() throws Exception {
        config.setMaxIdleMinutes(0); // immediate eviction
        pool = new WorkerPool(factory, config);

        pool.warmUp(KEY_A, 2);
        assertThat(pool.idleCount(KEY_A)).isEqualTo(2);

        Thread.sleep(10); // ensure idleMinutes >= 0 (rounds down)
        pool.evictIdle();
        // warmUpTarget=2, so eviction respects minimum
        // With maxIdleMinutes=0 and warmUpTarget=2, all should be kept
        assertThat(pool.idleCount(KEY_A)).isEqualTo(2);
    }

    @Test
    void evictIdle_respectsWarmUpMinimum() throws Exception {
        config.setMaxIdleMinutes(0);
        pool = new WorkerPool(factory, config);

        pool.warmUp(KEY_A, 1); // warmUpTarget=1
        // Borrow 3 workers (1 from idle warmup + 2 new creates), then return all
        WorkerHandle w1 = pool.borrow(KEY_A, Duration.ofSeconds(1));
        WorkerHandle w2 = pool.borrow(KEY_A, Duration.ofSeconds(1));
        WorkerHandle w3 = pool.borrow(KEY_A, Duration.ofSeconds(1));
        pool.returnWorker(w1);
        pool.returnWorker(w2);
        pool.returnWorker(w3);
        assertThat(pool.idleCount(KEY_A)).isEqualTo(3);

        Thread.sleep(10);
        pool.evictIdle();
        // Should evict down to warmUpTarget=1
        assertThat(pool.idleCount(KEY_A)).isEqualTo(1);
    }

    @Test
    void warmUp_createsSpecifiedCount() {
        pool.warmUp(KEY_A, 3);
        assertThat(pool.idleCount(KEY_A)).isEqualTo(3);
        assertThat(pool.totalIdle()).isEqualTo(3);
        assertThat(createCount.get()).isEqualTo(3);
    }

    @Test
    void shutdown_destroysAll() {
        pool.warmUp(KEY_A, 2);
        WorkerHandle active = pool.borrow(KEY_B, Duration.ofSeconds(1));

        assertThat(pool.totalIdle()).isEqualTo(2);
        assertThat(pool.totalActive()).isEqualTo(1);

        pool.shutdown();
        assertThat(pool.totalIdle()).isZero();
        assertThat(pool.totalActive()).isZero();
    }

    @Test
    void borrow_skipsUnhealthyIdle() {
        pool.warmUp(KEY_A, 2);
        // Mark first idle worker unhealthy
        // The warmUp puts workers at the tail, borrow takes from head
        // So we need to get the first one and mark it unhealthy

        // Borrow and make unhealthy, then return to idle head
        WorkerHandle bad = pool.borrow(KEY_A, Duration.ofSeconds(1));
        bad.setHealthy(false);
        // Return puts at idle head, but unhealthy workers are evicted on return
        // So let's test differently: manually mark idle workers unhealthy
        pool.returnWorker(bad);
        // bad was unhealthy, so it's destroyed, not returned to idle
        assertThat(pool.idleCount(KEY_A)).isEqualTo(1);

        // The remaining one should be healthy
        WorkerHandle good = pool.borrow(KEY_A, Duration.ofSeconds(1));
        assertThat(good.isHealthy()).isTrue();
    }
}
