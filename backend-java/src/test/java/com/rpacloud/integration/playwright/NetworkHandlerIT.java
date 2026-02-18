package com.rpacloud.integration.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpacloud.execution.engine.handlers.HandlerResult;
import com.rpacloud.execution.engine.handlers.NetworkHandler;
import org.junit.jupiter.api.Test;

class NetworkHandlerIT extends PlaywrightBaseIT {

    private final NetworkHandler handler = new NetworkHandler();

    @Test
    void captureNetwork_capturesMatchingResponse() {
        handler.captureNetwork(page, params("url_pattern", ".*/api/data.*", "save_to", "resp"), variables);
        page.click("#btn-fetch");
        page.waitForTimeout(2000);

        assertThat(variables).containsKey("resp");
        assertThat(variables.get("resp").toString()).contains("items");
        assertThat(variables.get("resp_status")).isEqualTo(200);
        assertThat(variables.get("resp_url").toString()).contains("/api/data");
    }

    @Test
    void captureNetwork_ignoresNonMatchingUrls() {
        handler.captureNetwork(page, params("url_pattern", ".*/nonexistent.*", "save_to", "missed"), variables);
        page.click("#btn-fetch");
        page.waitForTimeout(1000);

        assertThat(variables).doesNotContainKey("missed");
    }

    @Test
    void waitForNetwork_capturesResponse() {
        // Schedule a fetch to happen in 200ms
        page.evaluate("setTimeout(() => fetch('/api/data'), 200)");

        HandlerResult r = handler.waitForNetwork(page,
                params("url_pattern", "/api/data", "save_to", "waited", "timeout", 5000), variables);

        assertThat(r.getMessage()).contains("status=200");
        assertThat(r.getExtractedData().get("waited_status")).isEqualTo(200);
        assertThat(r.getExtractedData().get("waited").toString()).contains("items");
    }
}
