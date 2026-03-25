package com.rpacloud.proxy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rpacloud.proxy.service.GeoIpService;

import java.util.List;
import java.util.concurrent.Executor;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.proxy.entity.Proxy;
import com.rpacloud.proxy.entity.ProxyHealthLog;
import com.rpacloud.proxy.repository.ProxyHealthLogRepository;
import com.rpacloud.proxy.repository.ProxyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProxyHealthCheckerTest {

    @Mock private ProxyRepository proxyRepository;
    @Mock private ProxyHealthLogRepository proxyHealthLogRepository;
    @Mock private ProxyPoolService proxyPoolService;
    @Mock private GeoIpService geoIpService;

    private RpaProperties rpaProperties;
    private ProxyHealthChecker proxyHealthChecker;

    @BeforeEach
    void setUp() {
        rpaProperties = new RpaProperties();
        rpaProperties.setProxy(new RpaProperties.Proxy());
        rpaProperties.getProxy().setMaxConcurrentChecks(2);

        // Direct executor: runs task in the calling thread (synchronous for tests)
        Executor directExecutor = Runnable::run;

        proxyHealthChecker = new ProxyHealthChecker(
                proxyRepository,
                proxyHealthLogRepository,
                proxyPoolService,
                geoIpService,
                rpaProperties,
                directExecutor
        );
    }

    @Test
    void checkAll_noActiveProxies_doesNothing() {
        when(proxyRepository.findAllByIsActiveTrue()).thenReturn(List.of());

        proxyHealthChecker.checkAll();

        verify(proxyHealthLogRepository, never()).save(any());
        verify(proxyPoolService, never()).updateScore(anyLong(), anyBoolean(), anyInt(), any(), any());
    }

    @Test
    void checkAll_logsHealthCheckResult() {
        Proxy proxy = Proxy.builder()
                .id(1L).ip("127.0.0.1").port(1).isActive(true).build();
        when(proxyRepository.findAllByIsActiveTrue()).thenReturn(List.of(proxy));
        // updateScore will be called; it's mocked so no real DB needed
        doNothing().when(proxyPoolService).updateScore(anyLong(), anyBoolean(), anyInt(), any(), any());

        proxyHealthChecker.checkAll();

        // Health check to 127.0.0.1:1 will fail (connection refused), so:
        ArgumentCaptor<ProxyHealthLog> logCaptor = ArgumentCaptor.forClass(ProxyHealthLog.class);
        verify(proxyHealthLogRepository).save(logCaptor.capture());
        ProxyHealthLog saved = logCaptor.getValue();

        assertThat(saved.getProxy()).isEqualTo(proxy);
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getLatencyMs()).isPositive();
        assertThat(saved.getErrorMessage()).isNotNull();
    }

    @Test
    void checkAll_updatesScoreAfterCheck() {
        Proxy proxy = Proxy.builder()
                .id(5L).ip("127.0.0.1").port(1).isActive(true).build();
        when(proxyRepository.findAllByIsActiveTrue()).thenReturn(List.of(proxy));
        doNothing().when(proxyPoolService).updateScore(anyLong(), anyBoolean(), anyInt(), any(), any());

        proxyHealthChecker.checkAll();

        verify(proxyPoolService).updateScore(eq(Long.valueOf(5L)), eq(false), anyInt(), any(), any());
    }

    @Test
    void checkAll_multipleProxies_checksAll() {
        Proxy p1 = Proxy.builder().id(1L).ip("127.0.0.1").port(1).isActive(true).build();
        Proxy p2 = Proxy.builder().id(2L).ip("127.0.0.1").port(2).isActive(true).build();
        when(proxyRepository.findAllByIsActiveTrue()).thenReturn(List.of(p1, p2));
        doNothing().when(proxyPoolService).updateScore(anyLong(), anyBoolean(), anyInt(), any(), any());

        proxyHealthChecker.checkAll();

        // Both proxies should produce health logs
        verify(proxyHealthLogRepository, atLeastOnce()).save(any());
        // verify called at least for both proxy IDs
        verify(proxyPoolService).updateScore(eq(Long.valueOf(1L)), anyBoolean(), anyInt(), any(), any());
        verify(proxyPoolService).updateScore(eq(Long.valueOf(2L)), anyBoolean(), anyInt(), any(), any());
    }

    @Test
    void checkAll_updateScoreThrows_doesNotPropagateException() {
        Proxy proxy = Proxy.builder()
                .id(1L).ip("127.0.0.1").port(1).isActive(true).build();
        when(proxyRepository.findAllByIsActiveTrue()).thenReturn(List.of(proxy));
        doThrow(new RuntimeException("DB down")).when(proxyPoolService)
                .updateScore(anyLong(), anyBoolean(), anyInt(), any(), any());

        // Should not throw — error is caught in checkOne
        proxyHealthChecker.checkAll();

        verify(proxyHealthLogRepository).save(any());
    }
}
