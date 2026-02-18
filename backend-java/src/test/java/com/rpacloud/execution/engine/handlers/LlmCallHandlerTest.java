package com.rpacloud.execution.engine.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmCallHandlerTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void handleSuccess() throws Exception {
        server.createContext("/internal/llm/chat", exchange -> {
            String body = "{\"response\":\"AI answer\",\"tokens_used\":42,\"model\":\"test\"}";
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        server.start();

        LlmCallHandler handler = new LlmCallHandler("http://localhost:" + port, "test-token");
        Map<String, Object> params = Map.of("prompt", "What is 1+1?", "save_to", "result");
        Map<String, Object> variables = new HashMap<>();

        HandlerResult result = handler.handle(params, variables);

        assertThat(result.getMessage()).contains("42");
        assertThat(variables).containsEntry("result", "AI answer");
    }

    @Test
    void handleWithVariableResolution() throws Exception {
        server.createContext("/internal/llm/chat", exchange -> {
            // Read request body to verify variable resolution
            String reqBody = new String(exchange.getRequestBody().readAllBytes());
            String body = "{\"response\":\"resolved\",\"tokens_used\":10,\"model\":\"test\"}";
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        server.start();

        LlmCallHandler handler = new LlmCallHandler("http://localhost:" + port, "test-token");
        Map<String, Object> params = Map.of("prompt", "Analyze $page_text", "save_to", "$analysis");
        Map<String, Object> variables = new HashMap<>();
        variables.put("page_text", "Hello world");

        HandlerResult result = handler.handle(params, variables);

        assertThat(variables).containsEntry("analysis", "resolved");
    }

    @Test
    void handleEmptyPromptSkips() throws Exception {
        LlmCallHandler handler = new LlmCallHandler("http://localhost:" + port, "test-token");
        Map<String, Object> params = Map.of("prompt", "", "save_to", "result");
        Map<String, Object> variables = new HashMap<>();

        HandlerResult result = handler.handle(params, variables);

        assertThat(result.getMessage()).contains("skipped");
        assertThat(variables).doesNotContainKey("result");
    }

    @Test
    void handleMissingApiUrlThrows() {
        LlmCallHandler handler = new LlmCallHandler(null, null);
        Map<String, Object> params = Map.of("prompt", "test", "save_to", "result");

        assertThatThrownBy(() -> handler.handle(params, new HashMap<>()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("--internal-api-url");
    }

    @Test
    void handleHttpErrorThrows() throws Exception {
        server.createContext("/internal/llm/chat", exchange -> {
            String body = "{\"error\":\"unauthorized\"}";
            exchange.sendResponseHeaders(401, body.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        server.start();

        LlmCallHandler handler = new LlmCallHandler("http://localhost:" + port, "test-token");
        Map<String, Object> params = Map.of("prompt", "test", "save_to", "result");

        assertThatThrownBy(() -> handler.handle(params, new HashMap<>()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("HTTP 401");
    }

    @Test
    void handleSaveToWithDollarPrefix() throws Exception {
        server.createContext("/internal/llm/chat", exchange -> {
            String body = "{\"response\":\"data\",\"tokens_used\":5,\"model\":\"m\"}";
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        server.start();

        LlmCallHandler handler = new LlmCallHandler("http://localhost:" + port, "test-token");
        Map<String, Object> params = Map.of("prompt", "test", "save_to", "$my_var");
        Map<String, Object> variables = new HashMap<>();

        handler.handle(params, variables);

        assertThat(variables).containsEntry("my_var", "data");
    }
}
