package com.rpacloud.integration.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.rpacloud.execution.engine.handlers.HandlerResult;
import com.rpacloud.execution.engine.handlers.LlmAgentHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Real API verification for LlmAgentHandler.
 * Only runs when LLM_API_KEY environment variable is set.
 *
 * Verifies that the configured LLM API (e.g. gemini-2.5-flash via OpenAI-compatible proxy)
 * correctly supports tool calling protocol with Spring AI.
 *
 * Run manually: LLM_API_KEY=sk-xxx LLM_API_BASE_URL=https://x666.me/v1 LLM_MODEL=gemini-2.5-flash mvn ...
 */
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
class LlmAgentRealApiIT extends PlaywrightBaseIT {

    private static HttpServer credentialServer;
    private static String credentialBaseUrl;

    @BeforeAll
    static void startCredentialServer() throws Exception {
        String apiKey = System.getenv("LLM_API_KEY");
        String baseUrl = System.getenv().getOrDefault("LLM_API_BASE_URL", "https://x666.me/v1");
        String model = System.getenv().getOrDefault("LLM_MODEL", "gemini-2.5-flash");

        credentialServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = credentialServer.getAddress().getPort();
        credentialBaseUrl = "http://127.0.0.1:" + port;

        credentialServer.createContext("/internal/llm/credential", exchange -> {
            String body = """
                    {"api_key":"%s","base_url":"%s","model":"%s"}
                    """.formatted(apiKey, baseUrl, model).strip();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });

        credentialServer.setExecutor(null);
        credentialServer.start();
    }

    @AfterAll
    static void stopCredentialServer() {
        if (credentialServer != null) credentialServer.stop(0);
    }

    @Test
    void realApi_simpleGoal_extractsPageTitle() throws Exception {
        LlmAgentHandler handler = new LlmAgentHandler(credentialBaseUrl, "test-token", screenshotDir);
        Map<String, Object> params = Map.of(
                "goal", "Get the page title and the text content of the element with id 'greeting'. "
                        + "Return both values in your final response.",
                "max_steps", 5,
                "save_to", "result"
        );
        Map<String, Object> variables = new HashMap<>();

        HandlerResult result = handler.handle(page, params, variables);

        // Loose assertions — LLM output is non-deterministic
        assertThat(result.getMessage()).isNotBlank();
        assertThat(result.getTotalTokensUsed()).isGreaterThan(0);
        assertThat(variables).containsKey("result");

        System.out.println("[LlmAgentRealApiIT] Message: " + result.getMessage());
        System.out.println("[LlmAgentRealApiIT] Tokens used: " + result.getTotalTokensUsed());
    }
}
