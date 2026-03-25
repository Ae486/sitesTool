package com.rpacloud.proxy.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.rpacloud.proxy.dto.ProxyHealthLogResponse;
import com.rpacloud.proxy.dto.ProxyResponse;
import com.rpacloud.proxy.entity.Proxy;
import com.rpacloud.proxy.entity.ProxyHealthLog;
import com.rpacloud.proxy.provider.ProxyProviderAdapter;
import com.rpacloud.proxy.provider.ProxyProviderAdapter.ProxyInfo;
import com.rpacloud.proxy.repository.ProxyHealthLogRepository;
import com.rpacloud.proxy.repository.ProxyRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyPoolService {

    private static final String PROXY_POOL_KEY = "proxy:pool";
    private static final String PROXY_COOLDOWN_PREFIX = "proxy:cooldown:";

    private final ProxyRepository proxyRepository;
    private final ProxyHealthLogRepository proxyHealthLogRepository;
    private final List<ProxyProviderAdapter> providerAdapters;
    private final StringRedisTemplate stringRedisTemplate;
    private final RpaProperties rpaProperties;

    @Transactional(readOnly = true)
    public Optional<Proxy> getBestProxy() {
        try {
            Set<String> topOne = stringRedisTemplate.opsForZSet().reverseRange(PROXY_POOL_KEY, 0, 0);
            if (topOne == null || topOne.isEmpty()) return Optional.empty();
            String member = topOne.iterator().next();
            Long proxyId = parseProxyId(member);
            if (proxyId == null) {
                stringRedisTemplate.opsForZSet().remove(PROXY_POOL_KEY, member);
                return Optional.empty();
            }
            String cooldownKey = PROXY_COOLDOWN_PREFIX + proxyId;
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                    cooldownKey, "1",
                    Duration.ofSeconds(Math.max(1, rpaProperties.getProxy().getCooldownSeconds()))
            );
            if (!Boolean.TRUE.equals(acquired)) return Optional.empty();
            return proxyRepository.findById(proxyId).filter(proxy -> Boolean.TRUE.equals(proxy.getIsActive()));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for getBestProxy, falling back to DB", e.getMessage());
            return proxyRepository.findFirstByIsActiveTrueOrderBySuccessCountDesc();
        }
    }

    @Transactional
    public void updateScore(Long proxyId, boolean success, int latencyMs,
                            String detectedProtocol, String detectedRegion) {
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

        proxy.setLastCheckSuccess(success);
        proxy.setLastCheckedAt(LocalDateTime.now());
        if (detectedProtocol != null) proxy.setProtocol(detectedProtocol);
        if (detectedRegion != null) proxy.setRegion(detectedRegion);
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

            if ("manual".equals(proxy.getProvider())) {
                continue;
            }

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

    @Transactional
    public ProxyResponse addProxy(String ip, int port, String protocol, String region) {
        String normalizedProtocol = normalizeProtocol(protocol);
        Proxy proxy = proxyRepository.findByIpAndPort(ip, port)
                .orElseGet(() -> Proxy.builder()
                        .ip(ip)
                        .port(port)
                        .protocol(normalizedProtocol)
                        .region(region)
                        .isActive(true)
                        .successCount(0)
                        .failCount(0)
                        .avgLatencyMs(0)
                        .build());
        proxy.setProtocol(normalizedProtocol);
        if (region != null && !region.isBlank()) {
            proxy.setRegion(region);
        }
        proxy.setIsActive(true);
        proxy.setProvider("manual");
        Proxy saved = proxyRepository.save(proxy);
        syncProxyScore(saved);
        return toResponse(saved);
    }

    public record BatchImportResult(int imported, int skipped, List<Long> importedIds) {}

    @Transactional
    public BatchImportResult batchImport(List<String> lines, String providerName) {
        List<Long> importedIds = new ArrayList<>();
        int skipped = 0;
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) { skipped++; continue; }
            String[] parts = line.split(":");
            if (parts.length < 2) { skipped++; continue; }
            String ip = parts[0].trim();
            int port;
            try {
                port = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) { skipped++; continue; }
            if (ip.isBlank() || port < 1 || port > 65535) { skipped++; continue; }
            String protocol = parts.length >= 3 && !parts[2].isBlank() ? normalizeProtocol(parts[2]) : null;
            String region = parts.length >= 4 && !parts[3].isBlank() ? parts[3].trim() : null;
            Proxy proxy = proxyRepository.findByIpAndPort(ip, port)
                    .orElseGet(() -> Proxy.builder().ip(ip).port(port)
                            .protocol(protocol)
                            .isActive(true).successCount(0).failCount(0).avgLatencyMs(0).build());
            if (protocol != null) proxy.setProtocol(protocol);
            if (region != null) proxy.setRegion(region);
            proxy.setIsActive(true);
            proxy.setProvider(providerName);
            Proxy saved = proxyRepository.save(proxy);
            syncProxyScore(saved);
            importedIds.add(saved.getId());
        }
        return new BatchImportResult(importedIds.size(), skipped, importedIds);
    }

    @Transactional
    public void deleteProxy(Long id) {
        Proxy proxy = proxyRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Proxy not found"));
        proxyHealthLogRepository.deleteAllByProxyId(id);
        proxyRepository.delete(proxy);
        try {
            stringRedisTemplate.opsForZSet().remove(PROXY_POOL_KEY, String.valueOf(id));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, skipping ZSet cleanup for proxy {}", id);
        }
    }

    @Transactional
    public ProxyResponse toggleProxy(Long id) {
        Proxy proxy = proxyRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Proxy not found"));
        proxy.setIsActive(!Boolean.TRUE.equals(proxy.getIsActive()));
        Proxy saved = proxyRepository.save(proxy);
        syncProxyScore(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProxyResponse> getAllProxies(int skip, int limit,
                                                     String protocol, String provider, String status,
                                                     String search) {
        Specification<Proxy> spec = buildFilterSpec(protocol, provider, status, search);
        Page<Proxy> page = proxyRepository.findAll(spec,
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
        long poolSize = 0L;
        try {
            Long sz = stringRedisTemplate.opsForZSet().zCard(PROXY_POOL_KEY);
            poolSize = sz == null ? 0L : sz;
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, pool_size defaults to 0");
        }
        return Map.of("total", total, "active", active, "inactive", inactive, "pool_size", poolSize);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProxyHealthLogResponse> getProxyHealthLogs(Long proxyId, int skip, int limit) {
        if (!proxyRepository.existsById(proxyId)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Proxy not found");
        }
        Page<ProxyHealthLog> page = proxyHealthLogRepository.findByProxyIdOrderByCheckedAtDesc(
                proxyId, new OffsetBasedPageRequest(skip, limit, Sort.unsorted())
        );
        List<ProxyHealthLogResponse> items = page.getContent().stream()
                .map(log -> new ProxyHealthLogResponse(
                        log.getId(),
                        Boolean.TRUE.equals(log.getSuccess()),
                        log.getLatencyMs() == null ? 0 : log.getLatencyMs(),
                        log.getCheckedAt(),
                        log.getErrorMessage()
                ))
                .toList();
        return PageResponse.of(page.getTotalElements(), items);
    }

    private void syncProxyScore(Proxy proxy) {
        try {
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
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, skipping score sync for proxy {}", proxy.getId());
        }
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
                proxy.getLastCheckSuccess(),
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

    private Specification<Proxy> buildFilterSpec(String protocol, String provider, String status, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (protocol != null && !protocol.isBlank()) {
                if ("unknown".equalsIgnoreCase(protocol)) {
                    predicates.add(cb.isNull(root.get("protocol")));
                } else {
                    predicates.add(cb.equal(root.get("protocol"), protocol.toUpperCase(Locale.ROOT)));
                }
            }
            if (provider != null && !provider.isBlank()) {
                if ("manual".equals(provider)) {
                    predicates.add(cb.equal(root.get("provider"), "manual"));
                } else if ("api".equals(provider)) {
                    predicates.add(cb.or(
                            cb.isNull(root.get("provider")),
                            cb.notEqual(root.get("provider"), "manual")
                    ));
                }
            }
            if (status != null && !status.isBlank()) {
                switch (status.toLowerCase(Locale.ROOT)) {
                    case "available" -> predicates.add(cb.equal(root.get("lastCheckSuccess"), true));
                    case "unavailable" -> predicates.add(cb.and(
                            cb.isNotNull(root.get("lastCheckedAt")),
                            cb.or(
                                    cb.equal(root.get("lastCheckSuccess"), false),
                                    cb.isNull(root.get("lastCheckSuccess"))
                            )
                    ));
                    case "pending" -> predicates.add(cb.isNull(root.get("lastCheckedAt")));
                }
            }
            if (search != null && !search.isBlank()) {
                String escaped = search.trim()
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.like(root.get("ip"), escaped + "%"));
            }
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
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
