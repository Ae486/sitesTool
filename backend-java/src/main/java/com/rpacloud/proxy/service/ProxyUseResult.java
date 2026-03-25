package com.rpacloud.proxy.service;

import java.util.List;

public record ProxyUseResult(
        boolean success,
        int latencyMs,
        List<String> errorTypes
) {}
