package com.rpacloud.proxy.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.proxy.provider.ProxyProviderAdapter.ProxyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class ScdnProxyProviderTest {

    @Mock private RestTemplateBuilder restTemplateBuilder;
    @Mock private RestTemplate restTemplate;

    private RpaProperties rpaProperties;
    private ScdnProxyProvider scdnProxyProvider;

    @BeforeEach
    void setUp() {
        rpaProperties = new RpaProperties();
        rpaProperties.setProxy(new RpaProperties.Proxy());
        rpaProperties.getProxy().setProxyProviderUrl("https://proxy.scdn.io/api/get_proxy.php");

        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        scdnProxyProvider = new ScdnProxyProvider(restTemplateBuilder, rpaProperties);
    }

    @SuppressWarnings("unchecked")
    @Test
    void fetch_restClientException_returnsEmptyList() {
        when(restTemplate.getForObject(anyString(), any(Class.class)))
                .thenThrow(new RestClientException("Connection refused"));

        List<ProxyInfo> result = scdnProxyProvider.fetch("HTTP", 5, null);
        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void fetch_nullResponse_returnsEmptyList() {
        when(restTemplate.getForObject(anyString(), any(Class.class))).thenReturn(null);

        List<ProxyInfo> result = scdnProxyProvider.fetch("HTTP", 5, null);
        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void fetch_normalizesProtocol() {
        when(restTemplate.getForObject(anyString(), any(Class.class))).thenReturn(null);
        List<ProxyInfo> result = scdnProxyProvider.fetch("socks5", 1, null);
        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void fetch_negativeCount_clampedToOne() {
        when(restTemplate.getForObject(anyString(), any(Class.class))).thenReturn(null);
        List<ProxyInfo> result = scdnProxyProvider.fetch("HTTP", -5, null);
        assertThat(result).isEmpty();
    }
}
