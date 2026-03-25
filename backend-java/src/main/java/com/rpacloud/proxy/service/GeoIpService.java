package com.rpacloud.proxy.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.config.RpaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoIpService {

    private static final int TIMEOUT_SECONDS = 5;

    private final RpaProperties rpaProperties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();

    public Optional<String> lookupRegion(String ip) {
        if (ip == null || ip.isBlank()) return Optional.empty();
        try {
            String baseUrl = rpaProperties.getGeoIp().getBaseUrl();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/json/" + ip + "?fields=status,countryCode,regionName"))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());
            if ("success".equals(node.path("status").asText())) {
                String country = node.path("countryCode").asText("").trim();
                String region = node.path("regionName").asText("").trim();
                if (!country.isBlank()) {
                    return Optional.of(region.isBlank() ? country : country + "-" + region);
                }
            }
        } catch (Exception ex) {
            log.debug("GeoIP lookup failed for {}: {}", ip, ex.getMessage());
        }
        return Optional.empty();
    }

    public Map<String, String> lookupRegionBatch(List<String> ips) {
        if (ips == null || ips.isEmpty()) return Map.of();
        try {
            String baseUrl = rpaProperties.getGeoIp().getBaseUrl();
            List<Map<String, String>> queries = ips.stream()
                    .map(ip -> Map.of("query", ip))
                    .collect(Collectors.toList());
            String body = objectMapper.writeValueAsString(queries);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/batch?fields=status,countryCode,regionName,query"))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode arr = objectMapper.readTree(response.body());
            Map<String, String> result = new java.util.HashMap<>();
            if (arr.isArray()) {
                for (JsonNode item : arr) {
                    if ("success".equals(item.path("status").asText())) {
                        String ip = item.path("query").asText("").trim();
                        String country = item.path("countryCode").asText("").trim();
                        String region = item.path("regionName").asText("").trim();
                        if (!ip.isBlank() && !country.isBlank()) {
                            result.put(ip, region.isBlank() ? country : country + "-" + region);
                        }
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            log.debug("GeoIP batch lookup failed: {}", ex.getMessage());
        }
        return Map.of();
    }
}
