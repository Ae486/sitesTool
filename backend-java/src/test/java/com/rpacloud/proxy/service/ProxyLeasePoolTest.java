package com.rpacloud.proxy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.proxy.entity.Proxy;
import com.rpacloud.proxy.repository.ProxyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProxyLeasePoolTest {

    @Mock ProxyRepository proxyRepository;
    @Mock ProxyPoolService proxyPoolService;

    private RpaProperties rpaProperties;
    private ProxyLeasePool pool;

    @BeforeEach
    void setUp() {
        rpaProperties = new RpaProperties();
        rpaProperties.getProxy().setProxyAcquireTimeoutMs(30000L);
        rpaProperties.getProxy().setProxyMaxLeaseMs(360000L);
        pool = new ProxyLeasePool(proxyRepository, proxyPoolService, rpaProperties);
    }

    private Proxy buildProxy(Long id, String ip, int port, int successCount, int failCount, int avgLatencyMs) {
        return Proxy.builder()
                .id(id).ip(ip).port(port).protocol("HTTP")
                .isActive(true).successCount(successCount).failCount(failCount)
                .avgLatencyMs(avgLatencyMs).build();
    }

    private void loadProxies(Proxy... proxies) {
        when(proxyRepository.findAllByIsActiveTrue()).thenReturn(List.of(proxies));
        pool.init();
    }

    @Test
    void checkout_returnsHighestScore() {
        Proxy low = buildProxy(1L, "1.1.1.1", 8001, 50, 50, 500);
        Proxy high = buildProxy(2L, "2.2.2.2", 8002, 99, 1, 50);
        loadProxies(low, high);

        ProxyLease lease = pool.checkout("exec-1", Duration.ofSeconds(5));
        assertThat(lease.getProxyId()).isEqualTo(2L);
    }

    @Test
    void checkout_concurrent_differentProxies() {
        Proxy p1 = buildProxy(1L, "1.1.1.1", 8001, 90, 10, 100);
        Proxy p2 = buildProxy(2L, "2.2.2.2", 8002, 80, 20, 100);
        Proxy p3 = buildProxy(3L, "3.3.3.3", 8003, 70, 30, 100);
        loadProxies(p1, p2, p3);

        ProxyLease l1 = pool.checkout("exec-1", Duration.ofSeconds(5));
        ProxyLease l2 = pool.checkout("exec-2", Duration.ofSeconds(5));
        ProxyLease l3 = pool.checkout("exec-3", Duration.ofSeconds(5));

        assertThat(Set.of(l1.getProxyId(), l2.getProxyId(), l3.getProxyId())).hasSize(3);
        assertThat(pool.idleCount()).isZero();
        assertThat(pool.activeCount()).isEqualTo(3);
    }

    @Test
    void checkout_timeoutThrows() {
        Proxy p1 = buildProxy(1L, "1.1.1.1", 8001, 90, 10, 100);
        loadProxies(p1);
        pool.checkout("exec-1", Duration.ofSeconds(5));

        assertThatThrownBy(() -> pool.checkout("exec-2", Duration.ofMillis(100)))
                .isInstanceOf(ProxyAcquireTimeoutException.class);
    }

    @Test
    void checkout_waitsWhenAllBusy() throws Exception {
        Proxy p1 = buildProxy(1L, "1.1.1.1", 8001, 90, 10, 100);
        loadProxies(p1);
        lenient().when(proxyRepository.findById(1L)).thenReturn(Optional.of(p1));

        pool.checkout("exec-1", Duration.ofSeconds(5));
        CountDownLatch acquired = new CountDownLatch(1);

        CompletableFuture.runAsync(() -> {
            ProxyLease lease = pool.checkout("exec-2", Duration.ofSeconds(5));
            assertThat(lease.getProxyId()).isEqualTo(1L);
            acquired.countDown();
        });

        Thread.sleep(50);
        pool.returnProxy("exec-1", new ProxyUseResult(true, 200, List.of()));
        assertThat(acquired.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void return_success_putsBackToIdle() {
        Proxy p1 = buildProxy(1L, "1.1.1.1", 8001, 90, 10, 100);
        loadProxies(p1);
        when(proxyRepository.findById(1L)).thenReturn(Optional.of(p1));

        pool.checkout("exec-1", Duration.ofSeconds(5));
        assertThat(pool.idleCount()).isZero();

        pool.returnProxy("exec-1", new ProxyUseResult(true, 200, List.of()));
        assertThat(pool.idleCount()).isEqualTo(1);
        assertThat(pool.activeCount()).isZero();
    }

    @Test
    void return_networkError_discardsProxy() {
        Proxy p1 = buildProxy(1L, "1.1.1.1", 8001, 90, 10, 100);
        loadProxies(p1);

        pool.checkout("exec-1", Duration.ofSeconds(5));
        pool.returnProxy("exec-1", new ProxyUseResult(false, 5000, List.of("NETWORK_ERROR")));

        assertThat(pool.idleCount()).isZero();
        assertThat(pool.activeCount()).isZero();
        verify(proxyPoolService).updateScore(eq(1L), eq(false), eq(5000), any(), any());
    }

    @Test
    void return_unrelatedError_putsBackToIdle() {
        Proxy p1 = buildProxy(1L, "1.1.1.1", 8001, 90, 10, 100);
        loadProxies(p1);
        when(proxyRepository.findById(1L)).thenReturn(Optional.of(p1));

        pool.checkout("exec-1", Duration.ofSeconds(5));
        pool.returnProxy("exec-1", new ProxyUseResult(false, 200, List.of("SELECTOR_NOT_FOUND")));

        assertThat(pool.idleCount()).isEqualTo(1);
        verify(proxyPoolService).updateScore(eq(1L), eq(true), eq(200), any(), any());
    }

    @Test
    void reclaimOverdue_removesExpiredLeases() throws Exception {
        rpaProperties.getProxy().setProxyMaxLeaseMs(50L);
        Proxy p1 = buildProxy(1L, "1.1.1.1", 8001, 90, 10, 100);
        loadProxies(p1);
        lenient().when(proxyRepository.findById(1L)).thenReturn(Optional.of(p1));

        pool.checkout("exec-1", Duration.ofSeconds(5));
        assertThat(pool.activeCount()).isEqualTo(1);

        Thread.sleep(100); // ensure lease exceeds maxLeaseMs
        pool.reclaimOverdue();
        assertThat(pool.activeCount()).isZero();
        assertThat(pool.idleCount()).isEqualTo(1);
    }

    @Test
    void markUnavailable_removesFromIdle() {
        Proxy p1 = buildProxy(1L, "1.1.1.1", 8001, 90, 10, 100);
        Proxy p2 = buildProxy(2L, "2.2.2.2", 8002, 80, 20, 100);
        loadProxies(p1, p2);

        pool.markUnavailable(1L);
        assertThat(pool.idleCount()).isEqualTo(1);

        ProxyLease lease = pool.checkout("exec-1", Duration.ofSeconds(5));
        assertThat(lease.getProxyId()).isEqualTo(2L);
    }

    @Test
    void emptyPool_throwsImmediately() {
        when(proxyRepository.findAllByIsActiveTrue()).thenReturn(List.of());
        pool.init();

        assertThatThrownBy(() -> pool.checkout("exec-1", Duration.ofMillis(100)))
                .isInstanceOf(ProxyAcquireTimeoutException.class);
    }

    @Test
    void failureCategory_fromErrorTypes() {
        assertThat(FailureCategory.fromErrorTypes(null)).isEqualTo(FailureCategory.NONE);
        assertThat(FailureCategory.fromErrorTypes(List.of())).isEqualTo(FailureCategory.NONE);
        assertThat(FailureCategory.fromErrorTypes(List.of("NETWORK_ERROR"))).isEqualTo(FailureCategory.NETWORK_ERROR);
        assertThat(FailureCategory.fromErrorTypes(List.of("NAVIGATION_ERROR"))).isEqualTo(FailureCategory.NETWORK_ERROR);
        assertThat(FailureCategory.fromErrorTypes(List.of("TIMEOUT"))).isEqualTo(FailureCategory.TIMEOUT);
        assertThat(FailureCategory.fromErrorTypes(List.of("PROCESS_TIMEOUT"))).isEqualTo(FailureCategory.TIMEOUT);
        assertThat(FailureCategory.fromErrorTypes(List.of("SELECTOR_NOT_FOUND"))).isEqualTo(FailureCategory.UNRELATED);
        assertThat(FailureCategory.fromErrorTypes(List.of("JS_ERROR"))).isEqualTo(FailureCategory.UNRELATED);
    }
}
