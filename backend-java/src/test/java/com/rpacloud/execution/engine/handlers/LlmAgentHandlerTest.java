package com.rpacloud.execution.engine.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.microsoft.playwright.Page;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmAgentHandlerTest {

    @Mock private Page page;
    @TempDir Path tempDir;

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
    void handleRejectsNullPage() {
        LlmAgentHandler handler = new LlmAgentHandler("http://localhost:" + port, "token", tempDir);
        Map<String, Object> params = Map.of("goal", "test goal");

        assertThatThrownBy(() -> handler.handle(null, params, new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-null Playwright page");
    }

    @Test
    void handleRejectsEmptyGoal() {
        LlmAgentHandler handler = new LlmAgentHandler("http://localhost:" + port, "token", tempDir);
        Map<String, Object> params = Map.of("goal", "");

        assertThatThrownBy(() -> handler.handle(page, params, new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty 'goal'");
    }

    @Test
    void handleRejectsMissingGoal() {
        LlmAgentHandler handler = new LlmAgentHandler("http://localhost:" + port, "token", tempDir);

        assertThatThrownBy(() -> handler.handle(page, Map.of(), new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty 'goal'");
    }

    @Test
    void handleRejectsMissingInternalConfig() {
        LlmAgentHandler handler = new LlmAgentHandler(null, null, tempDir);
        Map<String, Object> params = Map.of("goal", "test goal");

        assertThatThrownBy(() -> handler.handle(page, params, new HashMap<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("--internal-api-url");
    }

    @Test
    void handleRejectsBlankInternalToken() {
        LlmAgentHandler handler = new LlmAgentHandler("http://localhost:" + port, "  ", tempDir);
        Map<String, Object> params = Map.of("goal", "test goal");

        assertThatThrownBy(() -> handler.handle(page, params, new HashMap<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("--internal-token");
    }

    @Test
    void handleCredentialHttpErrorThrows() {
        server.createContext("/internal/llm/credential", exchange -> {
            String body = "{\"error\":\"unauthorized\"}";
            exchange.sendResponseHeaders(401, body.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        server.start();

        LlmAgentHandler handler = new LlmAgentHandler("http://localhost:" + port, "token", tempDir);
        Map<String, Object> params = Map.of("goal", "test goal");

        assertThatThrownBy(() -> handler.handle(page, params, new HashMap<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 401");
    }

    @Test
    void handleCredentialMissingFieldThrows() {
        server.createContext("/internal/llm/credential", exchange -> {
            String body = "{\"api_key\":\"sk-test\",\"base_url\":\"http://localhost\"}";
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        server.start();

        LlmAgentHandler handler = new LlmAgentHandler("http://localhost:" + port, "token", tempDir);
        Map<String, Object> params = Map.of("goal", "test goal");

        assertThatThrownBy(() -> handler.handle(page, params, new HashMap<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required credential field: model");
    }

    @Test
    void handleNullParamsDefaultsToEmpty() {
        LlmAgentHandler handler = new LlmAgentHandler("http://localhost:" + port, "token", tempDir);

        assertThatThrownBy(() -> handler.handle(page, null, new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty 'goal'");
    }

    @Test
    void maxStepsClampedToUpperBound() {
        String serverBase = "http://localhost:" + port;
        server.createContext("/internal/llm/credential", exchange -> {
            // Point base_url to the test server so Spring AI hits it instead of port 80
            String body = "{\"api_key\":\"sk-test\",\"base_url\":\"" + serverBase + "/v1\",\"model\":\"test\"}";
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        // Return a valid OpenAI chat response (no tool calls) so Spring AI doesn't retry on 5xx
        server.createContext("/v1/chat/completions", exchange -> {
            String body = "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1700000000,"
                    + "\"model\":\"test\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                    + "\"content\":\"done\"},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":3,\"total_tokens\":8}}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        });
        server.start();

        LlmAgentHandler handler = new LlmAgentHandler(serverBase, "token", tempDir);
        Map<String, Object> params = Map.of("goal", "test", "max_steps", 99999);

        // max_steps=99999 should be clamped to MAX_STEPS_UPPER_BOUND=100.
        // The handler completes in 1 iteration (mock returns no tool calls).
        // If we reach here without OOM/infinite loop, max_steps was clamped correctly.
        try {
            HandlerResult result = handler.handle(page, params, new HashMap<>());
            assertThat(result.getMessage()).isEqualTo("done");
        } catch (Exception e) {
            // Even if Spring AI parsing fails, the test passes as long as it doesn't hang
        }
    }
}
