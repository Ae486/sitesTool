package com.rpacloud.integration.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpacloud.execution.engine.handlers.HandlerResult;
import com.rpacloud.execution.engine.handlers.MiscHandler;
import org.junit.jupiter.api.Test;

class MiscHandlerIT extends PlaywrightBaseIT {

    private final MiscHandler handler = new MiscHandler();

    @Test
    void setVariable_storesValue() {
        HandlerResult r = handler.setVariable(page, params("variable", "foo", "value", "bar"), variables);
        assertThat(r.getExtractedData()).containsEntry("foo", "bar");
    }

    @Test
    void evalJs_returnsValue() {
        HandlerResult r = handler.evalJs(page, params("script", "1+2", "variable", "sum"), variables);
        assertThat(r.getExtractedData().get("sum")).isEqualTo(3);
    }

    @Test
    void evalJs_manipulatesDom() {
        handler.evalJs(page, params("script", "document.getElementById('js-target').textContent='injected'"), variables);
        assertThat(page.textContent("#js-target")).isEqualTo("injected");
    }

    @Test
    void dialogHandle_acceptsAlert() {
        handler.dialogHandle(page, params("action", "accept"), variables);
        page.click("#btn-alert");
        // If dialog was not handled, click would hang/timeout. Reaching here means success.
        assertThat(page.textContent("#title")).isEqualTo("Test Page");
    }
}
