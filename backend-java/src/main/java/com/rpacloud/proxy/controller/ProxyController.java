package com.rpacloud.proxy.controller;

import java.util.Locale;
import java.util.Map;

import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.proxy.dto.ProxyResponse;
import com.rpacloud.proxy.service.ProxyPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("enterprise")
@RequestMapping("/api/proxy")
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyPoolService proxyPoolService;

    @GetMapping
    public PageResponse<ProxyResponse> list(
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit) {
        return proxyPoolService.getAllProxies(skip, limit);
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(
            @RequestParam(defaultValue = "https") String protocol,
            @RequestParam(defaultValue = "5") int count) {
        proxyPoolService.refreshPool(protocol, count);
        return Map.of(
                "status", "ok",
                "protocol", protocol.toUpperCase(Locale.ROOT),
                "count", count
        );
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return proxyPoolService.getStats();
    }
}
