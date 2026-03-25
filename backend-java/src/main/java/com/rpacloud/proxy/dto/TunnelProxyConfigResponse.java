package com.rpacloud.proxy.dto;

import java.time.LocalDateTime;

public record TunnelProxyConfigResponse(
        Long id,
        String name,
        String protocol,
        String host,
        Integer port,
        String username,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
