package com.rpacloud.proxy.service;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.proxy.entity.Proxy;
import com.rpacloud.proxy.entity.ProxyHealthLog;
import com.rpacloud.proxy.repository.ProxyHealthLogRepository;
import com.rpacloud.proxy.repository.ProxyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@Profile("enterprise")
@RequiredArgsConstructor
public class ProxyHealthChecker {

    private static final String HEALTH_CHECK_URL = "http://httpbin.org/ip";
    private static final int REQUEST_TIMEOUT_MS = 10_000;

    private final ProxyRepository proxyRepository;
    private final ProxyHealthLogRepository proxyHealthLogRepository;
    private final ProxyPoolService proxyPoolService;
    private final RpaProperties rpaProperties;

    @Qualifier("automationTaskExecutor")
    private final Executor automationTaskExecutor;

    @Scheduled(fixedRateString = "${rpa.proxy.health-check-interval-ms:300000}")
    public void checkAll() {
        List<Proxy> activeProxies = proxyRepository.findAllByIsActiveTrue();
        if (activeProxies.isEmpty()) {
            return;
        }

        int maxConcurrent = Math.max(1, rpaProperties.getProxy().getMaxConcurrentChecks());
        Semaphore semaphore = new Semaphore(maxConcurrent);

        List<CompletableFuture<Void>> tasks = activeProxies.stream()
                .map(proxy -> CompletableFuture.runAsync(() -> checkOne(proxy, semaphore), automationTaskExecutor))
                .toList();

        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
    }

    private void checkOne(Proxy proxy, Semaphore semaphore) {
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;

            long startNano = System.nanoTime();
            boolean success = false;
            String errorMessage = null;

            try {
                RestTemplate restTemplate = buildProxyRestTemplate(proxy);
                ResponseEntity<String> response = restTemplate.getForEntity(HEALTH_CHECK_URL, String.class);
                success = response.getStatusCode().is2xxSuccessful();
                if (!success) {
                    errorMessage = "Health check status: " + response.getStatusCode().value();
                }
            } catch (Exception ex) {
                errorMessage = trimTo500(ex.getMessage());
                log.debug("Proxy health check failed for {}:{} - {}", proxy.getIp(), proxy.getPort(), errorMessage);
            }

            int latencyMs = (int) Math.max(1, Duration.ofNanos(System.nanoTime() - startNano).toMillis());

            ProxyHealthLog logEntity = ProxyHealthLog.builder()
                    .proxy(proxy)
                    .success(success)
                    .latencyMs(latencyMs)
                    .errorMessage(errorMessage)
                    .checkedAt(LocalDateTime.now())
                    .build();
            proxyHealthLogRepository.save(logEntity);

            proxyPoolService.updateScore(proxy.getId(), success, latencyMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Proxy health check interrupted");
        } catch (Exception ex) {
            log.error("Unexpected error during proxy health check for {}:{}",
                    proxy.getIp(), proxy.getPort(), ex);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private RestTemplate buildProxyRestTemplate(Proxy proxyEntity) {
        java.net.Proxy.Type proxyType = "SOCKS5".equalsIgnoreCase(proxyEntity.getProtocol())
                ? java.net.Proxy.Type.SOCKS
                : java.net.Proxy.Type.HTTP;

        java.net.Proxy netProxy = new java.net.Proxy(
                proxyType,
                new InetSocketAddress(proxyEntity.getIp(), proxyEntity.getPort())
        );

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(netProxy);
        requestFactory.setConnectTimeout(REQUEST_TIMEOUT_MS);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT_MS);
        return new RestTemplate(requestFactory);
    }

    private String trimTo500(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
