package com.rpacloud.integration.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.rpacloud.execution.engine.handlers.HandlerResult;
import com.rpacloud.execution.engine.handlers.LlmAgentHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Deterministic IT for LlmAgentHandler using a mock OpenAI-compatible API.
 * Verifies the full agent loop: credential fetch → LLM tool_call → Playwright tool execution → final answer.
 *
 * No real LLM API needed — pre-scripted responses simulate a 2-round agent interaction.
 */
class LlmAgentMockIT extends PlaywrightBaseIT {

    private static HttpServer apiServer;
    private static String apiBaseUrl;
    private static final AtomicInteger completionCallCount = new AtomicInteger(0);

    @BeforeAll
    static void startApiServer() throws Exception {
        apiServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = apiServer.getAddress().getPort();
        apiBaseUrl = "http://127.0.0.1:" + port;

        // Mock: /internal/llm/credential
        apiServer.createContext("/internal/llm/credential", exchange -> {
            String body = """
                    {"api_key":"test-key","base_url":"%s","model":"mock-model"}
                    """.formatted(apiBaseUrl).strip();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });

        // Mock: /v1/chat/completions (OpenAI-compatible)
        apiServer.createContext("/v1/chat/completions", exchange -> {
            int round = completionCallCount.incrementAndGet();
            String body;
            if (round == 1) {
                // Round 1: LLM calls getPageContent() to observe the page
                body = toolCallResponse("call_1", "getPageContent", "{}", 120);
            } else if (round == 2) {
                // Round 2: LLM calls getText(selector) to extract specific element
                body = toolCallResponse("call_2", "getText", """
                        {"selector":"#greeting"}""", 80);
            } else {
                // Round 3: LLM produces final answer
                body = finalResponse("I observed the page. The greeting says: Hello, World!", 250);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });

        apiServer.setExecutor(null);
        apiServer.start();
    }

    @AfterAll
    static void stopApiServer() {
        if (apiServer != null) apiServer.stop(0);
    }

    @Override
    @BeforeEach
    void setUp() {
        super.setUp();
        completionCallCount.set(0);
    }

    @Test
    void agentLoop_mockToolCalling_success() throws Exception {
        LlmAgentHandler handler = new LlmAgentHandler(apiBaseUrl, "test-token", screenshotDir);
        Map<String, Object> params = Map.of(
                "goal", "Read the page and tell me what the greeting says",
                "save_to", "agent_result"
        );
        Map<String, Object> variables = new HashMap<>();

        HandlerResult result = handler.handle(page, params, variables);

        assertThat(result.getMessage()).contains("Hello, World!");
        assertThat(result.getTotalTokensUsed()).isEqualTo(120 + 80 + 250);
        assertThat(variables).containsKey("agent_result");
        assertThat(variables.get("agent_result").toString()).contains("Hello, World!");
        assertThat(completionCallCount.get()).isEqualTo(3);
    }

    @Test
    void agentLoop_singleRound_noToolCall() throws Exception {
        // Override: make round 1 return final answer directly
        completionCallCount.set(2); // skip to round 3 logic

        LlmAgentHandler handler = new LlmAgentHandler(apiBaseUrl, "test-token", screenshotDir);
        Map<String, Object> params = Map.of("goal", "Just say hello");
        Map<String, Object> variables = new HashMap<>();

        HandlerResult result = handler.handle(page, params, variables);

        assertThat(result.getMessage()).isNotBlank();
        assertThat(result.getTotalTokensUsed()).isGreaterThan(0);
    }

    @Test
    void agentLoop_savesToVariable() throws Exception {
        LlmAgentHandler handler = new LlmAgentHandler(apiBaseUrl, "test-token", screenshotDir);
        Map<String, Object> params = Map.of(
                "goal", "Extract the greeting",
                "save_to", "$greeting_text"
        );
        Map<String, Object> variables = new HashMap<>();

        HandlerResult result = handler.handle(page, params, variables);

        // save_to strips $ prefix
        assertThat(variables).containsKey("greeting_text");
        assertThat(result.getExtractedData()).containsKey("greeting_text");
    }

    // --- Helper: build OpenAI-compatible JSON responses ---

    private static String toolCallResponse(String callId, String functionName, String arguments, int totalTokens) {
        return """
                {
                  "id": "chatcmpl-mock",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "mock-model",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [{
                        "id": "%s",
                        "type": "function",
                        "function": {
                          "name": "%s",
                          "arguments": %s
                        }
                      }]
                    },
                    "finish_reason": "tool_calls"
                  }],
                  "usage": {
                    "prompt_tokens": %d,
                    "completion_tokens": %d,
                    "total_tokens": %d
                  }
                }
                """.formatted(
                callId, functionName,
                // arguments needs to be a JSON string value
                "\"" + arguments.replace("\"", "\\\"").replace("\n", "").replace(" ", "") + "\"",
                totalTokens - 20, 20, totalTokens);
    }

    private static String finalResponse(String content, int totalTokens) {
        return """
                {
                  "id": "chatcmpl-final",
                  "object": "chat.completion",
                  "created": 1700000001,
                  "model": "mock-model",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": "%s"
                    },
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": %d,
                    "completion_tokens": %d,
                    "total_tokens": %d
                  }
                }
                """.formatted(
                content.replace("\"", "\\\""),
                totalTokens - 50, 50, totalTokens);
    }
}
