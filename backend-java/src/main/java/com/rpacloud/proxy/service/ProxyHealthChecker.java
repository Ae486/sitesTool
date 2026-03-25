package com.rpacloud.proxy.service;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.proxy.entity.Proxy;
import com.rpacloud.proxy.entity.ProxyHealthLog;
import com.rpacloud.proxy.repository.ProxyHealthLogRepository;
import com.rpacloud.proxy.repository.ProxyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProxyHealthChecker {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final Pattern IP_PATTERN = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    private static final String[] HEALTH_CHECK_URLS = {
            "http://httpbin.org/ip",
            "http://myip.ipip.net",
            "http://api.ipify.org",
    };

    private final ProxyRepository proxyRepository;
    private final ProxyHealthLogRepository proxyHealthLogRepository;
    private final ProxyPoolService proxyPoolService;
    private final GeoIpService geoIpService;
    private final RpaProperties rpaProperties;

    @Qualifier("proxyHealthExecutor")
    private final Executor proxyHealthExecutor;

    @Scheduled(fixedRateString = "${rpa.proxy.health-check-interval-ms:300000}")
    public void checkAll() {
        List<Proxy> activeProxies = proxyRepository.findAllByIsActiveTrue();
        if (activeProxies.isEmpty()) return;
        runChecks(activeProxies);
    }

    public void checkProxiesAsync(List<Long> proxyIds) {
        if (proxyIds == null || proxyIds.isEmpty()) return;
        List<Proxy> proxies = proxyRepository.findAllById(proxyIds);
        if (!proxies.isEmpty()) {
            runChecks(proxies);
        }
    }

    private void runChecks(List<Proxy> proxies) {
        int maxConcurrent = Math.max(1, rpaProperties.getProxy().getMaxConcurrentChecks());
        Semaphore semaphore = new Semaphore(maxConcurrent);
        proxies.forEach(proxy ->
                CompletableFuture.runAsync(() -> checkOne(proxy, semaphore), proxyHealthExecutor));
    }

    private void checkOne(Proxy proxy, Semaphore semaphore) {
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;

            String markedProtocol = (proxy.getProtocol() != null && !proxy.getProtocol().isBlank())
                    ? proxy.getProtocol().toUpperCase() : null;
            String[] protocolsToTry;
            if (markedProtocol == null) {
                protocolsToTry = new String[]{"HTTP", "SOCKS5"};
            } else if ("SOCKS5".equals(markedProtocol)) {
                protocolsToTry = new String[]{"SOCKS5", "HTTP"};
            } else {
                protocolsToTry = new String[]{markedProtocol, "SOCKS5"};
            }

            long startNano = System.nanoTime();
            boolean success = false;
            String errorMessage = null;
            String detectedProtocol = null;

            outer:
            for (String protocol : protocolsToTry) {
                for (String url : HEALTH_CHECK_URLS) {
                    try {
                        RestTemplate rt = buildProxyRestTemplate(proxy.getIp(), proxy.getPort(), protocol);
                        ResponseEntity<String> resp = rt.getForEntity(url, String.class);
                        if (resp.getStatusCode().is2xxSuccessful() && isValidResponse(resp.getBody())) {
                            success = true;
                            if (markedProtocol == null || !markedProtocol.equals(protocol)) {
                                detectedProtocol = protocol;
                            }
                            log.info("Proxy {}:{} OK via {} → {} ({}ms)", proxy.getIp(), proxy.getPort(),
                                    protocol, url, Duration.ofNanos(System.nanoTime() - startNano).toMillis());
                            break outer;
                        }
                        errorMessage = resp.getBody() != null && resp.getBody().contains("Could not connect")
                                ? "Proxy returned error page" : "Status " + resp.getStatusCode().value();
                    } catch (Exception ex) {
                        errorMessage = trimTo500(ex.getMessage());
                        log.debug("Proxy {}:{} FAIL {} → {} - {}", proxy.getIp(), proxy.getPort(),
                                protocol, url, errorMessage);
                    }
                }
            }

            int latencyMs = (int) Math.max(1, Duration.ofNanos(System.nanoTime() - startNano).toMillis());

            if (!success) {
                log.info("Proxy {}:{} all checks FAILED ({} protocols × {} URLs, {}ms)",
                        proxy.getIp(), proxy.getPort(), protocolsToTry.length, HEALTH_CHECK_URLS.length, latencyMs);
            }

            String detectedRegion = null;
            if (success && (proxy.getRegion() == null || proxy.getRegion().isBlank())) {
                detectedRegion = geoIpService.lookupRegion(proxy.getIp()).orElse(null);
            }

            proxyHealthLogRepository.save(ProxyHealthLog.builder()
                    .proxy(proxy).success(success).latencyMs(latencyMs)
                    .errorMessage(errorMessage).checkedAt(LocalDateTime.now()).build());

            proxyPoolService.updateScore(proxy.getId(), success, latencyMs, detectedProtocol, detectedRegion);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Proxy health check interrupted");
        } catch (Exception ex) {
            log.error("Unexpected error checking {}:{}", proxy.getIp(), proxy.getPort(), ex);
        } finally {
            if (acquired) semaphore.release();
        }
    }

    /** Validate response body contains an IP address (rejects proxy error pages). */
    private boolean isValidResponse(String body) {
        return body != null && IP_PATTERN.matcher(body).find();
    }

    private RestTemplate buildProxyRestTemplate(String ip, int port, String protocol) {
        java.net.Proxy.Type proxyType = "SOCKS5".equalsIgnoreCase(protocol)
                ? java.net.Proxy.Type.SOCKS
                : java.net.Proxy.Type.HTTP;
        java.net.Proxy netProxy = new java.net.Proxy(proxyType, new InetSocketAddress(ip, port));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(netProxy);
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    private String trimTo500(String message) {
        if (message == null) return null;
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
