package com.rpacloud.integration.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.rpacloud.integration.BaseIntegrationTest;
import com.rpacloud.proxy.entity.Proxy;
import com.rpacloud.proxy.entity.ProxyHealthLog;
import com.rpacloud.proxy.repository.ProxyHealthLogRepository;
import com.rpacloud.proxy.repository.ProxyRepository;
import com.rpacloud.proxy.service.ProxyHealthChecker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

class ProxyHealthCheckerIT extends BaseIntegrationTest {

    @Autowired private ProxyHealthChecker proxyHealthChecker;
    @Autowired private ProxyRepository proxyRepository;
    @Autowired private ProxyHealthLogRepository proxyHealthLogRepository;
    @Autowired private StringRedisTemplate stringRedisTemplate;

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        // Stub the health check endpoint
        wireMock.stubFor(get(urlPathEqualTo("/ip"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"origin\": \"127.0.0.1\"}")));
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void cleanUp() {
        proxyHealthLogRepository.deleteAll();
        proxyRepository.deleteAll();
        stringRedisTemplate.delete("proxy:pool");
        var keys = stringRedisTemplate.keys("proxy:cooldown:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void checkAll_writesHealthLogAndUpdatesScore() {
        // Insert a proxy pointing to unreachable address (will fail health check)
        Proxy proxy = proxyRepository.save(
                Proxy.builder().ip("127.0.0.1").port(1).protocol("HTTP").isActive(true)
                        .successCount(0).failCount(0).avgLatencyMs(0).build()
        );

        proxyHealthChecker.checkAll();

        // Health log should be written
        List<ProxyHealthLog> logs = proxyHealthLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getProxy().getId()).isEqualTo(proxy.getId());
        assertThat(logs.get(0).getSuccess()).isFalse();
        assertThat(logs.get(0).getLatencyMs()).isPositive();
        assertThat(logs.get(0).getErrorMessage()).isNotNull();

        // Score should be updated in Redis
        Double score = stringRedisTemplate.opsForZSet().score("proxy:pool", String.valueOf(proxy.getId()));
        assertThat(score).isNotNull();

        // Proxy stats should be updated in MySQL
        Proxy updated = proxyRepository.findById(proxy.getId()).orElseThrow();
        assertThat(updated.getFailCount()).isEqualTo(1);
        assertThat(updated.getLastCheckedAt()).isNotNull();
    }

    @Test
    void checkAll_multipleProxies_allChecked() {
        Proxy p1 = proxyRepository.save(
                Proxy.builder().ip("127.0.0.1").port(1).protocol("HTTP").isActive(true)
                        .successCount(0).failCount(0).avgLatencyMs(0).build()
        );
        Proxy p2 = proxyRepository.save(
                Proxy.builder().ip("127.0.0.1").port(2).protocol("HTTP").isActive(true)
                        .successCount(0).failCount(0).avgLatencyMs(0).build()
        );

        proxyHealthChecker.checkAll();

        List<ProxyHealthLog> logs = proxyHealthLogRepository.findAll();
        assertThat(logs).hasSize(2);

        // Both proxies should have updated fail counts
        assertThat(proxyRepository.findById(p1.getId()).orElseThrow().getFailCount()).isEqualTo(1);
        assertThat(proxyRepository.findById(p2.getId()).orElseThrow().getFailCount()).isEqualTo(1);
    }

    @Test
    void checkAll_noActiveProxies_nothingHappens() {
        proxyRepository.save(
                Proxy.builder().ip("127.0.0.1").port(1).protocol("HTTP").isActive(false)
                        .successCount(0).failCount(0).avgLatencyMs(0).build()
        );

        proxyHealthChecker.checkAll();

        assertThat(proxyHealthLogRepository.findAll()).isEmpty();
    }
}
