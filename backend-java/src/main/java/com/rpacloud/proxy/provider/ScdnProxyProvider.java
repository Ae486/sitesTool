package com.rpacloud.proxy.provider;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.proxy.provider.ProxyProviderAdapter.ProxyInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@Profile("enterprise")
@RequiredArgsConstructor
public class ScdnProxyProvider implements ProxyProviderAdapter {

    private final RestTemplateBuilder restTemplateBuilder;
    private final RpaProperties rpaProperties;

    @Override
    public List<ProxyInfo> fetch(String protocol, int count, String countryCode) {
        String normalizedProtocol = normalizeProtocol(protocol);
        int safeCount = Math.max(count, 1);

        String url = UriComponentsBuilder
                .fromHttpUrl(rpaProperties.getProxy().getProxyProviderUrl())
                .queryParam("protocol", normalizedProtocol.toLowerCase(Locale.ROOT))
                .queryParam("count", safeCount)
                .queryParamIfPresent("country", Optional.ofNullable(countryCode).filter(v -> !v.isBlank()))
                .toUriString();

        RestTemplate restTemplate = restTemplateBuilder.build();
        try {
            ProviderResponse response = restTemplate.getForObject(url, ProviderResponse.class);
            if (response == null || response.code() == null || response.code() != 200
                    || response.data() == null || response.data().proxies() == null) {
                log.warn("SCDN provider returned unexpected response: {}", response);
                return List.of();
            }
            return response.data().proxies().stream()
                    .map(value -> parseProxy(value, normalizedProtocol))
                    .flatMap(Optional::stream)
                    .toList();
        } catch (RestClientException ex) {
            log.error("Failed to fetch proxies from SCDN provider: {}", ex.getMessage());
            return List.of();
        }
    }

    private Optional<ProxyInfo> parseProxy(String endpoint, String protocol) {
        if (endpoint == null || endpoint.isBlank()) {
            return Optional.empty();
        }

        int separatorIndex = endpoint.lastIndexOf(':');
        if (separatorIndex <= 0 || separatorIndex >= endpoint.length() - 1) {
            log.warn("Ignore invalid proxy endpoint: {}", endpoint);
            return Optional.empty();
        }

        String ip = endpoint.substring(0, separatorIndex).replace("[", "").replace("]", "");
        String portText = endpoint.substring(separatorIndex + 1);

        try {
            int port = Integer.parseInt(portText);
            if (port <= 0 || port > 65535) {
                return Optional.empty();
            }
            return Optional.of(new ProxyInfo(ip, port, protocol));
        } catch (NumberFormatException ex) {
            log.warn("Ignore invalid proxy endpoint: {}", endpoint);
            return Optional.empty();
        }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProviderResponse(Integer code, ProviderData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProviderData(List<String> proxies, Integer count) {
    }
}
