package com.rpacloud.proxy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.config.RpaProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeoIpServiceTest {

    private HttpServer httpServer;
    private GeoIpService geoIpService;
    private String singleResponse;
    private String batchResponse;

    @BeforeEach
    void setUp() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        int port = httpServer.getAddress().getPort();

        httpServer.createContext("/json/", exchange -> {
            byte[] bytes = singleResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        httpServer.createContext("/batch", exchange -> {
            byte[] bytes = batchResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        httpServer.start();

        RpaProperties props = new RpaProperties();
        props.setGeoIp(new RpaProperties.GeoIp());
        props.getGeoIp().setBaseUrl("http://localhost:" + port);
        geoIpService = new GeoIpService(props, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) httpServer.stop(0);
    }

    // ── guard conditions (no HTTP) ──────────────────────────────

    @Test
    void lookupRegion_nullIp_returnsEmpty() {
        assertThat(geoIpService.lookupRegion(null)).isEmpty();
    }

    @Test
    void lookupRegion_blankIp_returnsEmpty() {
        assertThat(geoIpService.lookupRegion("  ")).isEmpty();
    }

    @Test
    void lookupRegionBatch_nullList_returnsEmptyMap() {
        assertThat(geoIpService.lookupRegionBatch(null)).isEmpty();
    }

    @Test
    void lookupRegionBatch_emptyList_returnsEmptyMap() {
        assertThat(geoIpService.lookupRegionBatch(List.of())).isEmpty();
    }

    // ── response parsing ────────────────────────────────────────

    @Test
    void lookupRegion_successResponse_returnsCountryDashRegion() {
        singleResponse = "{\"status\":\"success\",\"countryCode\":\"CN\",\"regionName\":\"Guangdong\"}";

        Optional<String> result = geoIpService.lookupRegion("1.2.3.4");

        assertThat(result).isPresent().hasValue("CN-Guangdong");
    }

    @Test
    void lookupRegion_successNoRegionName_returnsCountryOnly() {
        singleResponse = "{\"status\":\"success\",\"countryCode\":\"US\",\"regionName\":\"\"}";

        Optional<String> result = geoIpService.lookupRegion("8.8.8.8");

        assertThat(result).isPresent().hasValue("US");
    }

    @Test
    void lookupRegion_failStatus_returnsEmpty() {
        singleResponse = "{\"status\":\"fail\",\"message\":\"reserved range\"}";

        assertThat(geoIpService.lookupRegion("127.0.0.1")).isEmpty();
    }

    @Test
    void lookupRegionBatch_successResponse_parsesMap() {
        batchResponse = "[{\"status\":\"success\",\"query\":\"1.2.3.4\",\"countryCode\":\"JP\",\"regionName\":\"Tokyo\"},"
                + "{\"status\":\"fail\",\"query\":\"0.0.0.0\"}]";

        Map<String, String> result = geoIpService.lookupRegionBatch(List.of("1.2.3.4", "0.0.0.0"));

        assertThat(result).containsEntry("1.2.3.4", "JP-Tokyo");
        assertThat(result).doesNotContainKey("0.0.0.0");
    }

    // ── error handling ──────────────────────────────────────────

    @Test
    void lookupRegion_connectionRefused_returnsEmpty() {
        RpaProperties props = new RpaProperties();
        props.setGeoIp(new RpaProperties.GeoIp());
        props.getGeoIp().setBaseUrl("http://localhost:1");
        GeoIpService unreachable = new GeoIpService(props, new ObjectMapper());

        // Should not throw — all errors swallowed
        assertThat(unreachable.lookupRegion("1.2.3.4")).isEmpty();
    }
}
