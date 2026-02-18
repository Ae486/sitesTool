package com.rpacloud.integration.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpacloud.execution.engine.handlers.HandlerResult;
import com.rpacloud.execution.engine.handlers.WaitHandler;
import org.junit.jupiter.api.Test;

class WaitHandlerIT extends PlaywrightBaseIT {

    private final WaitHandler handler = new WaitHandler();

    @Test
    void waitFor_element() {
        // #delayed-element appears after 500ms via setTimeout
        HandlerResult r = handler.waitFor(page, params("selector", "#delayed-element", "timeout", 3000, "state", "visible"), variables);
        assertThat(r.getMessage()).contains("#delayed-element");
        assertThat(page.textContent("#delayed-element")).isEqualTo("I appeared!");
    }

    @Test
    void waitTime_sleeps() throws Exception {
        long start = System.currentTimeMillis();
        handler.waitTime(page, params("duration", 100), variables);
        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isGreaterThanOrEqualTo(90); // small tolerance
    }

    @Test
    void randomDelay_inRange() throws Exception {
        long start = System.currentTimeMillis();
        handler.randomDelay(page, params("min", 50, "max", 150), variables);
        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isBetween(40L, 300L); // generous tolerance
    }
}
