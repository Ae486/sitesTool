package com.rpacloud.integration.playwright;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpacloud.execution.engine.handlers.AssertionHandler;
import com.rpacloud.execution.engine.handlers.HandlerResult;
import org.junit.jupiter.api.Test;

class AssertionHandlerIT extends PlaywrightBaseIT {

    private final AssertionHandler handler = new AssertionHandler();

    @Test
    void assertText_passes() {
        HandlerResult r = handler.assertText(page, params("selector", "#greeting", "expected", "Hello"), variables);
        assertThat(r.getMessage()).contains("passed");
    }

    @Test
    void assertText_fails() {
        assertThatThrownBy(() ->
                handler.assertText(page, params("selector", "#greeting", "expected", "Goodbye"), variables))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void assertVisible_passes() {
        HandlerResult r = handler.assertVisible(page, params("selector", "#title", "timeout", 3000), variables);
        assertThat(r.getMessage()).contains("visible");
    }

    @Test
    void ifExists_true() {
        HandlerResult r = handler.ifExists(page, params("selector", "#conditional-box", "variable", "found", "timeout", 1000), variables);
        assertThat(r.getExtractedData().get("found")).isEqualTo(true);
    }

    @Test
    void ifExists_false() {
        HandlerResult r = handler.ifExists(page, params("selector", "#nonexistent", "variable", "found", "timeout", 500), variables);
        assertThat(r.getExtractedData().get("found")).isEqualTo(false);
    }
}
