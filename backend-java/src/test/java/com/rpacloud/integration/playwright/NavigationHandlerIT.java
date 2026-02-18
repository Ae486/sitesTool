package com.rpacloud.integration.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpacloud.execution.engine.handlers.HandlerResult;
import com.rpacloud.execution.engine.handlers.NavigationHandler;
import org.junit.jupiter.api.Test;

class NavigationHandlerIT extends PlaywrightBaseIT {

    private final NavigationHandler handler = new NavigationHandler();

    @Test
    void navigate_loadsPage() {
        HandlerResult r = handler.navigate(page, params("url", baseUrl + "/test-page.html", "wait_until", "load"), variables);
        assertThat(r.getMessage()).contains(baseUrl);
        assertThat(page.url()).contains("/test-page.html");
        assertThat(page.title()).isEqualTo("Automation Test Page");
    }

    @Test
    void navigate_networkIdle() {
        HandlerResult r = handler.navigate(page, params("url", baseUrl + "/test-page.html", "wait_until", "networkidle"), variables);
        assertThat(r.getMessage()).contains("Navigated");
    }

    @Test
    void newTab_opensPage() {
        HandlerResult r = handler.newTab(page, params("url", baseUrl + "/test-page.html"), variables);
        assertThat(page.context().pages()).hasSize(2);
        assertThat(r.getMessage()).contains("new tab");
    }

    @Test
    void newTab_storesTabVariable() {
        HandlerResult r = handler.newTab(page, params("url", baseUrl + "/test-page.html", "tab_variable", "idx"), variables);
        assertThat(r.getExtractedData()).containsKey("idx");
    }

    @Test
    void switchTab_switchesCorrectly() {
        handler.newTab(page, params("url", baseUrl + "/frame-page.html"), variables);
        assertThat(page.context().pages()).hasSize(2);
        handler.switchTab(page, params("index", 0), variables);
        // first tab should still be test-page
        assertThat(page.context().pages().get(0).title()).isEqualTo("Automation Test Page");
    }

    @Test
    void closeTab_closesPage() {
        handler.newTab(page, params("url", baseUrl + "/test-page.html"), variables);
        assertThat(page.context().pages()).hasSize(2);
        // close the second tab
        var secondPage = page.context().pages().get(1);
        handler.closeTab(secondPage, params(), variables);
        assertThat(page.context().pages()).hasSize(1);
    }

    @Test
    void frameSwitch_accessesIframe() {
        page.navigate(baseUrl + "/frame-page.html");
        HandlerResult r = handler.frameSwitch(page, params("selector", "#test-frame"), variables);
        assertThat(r.getMessage()).contains("#test-frame");
        String frameText = page.frameLocator("#test-frame").locator("#frame-title").textContent();
        assertThat(frameText).isEqualTo("Inside Frame");
    }

    @Test
    void frameMain_returnsOk() {
        HandlerResult r = handler.frameMain(page, params(), variables);
        assertThat(r.getMessage()).contains("main frame");
    }
}
