package com.rpacloud.proxy.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.proxy.entity.Proxy;
import com.rpacloud.proxy.repository.ProxyRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProxyLeasePool {

    private final ProxyRepository proxyRepository;
    private final ProxyPoolService proxyPoolService;
    private final RpaProperties rpaProperties;

    private final Deque<ProxyLease> idle = new ArrayDeque<>();
    private final Map<String, ProxyLease> active = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    public ProxyLeasePool(ProxyRepository proxyRepository,
                          ProxyPoolService proxyPoolService,
                          RpaProperties rpaProperties) {
        this.proxyRepository = proxyRepository;
        this.proxyPoolService = proxyPoolService;
        this.rpaProperties = rpaProperties;
    }

    @PostConstruct
    void init() {
        List<Proxy> proxies = proxyRepository.findAllByIsActiveTrue();
        List<ProxyLease> sorted = proxies.stream()
                .map(p -> new ProxyLease(p.getId(), formatUrl(p), computeScore(p)))
                .sorted(Comparator.comparingDouble(ProxyLease::getScore).reversed())
                .toList();
        lock.lock();
        try {
            idle.clear();
            idle.addAll(sorted);
        } finally {
            lock.unlock();
        }
        log.info("ProxyLeasePool init: loaded {} proxies into idle", sorted.size());
    }

    public ProxyLease checkout(String executionId, java.time.Duration timeout) {
        lock.lock();
        try {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                ProxyLease lease = idle.pollFirst();
                if (lease != null) {
                    lease.setCheckoutAt(System.currentTimeMillis());
                    lease.setExecutionId(executionId);
                    active.put(executionId, lease);
                    log.info("Proxy {} checked out for execution {}, idle={} active={}",
                            lease.getProxyId(), executionId, idle.size(), active.size());
                    return lease;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new ProxyAcquireTimeoutException(
                            "No proxy available within " + timeout.toMillis() + "ms, active=" + active.size());
                }
                notEmpty.awaitNanos(remaining);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProxyAcquireTimeoutException("Proxy checkout interrupted");
        } finally {
            lock.unlock();
        }
    }

    public void returnProxy(String executionId, ProxyUseResult result) {
        lock.lock();
        try {
            ProxyLease lease = active.remove(executionId);
            if (lease == null) {
                log.debug("No active lease for execution {}, possibly already reclaimed", executionId);
                return;
            }

            FailureCategory cat = FailureCategory.fromErrorTypes(result.errorTypes());
            proxyPoolService.updateScore(lease.getProxyId(), !cat.isProxyFault(),
                    result.latencyMs(), null, null);

            if (cat.isProxyFault()) {
                log.warn("Proxy {} discarded after execution {}: {}",
                        lease.getProxyId(), executionId, cat);
            } else {
                refreshScore(lease);
                idle.addFirst(lease);
                log.info("Proxy {} returned to idle after execution {}, idle={} active={}",
                        lease.getProxyId(), executionId, idle.size(), active.size());
            }
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public void markUnavailable(Long proxyId) {
        lock.lock();
        try {
            idle.removeIf(l -> l.getProxyId().equals(proxyId));
            log.info("Proxy {} marked unavailable, removed from idle", proxyId);
        } finally {
            lock.unlock();
        }
    }

    public void markAvailable(Long proxyId) {
        lock.lock();
        try {
            boolean inIdle = idle.stream().anyMatch(l -> l.getProxyId().equals(proxyId));
            boolean inActive = active.values().stream().anyMatch(l -> l.getProxyId().equals(proxyId));
            if (!inIdle && !inActive) {
                proxyRepository.findById(proxyId)
                        .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                        .ifPresent(p -> {
                            idle.addLast(new ProxyLease(p.getId(), formatUrl(p), computeScore(p)));
                            notEmpty.signal();
                            log.info("Proxy {} marked available, added to idle", proxyId);
                        });
            }
        } finally {
            lock.unlock();
        }
    }

    @Scheduled(fixedRate = 60000)
    void reclaimOverdue() {
        long maxLeaseMs = rpaProperties.getProxy().getProxyMaxLeaseMs();
        long now = System.currentTimeMillis();
        List<String> reclaimed = new ArrayList<>();

        lock.lock();
        try {
            Iterator<Map.Entry<String, ProxyLease>> it = active.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, ProxyLease> entry = it.next();
                ProxyLease lease = entry.getValue();
                if (now - lease.getCheckoutAt() > maxLeaseMs) {
                    it.remove();
                    refreshScore(lease);
                    idle.addLast(lease);
                    reclaimed.add(entry.getKey());
                    notEmpty.signal();
                }
            }
        } finally {
            lock.unlock();
        }

        for (String execId : reclaimed) {
            log.warn("Reclaimed overdue proxy lease for execution {}", execId);
        }
    }

    public int idleCount() {
        lock.lock();
        try { return idle.size(); } finally { lock.unlock(); }
    }

    public int activeCount() {
        return active.size();
    }

    static double computeScore(Proxy proxy) {
        int success = proxy.getSuccessCount() != null ? proxy.getSuccessCount() : 0;
        int fail = proxy.getFailCount() != null ? proxy.getFailCount() : 0;
        int total = success + fail;
        double successRate = total == 0 ? 0.5d : (double) success / total;
        int avgLatency = proxy.getAvgLatencyMs() != null ? proxy.getAvgLatencyMs() : 0;
        return successRate * 100d - fail * 2d - avgLatency / 100.0d;
    }

    private void refreshScore(ProxyLease lease) {
        proxyRepository.findById(lease.getProxyId()).ifPresent(p -> lease.setScore(computeScore(p)));
    }

    private static String formatUrl(Proxy proxy) {
        String protocol = proxy.getProtocol() != null ? proxy.getProtocol().toLowerCase() : "http";
        if ("socks5".equals(protocol)) return "socks5://" + proxy.getIp() + ":" + proxy.getPort();
        return "http://" + proxy.getIp() + ":" + proxy.getPort();
    }
}
