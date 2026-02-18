package com.rpacloud.proxy.dto;

import java.time.LocalDateTime;

public record ProxyResponse(
        Long id,
        String ip,
        int port,
        String protocol,
        String region,
        String provider,
        boolean isActive,
        int successCount,
        int failCount,
        int avgLatencyMs,
        LocalDateTime lastCheckedAt
) {
}
