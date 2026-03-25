package com.rpacloud.proxy.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.execution.engine.handlers.SsrfValidator;
import com.rpacloud.proxy.dto.ProxyHealthLogResponse;
import com.rpacloud.proxy.dto.ProxyResponse;
import com.rpacloud.proxy.service.ProxyHealthChecker;
import com.rpacloud.proxy.service.ProxyPoolService;
import com.rpacloud.proxy.service.ProxyPoolService.BatchImportResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/proxy")
@RequiredArgsConstructor
public class ProxyController {

    private static final int FETCH_TIMEOUT_SECONDS = 10;

    private final ProxyPoolService proxyPoolService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private ProxyHealthChecker proxyHealthChecker;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(FETCH_TIMEOUT_SECONDS))
            .build();

    @GetMapping
    public PageResponse<ProxyResponse> list(
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return proxyPoolService.getAllProxies(skip, limit, protocol, provider, status, search);
    }

    @GetMapping("/{id}/logs")
    public PageResponse<ProxyHealthLogResponse> logs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit) {
        return proxyPoolService.getProxyHealthLogs(id, skip, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProxyResponse add(@Valid @RequestBody ProxyAddRequest req) {
        ProxyResponse response = proxyPoolService.addProxy(req.ip(), req.port(), req.protocol(), req.region());
        if (proxyHealthChecker != null) {
            proxyHealthChecker.checkProxiesAsync(List.of(response.id()));
        }
        return response;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        proxyPoolService.deleteProxy(id);
    }

    @PatchMapping("/{id}/toggle")
    public ProxyResponse toggle(@PathVariable Long id) {
        return proxyPoolService.toggleProxy(id);
    }

    @PostMapping("/{id}/check")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void triggerCheck(@PathVariable Long id) {
        if (proxyHealthChecker != null) {
            proxyHealthChecker.checkProxiesAsync(List.of(id));
        }
    }

    @PostMapping("/batch-check")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void batchCheck(@RequestBody List<Long> ids) {
        if (proxyHealthChecker != null && ids != null && !ids.isEmpty()) {
            proxyHealthChecker.checkProxiesAsync(ids);
        }
    }

    @PostMapping("/batch")
    public Map<String, Object> batchImport(@Valid @RequestBody BatchImportRequest req) {
        BatchImportResult result = proxyPoolService.batchImport(req.lines(), req.providerName());
        if (proxyHealthChecker != null && !result.importedIds().isEmpty()) {
            proxyHealthChecker.checkProxiesAsync(result.importedIds());
        }
        return Map.of("imported", result.imported(), "skipped", result.skipped());
    }

    @PostMapping("/fetch-preview")
    public Map<String, Object> fetchPreview(@Valid @RequestBody FetchPreviewRequest req) {
        String url = buildUrl(req.baseUrl(), req.params());
        SsrfValidator.validate(url);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(FETCH_TIMEOUT_SECONDS))
                    .GET().build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            List<Map<String, Object>> items = parseProxyList(response.body());
            return Map.of("items", items, "url", url);
        } catch (Exception ex) {
            log.warn("Proxy fetch-preview failed for url={}: {}", url, ex.getMessage());
            return Map.of("items", List.of(), "url", url, "error", ex.getMessage());
        }
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(
            @RequestParam(defaultValue = "https") String protocol,
            @RequestParam(defaultValue = "5") int count) {
        proxyPoolService.refreshPool(protocol, count);
        return Map.of("status", "ok", "protocol", protocol.toUpperCase(Locale.ROOT), "count", count);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return proxyPoolService.getStats();
    }

    // --- Helper Methods ---

    private String buildUrl(String baseUrl, List<ParamEntry> params) {
        if (params == null || params.isEmpty()) return baseUrl;
        String query = params.stream()
                .filter(p -> p.key() != null && !p.key().isBlank())
                .map(p -> encode(p.key()) + "=" + encode(p.value() == null ? "" : p.value()))
                .collect(Collectors.joining("&"));
        return query.isEmpty() ? baseUrl : baseUrl + "?" + query;
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> parseProxyList(String body) {
        if (body == null || body.isBlank()) return List.of();
        // Try JSON
        try {
            JsonNode root = objectMapper.readTree(body);
            List<String> rawList = null;
            if (root.isArray()) {
                rawList = toStringList(root);
            } else if (root.isObject()) {
                for (String path : new String[]{"data/proxies", "data/proxy_list", "proxies", "proxy_list"}) {
                    JsonNode node = root.at("/" + path);
                    if (node.isArray()) { rawList = toStringList(node); break; }
                }
            }
            if (rawList != null) {
                return rawList.stream().map(this::parseIpPort)
                        .filter(m -> m != null)
                        .collect(Collectors.toList());
            }
        } catch (Exception ignored) {}
        // Fallback: newline-separated text
        return body.lines().map(String::trim)
                .map(this::parseIpPort)
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }

    private List<String> toStringList(JsonNode arr) {
        List<String> list = new ArrayList<>();
        arr.forEach(n -> { if (n.isTextual()) list.add(n.textValue()); });
        return list;
    }

    private Map<String, Object> parseIpPort(String s) {
        if (s == null || s.isBlank()) return null;
        String[] parts = s.split(":");
        if (parts.length < 2) return null;
        try {
            int port = Integer.parseInt(parts[1].trim());
            return Map.of("ip", parts[0].trim(), "port", port);
        } catch (NumberFormatException e) { return null; }
    }

    // --- Request Records ---

    record ProxyAddRequest(
            @NotBlank String ip,
            @Min(1) @Max(65535) int port,
            @NotBlank String protocol,
            String region
    ) {}

    record BatchImportRequest(
            @NotEmpty List<String> lines,
            @NotBlank String providerName
    ) {}

    record FetchPreviewRequest(
            @NotBlank String baseUrl,
            List<ParamEntry> params
    ) {}

    record ParamEntry(String key, String value) {}
}
