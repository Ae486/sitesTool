package com.rpacloud.execution.engine.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HttpRequestHandlerTest {

    private static HttpServer server;
    private static int port;
    private final HttpRequestHandler handler = new HttpRequestHandler();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/echo", exchange -> {
            byte[] reqBody = exchange.getRequestBody().readAllBytes();
            String method = exchange.getRequestMethod();
            String responseBody = "{\"method\":\"" + method + "\",\"body\":\"" + new String(reqBody) + "\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBody.getBytes());
            }
        });

        server.createContext("/status/404", exchange -> {
            exchange.sendResponseHeaders(404, 0);
            exchange.getResponseBody().close();
        });

        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    private Map<String, Object> baseParams(String path, String method) {
        Map<String, Object> params = new HashMap<>();
        params.put("url", "http://127.0.0.1:" + port + path);
        params.put("method", method);
        params.put("save_to", "result");
        return params;
    }

    @Test
    void getRequest() throws Exception {
        HandlerResult result = handler.handleWithoutSsrf(baseParams("/echo", "GET"), new HashMap<>());
        assertThat(result.getExtractedData()).containsKey("result");
        assertThat(result.getExtractedData().get("result_status")).isEqualTo(200);
    }

    @Test
    void postRequestWithBody() throws Exception {
        Map<String, Object> params = baseParams("/echo", "POST");
        params.put("body", Map.of("key", "value"));

        HandlerResult result = handler.handleWithoutSsrf(params, new HashMap<>());
        assertThat(result.getMessage()).contains("POST");
        assertThat(result.getExtractedData().get("result_status")).isEqualTo(200);
        assertThat((String) result.getExtractedData().get("result")).contains("key");
    }

    @Test
    void putRequest() throws Exception {
        Map<String, Object> params = baseParams("/echo", "PUT");
        params.put("body", "update data");

        HandlerResult result = handler.handleWithoutSsrf(params, new HashMap<>());
        assertThat(result.getMessage()).contains("PUT");
    }

    @Test
    void deleteRequest() throws Exception {
        HandlerResult result = handler.handleWithoutSsrf(baseParams("/echo", "DELETE"), new HashMap<>());
        assertThat(result.getMessage()).contains("DELETE");
    }

    @Test
    void variableResolutionInBody() throws Exception {
        Map<String, Object> params = baseParams("/echo", "POST");
        params.put("body", Map.of("data", "${myvar}"));

        Map<String, Object> variables = new HashMap<>(Map.of("myvar", "resolved_value"));
        HandlerResult result = handler.handleWithoutSsrf(params, variables);
        assertThat((String) result.getExtractedData().get("result")).contains("resolved_value");
    }

    @Test
    void variableResolutionInHeaders() throws Exception {
        Map<String, Object> params = baseParams("/echo", "GET");
        params.put("headers", Map.of("X-Custom", "${token}"));

        Map<String, Object> variables = new HashMap<>(Map.of("token", "abc123"));
        HandlerResult result = handler.handleWithoutSsrf(params, variables);
        assertThat(result.getExtractedData().get("result_status")).isEqualTo(200);
    }

    @Test
    void ssrfBlocksPrivateUrl() {
        Map<String, Object> params = new HashMap<>();
        params.put("url", "http://10.0.0.1/internal");
        params.put("method", "GET");

        assertThatThrownBy(() -> handler.handle(params, Map.of()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void handles404Response() throws Exception {
        HandlerResult result = handler.handleWithoutSsrf(baseParams("/status/404", "GET"), new HashMap<>());
        assertThat(result.getExtractedData().get("result_status")).isEqualTo(404);
    }

    @Test
    void noSaveToOmitsData() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("url", "http://127.0.0.1:" + port + "/echo");
        params.put("method", "GET");

        HandlerResult result = handler.handleWithoutSsrf(params, new HashMap<>());
        assertThat(result.getExtractedData()).isEmpty();
        assertThat(result.getMessage()).contains("GET");
    }
}
