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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Real-world website integration tests for LlmAgentHandler.
 * Only runs when LLM_API_KEY environment variable is set.
 *
 * Tests the full agent loop against live websites (quotes/books.toscrape.com).
 * Tests are ordered with cooldown delays to respect API rate limits.
 *
 * Run all:  LLM_API_KEY=sk-xxx mvn test-compile failsafe:integration-test -Dit.test=LlmAgentRealWebIT
 * Run one:  LLM_API_KEY=sk-xxx mvn test-compile failsafe:integration-test -Dit.test="LlmAgentRealWebIT#realWeb_extractQuoteAndAuthor"
 */
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LlmAgentRealWebIT extends PlaywrightBaseIT {

    private static HttpServer credentialServer;
    private static String credentialBaseUrl;
    private static volatile long lastTestEndTime = 0;

    /** Cooldown between tests to avoid 429 rate limits (seconds). */
    private static final int COOLDOWN_SECONDS = 65;

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

    @Override
    @BeforeEach
    void setUp() {
        super.setUp();
        waitForCooldown();
    }

    private static void waitForCooldown() {
        if (lastTestEndTime == 0) return;
        long elapsed = (System.currentTimeMillis() - lastTestEndTime) / 1000;
        long remaining = COOLDOWN_SECONDS - elapsed;
        if (remaining > 0) {
            System.out.printf("[RealWebIT] Rate limit cooldown: waiting %ds...%n", remaining);
            try { Thread.sleep(remaining * 1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
    }

    private static void markTestEnd() {
        lastTestEndTime = System.currentTimeMillis();
    }

    @Test
    @Order(1)
    void realWeb_extractQuoteAndAuthor() throws Exception {
        LlmAgentHandler handler = new LlmAgentHandler(credentialBaseUrl, "test-token", screenshotDir);
        Map<String, Object> params = Map.of(
                "goal", "Navigate to https://quotes.toscrape.com/ and extract the first quote text "
                        + "and its author name. Return both values clearly in your response.",
                "max_steps", 10,
                "save_to", "quote_result"
        );
        Map<String, Object> variables = new HashMap<>();

        HandlerResult result = handler.handle(page, params, variables);
        markTestEnd();

        assertThat(result.getMessage()).isNotBlank();
        assertThat(result.getTotalTokensUsed()).isGreaterThan(0);
        assertThat(variables).containsKey("quote_result");

        String msg = result.getMessage().toLowerCase();
        assertThat(msg).containsAnyOf("einstein", "world", "thinking", "process");

        System.out.println("[RealWeb-Quote] Message: " + result.getMessage());
        System.out.println("[RealWeb-Quote] Tokens: " + result.getTotalTokensUsed());
    }

    @Test
    @Order(2)
    void realWeb_navigateCategoryAndExtractBooks() throws Exception {
        LlmAgentHandler handler = new LlmAgentHandler(credentialBaseUrl, "test-token", screenshotDir);
        Map<String, Object> params = Map.of(
                "goal", "Navigate to https://books.toscrape.com/, click on the 'Travel' category link "
                        + "in the left sidebar, then extract the titles and prices of the first 3 books "
                        + "shown in that category. Return them in a structured format.",
                "max_steps", 10,
                "save_to", "books_result"
        );
        Map<String, Object> variables = new HashMap<>();

        HandlerResult result = handler.handle(page, params, variables);
        markTestEnd();

        assertThat(result.getMessage()).isNotBlank();
        assertThat(result.getTotalTokensUsed()).isGreaterThan(0);
        assertThat(variables).containsKey("books_result");

        assertThat(result.getMessage()).containsAnyOf("£", "price", "Price");

        System.out.println("[RealWeb-Books] Message: " + result.getMessage());
        System.out.println("[RealWeb-Books] Tokens: " + result.getTotalTokensUsed());
    }

    @Test
    @Order(3)
    void realWeb_loginFormInteraction() throws Exception {
        LlmAgentHandler handler = new LlmAgentHandler(credentialBaseUrl, "test-token", screenshotDir);
        Map<String, Object> params = Map.of(
                "goal", "Navigate to https://quotes.toscrape.com/login, fill in the username field "
                        + "with 'admin' and password field with 'admin', then click the Login button. "
                        + "After login, report what changed on the page compared to before login.",
                "max_steps", 10,
                "save_to", "login_result"
        );
        Map<String, Object> variables = new HashMap<>();

        HandlerResult result = handler.handle(page, params, variables);
        markTestEnd();

        assertThat(result.getMessage()).isNotBlank();
        assertThat(result.getTotalTokensUsed()).isGreaterThan(0);
        assertThat(variables).containsKey("login_result");

        String msg = result.getMessage().toLowerCase();
        assertThat(msg).containsAnyOf("logout", "goodreads", "logged", "login");

        System.out.println("[RealWeb-Login] Message: " + result.getMessage());
        System.out.println("[RealWeb-Login] Tokens: " + result.getTotalTokensUsed());
    }
}
