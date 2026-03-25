package com.rpacloud.proxy.dto;

import java.time.LocalDateTime;

public record ProxyHealthLogResponse(
        Long id,
        boolean success,
        int latencyMs,
        LocalDateTime checkedAt,
        String errorMessage
) {}
