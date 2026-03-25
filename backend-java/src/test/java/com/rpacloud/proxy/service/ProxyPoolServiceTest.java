package com.rpacloud.proxy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.proxy.dto.ProxyResponse;
import com.rpacloud.proxy.entity.Proxy;
import com.rpacloud.proxy.provider.ProxyProviderAdapter;
import com.rpacloud.proxy.provider.ProxyProviderAdapter.ProxyInfo;
import com.rpacloud.proxy.repository.ProxyHealthLogRepository;
import com.rpacloud.proxy.repository.ProxyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class ProxyPoolServiceTest {

    @Mock private ProxyRepository proxyRepository;
    @Mock private ProxyHealthLogRepository proxyHealthLogRepository;
    @Mock private ProxyProviderAdapter providerAdapter;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ZSetOperations<String, String> zSetOps;
    @Mock private ValueOperations<String, String> valueOps;

    private RpaProperties rpaProperties;
    private ProxyPoolService proxyPoolService;

    @BeforeEach
    void setUp() {
        rpaProperties = new RpaProperties();
        rpaProperties.setProxy(new RpaProperties.Proxy());
        rpaProperties.getProxy().setCooldownSeconds(60);

        lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);

        proxyPoolService = new ProxyPoolService(
                proxyRepository,
                proxyHealthLogRepository,
                List.of(providerAdapter),
                stringRedisTemplate,
                rpaProperties
        );
    }

    // ── getBestProxy ──────────────────────────────────────────

    @Test
    void getBestProxy_emptyPool_returnsEmpty() {
        when(zSetOps.reverseRange("proxy:pool", 0, 0)).thenReturn(Set.of());
        assertThat(proxyPoolService.getBestProxy()).isEmpty();
    }

    @Test
    void getBestProxy_nullPool_returnsEmpty() {
        when(zSetOps.reverseRange("proxy:pool", 0, 0)).thenReturn(null);
        assertThat(proxyPoolService.getBestProxy()).isEmpty();
    }

    @Test
    void getBestProxy_invalidMember_removesAndReturnsEmpty() {
        when(zSetOps.reverseRange("proxy:pool", 0, 0)).thenReturn(Set.of("not-a-number"));
        assertThat(proxyPoolService.getBestProxy()).isEmpty();
        verify(zSetOps).remove("proxy:pool", "not-a-number");
    }

    @Test
    void getBestProxy_cooldownActive_returnsEmpty() {
        when(zSetOps.reverseRange("proxy:pool", 0, 0)).thenReturn(Set.of("1"));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("proxy:cooldown:1"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        assertThat(proxyPoolService.getBestProxy()).isEmpty();
    }

    @Test
    void getBestProxy_success_returnsActiveProxy() {
        Proxy proxy = Proxy.builder().id(1L).ip("1.2.3.4").port(8080).isActive(true).build();
        when(zSetOps.reverseRange("proxy:pool", 0, 0)).thenReturn(Set.of("1"));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("proxy:cooldown:1"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(proxyRepository.findById(1L)).thenReturn(Optional.of(proxy));

        Optional<Proxy> result = proxyPoolService.getBestProxy();
        assertThat(result).isPresent();
        assertThat(result.get().getIp()).isEqualTo("1.2.3.4");
    }

    @Test
    void getBestProxy_inactiveProxy_returnsEmpty() {
        Proxy proxy = Proxy.builder().id(1L).ip("1.2.3.4").port(8080).isActive(false).build();
        when(zSetOps.reverseRange("proxy:pool", 0, 0)).thenReturn(Set.of("1"));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("proxy:cooldown:1"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(proxyRepository.findById(1L)).thenReturn(Optional.of(proxy));

        assertThat(proxyPoolService.getBestProxy()).isEmpty();
    }

    // ── updateScore ───────────────────────────────────────────

    @Test
    void updateScore_success_incrementsSuccessAndUpdatesAvgLatency() {
        Proxy proxy = Proxy.builder()
                .id(1L).ip("1.2.3.4").port(8080).isActive(true)
                .successCount(4).failCount(0).avgLatencyMs(100).build();
        when(proxyRepository.findById(1L)).thenReturn(Optional.of(proxy));
        when(proxyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        proxyPoolService.updateScore(1L, true, 200, null, null);

        assertThat(proxy.getSuccessCount()).isEqualTo(5);
        // avgLatency = (100*4 + 200) / 5 = 120
        assertThat(proxy.getAvgLatencyMs()).isEqualTo(120);
        assertThat(proxy.getLastCheckSuccess()).isTrue();
        verify(proxyRepository).save(proxy);
        verify(zSetOps).add(eq("proxy:pool"), eq("1"), anyDouble());
    }

    @Test
    void updateScore_failure_incrementsFailCount() {
        Proxy proxy = Proxy.builder()
                .id(1L).ip("1.2.3.4").port(8080).isActive(true)
                .successCount(3).failCount(1).avgLatencyMs(100).build();
        when(proxyRepository.findById(1L)).thenReturn(Optional.of(proxy));
        when(proxyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        proxyPoolService.updateScore(1L, false, 0, null, null);

        assertThat(proxy.getFailCount()).isEqualTo(2);
        assertThat(proxy.getSuccessCount()).isEqualTo(3); // unchanged
        assertThat(proxy.getLastCheckSuccess()).isFalse();
    }

    @Test
    void updateScore_notFound_throwsBizException() {
        when(proxyRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> proxyPoolService.updateScore(999L, true, 100, null, null))
                .isInstanceOf(BizException.class);
    }

    // ── refreshPool ───────────────────────────────────────────

    @Test
    void refreshPool_savesNewProxies() {
        List<ProxyInfo> fetched = List.of(
                new ProxyInfo("10.0.0.1", 3128, "HTTP"),
                new ProxyInfo("10.0.0.2", 3128, "HTTP")
        );
        when(providerAdapter.fetch("HTTPS", 5, null)).thenReturn(fetched);
        when(proxyRepository.findByIpAndPort(anyString(), any()))
                .thenReturn(Optional.empty());
        when(proxyRepository.save(any())).thenAnswer(inv -> {
            Proxy p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        proxyPoolService.refreshPool("https", 5);

        verify(proxyRepository).findByIpAndPort("10.0.0.1", 3128);
        verify(proxyRepository).findByIpAndPort("10.0.0.2", 3128);
    }

    @Test
    void refreshPool_skipsInvalidEntries() {
        List<ProxyInfo> fetched = List.of(
                new ProxyInfo("", 3128, "HTTP"),   // blank IP
                new ProxyInfo("10.0.0.1", -1, "HTTP") // invalid port
        );
        when(providerAdapter.fetch("HTTP", 1, null)).thenReturn(fetched);

        proxyPoolService.refreshPool("http", 1);

        verify(proxyRepository, never()).save(any());
    }

    @Test
    void refreshPool_emptyFetch_doesNothing() {
        when(providerAdapter.fetch("HTTP", 1, null)).thenReturn(List.of());
        proxyPoolService.refreshPool("http", 1);
        verify(proxyRepository, never()).save(any());
    }

    // ── getStats ──────────────────────────────────────────────

    @Test
    void getStats_returnsCorrectCounts() {
        when(proxyRepository.count()).thenReturn(10L);
        when(proxyRepository.countByIsActiveTrue()).thenReturn(7L);
        when(zSetOps.zCard("proxy:pool")).thenReturn(5L);

        var stats = proxyPoolService.getStats();

        assertThat(stats).containsEntry("total", 10L)
                .containsEntry("active", 7L)
                .containsEntry("inactive", 3L)
                .containsEntry("pool_size", 5L);
    }

    // ── score calculation ─────────────────────────────────────

    @Test
    void syncProxyScore_calculatesCorrectly() {
        // score = successRate * 100 - fail * 2 - avgLatency / 100
        // successRate = 8/10 = 0.8, score = 80 - 4 - 1.5 = 74.5
        Proxy proxy = Proxy.builder()
                .id(1L).ip("1.2.3.4").port(8080).isActive(true)
                .successCount(8).failCount(2).avgLatencyMs(150).build();
        when(proxyRepository.findById(1L)).thenReturn(Optional.of(proxy));
        when(proxyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        proxyPoolService.updateScore(1L, true, 0, null, null);

        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zSetOps).add(eq("proxy:pool"), eq("1"), scoreCaptor.capture());
        // After success: successCount=9, failCount=2, total=11
        // successRate = 9/11 ≈ 0.818, score ≈ 81.8 - 4 - 1.5 = 76.3
        assertThat(scoreCaptor.getValue()).isBetween(75.0, 78.0);
    }

    @Test
    void syncProxyScore_inactiveProxy_removesFromPool() {
        Proxy proxy = Proxy.builder()
                .id(1L).ip("1.2.3.4").port(8080).isActive(false)
                .successCount(0).failCount(5).avgLatencyMs(0).build();
        when(proxyRepository.findById(1L)).thenReturn(Optional.of(proxy));
        when(proxyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        proxyPoolService.updateScore(1L, false, 0, null, null);

        verify(zSetOps).remove("proxy:pool", "1");
        verify(zSetOps, never()).add(anyString(), anyString(), anyDouble());
    }

    // ── addProxy ──────────────────────────────────────────────

    @Test
    void addProxy_setsProviderToManual() {
        when(proxyRepository.findByIpAndPort("1.2.3.4", 8080)).thenReturn(Optional.empty());
        when(proxyRepository.save(any())).thenAnswer(inv -> {
            Proxy p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        proxyPoolService.addProxy("1.2.3.4", 8080, "HTTP", null);

        ArgumentCaptor<Proxy> captor = ArgumentCaptor.forClass(Proxy.class);
        verify(proxyRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("manual");
    }

    // ── refreshPool (skip manual) ─────────────────────────────

    @Test
    void refreshPool_skipsManualProxies() {
        Proxy manualProxy = Proxy.builder().id(1L).ip("10.0.0.1").port(3128)
                .protocol("HTTP").provider("manual").isActive(true).build();
        when(providerAdapter.fetch("HTTP", 1, null))
                .thenReturn(List.of(new ProxyInfo("10.0.0.1", 3128, "HTTP")));
        when(proxyRepository.findByIpAndPort("10.0.0.1", 3128))
                .thenReturn(Optional.of(manualProxy));

        proxyPoolService.refreshPool("http", 1);

        verify(proxyRepository, never()).save(any());
    }

    // ── batchImport ───────────────────────────────────────────

    @Test
    void batchImport_parsesIpPortLines() {
        when(proxyRepository.findByIpAndPort(anyString(), any()))
                .thenReturn(Optional.empty());
        when(proxyRepository.save(any())).thenAnswer(inv -> {
            Proxy p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        ProxyPoolService.BatchImportResult result = proxyPoolService.batchImport(
                List.of("10.0.0.1:3128", "10.0.0.2:8080:HTTP:CN"), "myProvider");

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(0);
        assertThat(result.importedIds()).hasSize(2);
    }

    @Test
    void batchImport_withoutProtocol_storesNull() {
        when(proxyRepository.findByIpAndPort(anyString(), any()))
                .thenReturn(Optional.empty());
        when(proxyRepository.save(any())).thenAnswer(inv -> {
            Proxy p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        proxyPoolService.batchImport(List.of("10.0.0.1:3128"), "test");

        ArgumentCaptor<Proxy> captor = ArgumentCaptor.forClass(Proxy.class);
        verify(proxyRepository).save(captor.capture());
        assertThat(captor.getValue().getProtocol()).isNull();
    }

    @Test
    void batchImport_withProtocol_normalizesAndStores() {
        when(proxyRepository.findByIpAndPort(anyString(), any()))
                .thenReturn(Optional.empty());
        when(proxyRepository.save(any())).thenAnswer(inv -> {
            Proxy p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        proxyPoolService.batchImport(List.of("10.0.0.1:3128:socks5:US"), "test");

        ArgumentCaptor<Proxy> captor = ArgumentCaptor.forClass(Proxy.class);
        verify(proxyRepository).save(captor.capture());
        assertThat(captor.getValue().getProtocol()).isEqualTo("SOCKS5");
        assertThat(captor.getValue().getRegion()).isEqualTo("US");
    }

    @Test
    void batchImport_skipsInvalidLines() {
        ProxyPoolService.BatchImportResult result = proxyPoolService.batchImport(
                List.of("", "notanip", "10.0.0.1:notaport", "10.0.0.1:99999"), "test");

        assertThat(result.imported()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(4);
        verify(proxyRepository, never()).save(any());
    }

    @Test
    void batchImport_setsProvider() {
        when(proxyRepository.findByIpAndPort(anyString(), any()))
                .thenReturn(Optional.empty());
        when(proxyRepository.save(any())).thenAnswer(inv -> {
            Proxy p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        proxyPoolService.batchImport(List.of("1.2.3.4:1080"), "myApi");

        ArgumentCaptor<Proxy> captor = ArgumentCaptor.forClass(Proxy.class);
        verify(proxyRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("myApi");
    }

    // ── getProxyHealthLogs ────────────────────────────────────

    @Test
    void getProxyHealthLogs_proxyNotFound_throwsBizException() {
        when(proxyRepository.existsById(999L)).thenReturn(false);
        assertThatThrownBy(() -> proxyPoolService.getProxyHealthLogs(999L, 0, 10))
                .isInstanceOf(BizException.class);
    }
}
