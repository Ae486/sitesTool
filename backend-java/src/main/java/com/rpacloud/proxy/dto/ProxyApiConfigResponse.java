package com.rpacloud.proxy.dto;

import java.time.LocalDateTime;

public record ProxyApiConfigResponse(
        Long id,
        String name,
        String baseUrl,
        String paramsJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
