package com.rpacloud.proxy.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.common.dto.OffsetBasedPageRequest;
import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.proxy.dto.ProxyResponse;
import com.rpacloud.proxy.entity.Proxy;
import com.rpacloud.proxy.provider.ProxyProviderAdapter;
import com.rpacloud.proxy.provider.ProxyProviderAdapter.ProxyInfo;
import com.rpacloud.proxy.repository.ProxyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Profile("enterprise")
@RequiredArgsConstructor
public class ProxyPoolService {

    private static final String PROXY_POOL_KEY = "proxy:pool";
    private static final String PROXY_COOLDOWN_PREFIX = "proxy:cooldown:";

    private final ProxyRepository proxyRepository;
    private final List<ProxyProviderAdapter> providerAdapters;
    private final StringRedisTemplate stringRedisTemplate;
    private final RpaProperties rpaProperties;

    @Transactional(readOnly = true)
    public Optional<Proxy> getBestProxy() {
        Set<String> topOne = stringRedisTemplate.opsForZSet().reverseRange(PROXY_POOL_KEY, 0, 0);
        if (topOne == null || topOne.isEmpty()) {
            return Optional.empty();
        }

        String member = topOne.iterator().next();
        Long proxyId = parseProxyId(member);
        if (proxyId == null) {
            stringRedisTemplate.opsForZSet().remove(PROXY_POOL_KEY, member);
            return Optional.empty();
        }

        String cooldownKey = PROXY_COOLDOWN_PREFIX + proxyId;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                cooldownKey,
                "1",
                Duration.ofSeconds(Math.max(1, rpaProperties.getProxy().getCooldownSeconds()))
        );
        if (!Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }

        return proxyRepository.findById(proxyId).filter(proxy -> Boolean.TRUE.equals(proxy.getIsActive()));
    }

    @Transactional
    public void updateScore(Long proxyId, boolean success, int latencyMs) {
        Proxy proxy = proxyRepository.findById(proxyId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Proxy not found"));

        int currentSuccess = valueOrZero(proxy.getSuccessCount());
        int currentFail = valueOrZero(proxy.getFailCount());
        int currentAvgLatency = valueOrZero(proxy.getAvgLatencyMs());
        int safeLatency = Math.max(latencyMs, 0);

        if (success) {
            int nextSuccess = currentSuccess + 1;
            int nextAvgLatency = safeLatency > 0
                    ? (currentAvgLatency * currentSuccess + safeLatency) / Math.max(nextSuccess, 1)
                    : currentAvgLatency;
            proxy.setSuccessCount(nextSuccess);
            proxy.setAvgLatencyMs(nextAvgLatency);
        } else {
            proxy.setFailCount(currentFail + 1);
        }

        proxy.setLastCheckedAt(LocalDateTime.now());
        proxyRepository.save(proxy);
        syncProxyScore(proxy);
    }

    @Transactional
    public void refreshPool(String protocol, int count) {
        ProxyProviderAdapter provider = resolveProvider();
        String normalizedProtocol = normalizeProtocol(protocol);
        int safeCount = Math.max(1, count);

        List<ProxyInfo> fetched = provider.fetch(normalizedProtocol, safeCount, null);
        if (fetched.isEmpty()) {
            return;
        }

        for (ProxyInfo item : fetched) {
            if (item.ip() == null || item.ip().isBlank() || item.port() <= 0) {
                continue;
            }

            Proxy proxy = proxyRepository.findByIpAndPort(item.ip(), item.port())
                    .orElseGet(() -> Proxy.builder()
                            .ip(item.ip())
                            .port(item.port())
                            .protocol(normalizedProtocol)
                            .provider(provider.getClass().getSimpleName())
                            .isActive(true)
                            .successCount(0)
                            .failCount(0)
                            .avgLatencyMs(0)
                            .build());

            proxy.setProtocol(normalizeProtocol(item.protocol()));
            if (!Boolean.TRUE.equals(proxy.getIsActive())) {
                proxy.setIsActive(true);
            }
            if (proxy.getProvider() == null || proxy.getProvider().isBlank()) {
                proxy.setProvider(provider.getClass().getSimpleName());
            }

            Proxy saved = proxyRepository.save(proxy);
            syncProxyScore(saved);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ProxyResponse> getAllProxies(int skip, int limit) {
        Page<Proxy> page = proxyRepository.findAll(
                new OffsetBasedPageRequest(skip, limit, Sort.by(Sort.Direction.DESC, "id"))
        );
        List<ProxyResponse> items = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.of(page.getTotalElements(), items);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        long total = proxyRepository.count();
        long active = proxyRepository.countByIsActiveTrue();
        long inactive = Math.max(0, total - active);
        Long poolSize = stringRedisTemplate.opsForZSet().zCard(PROXY_POOL_KEY);
        return Map.of(
                "total", total,
                "active", active,
                "inactive", inactive,
                "pool_size", poolSize == null ? 0L : poolSize
        );
    }

    private void syncProxyScore(Proxy proxy) {
        if (!Boolean.TRUE.equals(proxy.getIsActive()) || proxy.getId() == null) {
            if (proxy.getId() != null) {
                stringRedisTemplate.opsForZSet().remove(PROXY_POOL_KEY, String.valueOf(proxy.getId()));
            }
            return;
        }

        int success = valueOrZero(proxy.getSuccessCount());
        int fail = valueOrZero(proxy.getFailCount());
        int total = success + fail;
        double successRate = total == 0 ? 0.5d : (double) success / total;
        int avgLatency = valueOrZero(proxy.getAvgLatencyMs());

        double score = successRate * 100d - fail * 2d - avgLatency / 100.0d;
        stringRedisTemplate.opsForZSet().add(PROXY_POOL_KEY, String.valueOf(proxy.getId()), score);
    }

    private ProxyProviderAdapter resolveProvider() {
        if (providerAdapters == null || providerAdapters.isEmpty()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "No proxy provider adapter configured");
        }
        return providerAdapters.get(0);
    }

    private ProxyResponse toResponse(Proxy proxy) {
        return new ProxyResponse(
                proxy.getId(),
                proxy.getIp(),
                valueOrZero(proxy.getPort()),
                proxy.getProtocol(),
                proxy.getRegion(),
                proxy.getProvider(),
                Boolean.TRUE.equals(proxy.getIsActive()),
                valueOrZero(proxy.getSuccessCount()),
                valueOrZero(proxy.getFailCount()),
                valueOrZero(proxy.getAvgLatencyMs()),
                proxy.getLastCheckedAt()
        );
    }

    private Long parseProxyId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            log.warn("Invalid proxy member in Redis zset: {}", value);
            return null;
        }
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return "HTTP";
        }
        String upper = protocol.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "HTTP", "HTTPS", "SOCKS5" -> upper;
            default -> "HTTP";
        };
    }
}
