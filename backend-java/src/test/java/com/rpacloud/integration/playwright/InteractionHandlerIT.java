package com.rpacloud.integration.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpacloud.execution.engine.handlers.HandlerResult;
import com.rpacloud.execution.engine.handlers.InteractionHandler;
import org.junit.jupiter.api.Test;

class InteractionHandlerIT extends PlaywrightBaseIT {

    private final InteractionHandler handler = new InteractionHandler();

    @Test
    void click_clicksButton() {
        HandlerResult r = handler.click(page, params("selector", "#btn-click", "timeout", 5000), variables);
        assertThat(r.getMessage()).contains("#btn-click");
        assertThat(page.textContent("#btn-click")).isEqualTo("Clicked!");
    }

    @Test
    void input_fillsField() {
        handler.input(page, params("selector", "#text-input", "value", "hello world", "clear", true), variables);
        assertThat(page.inputValue("#text-input")).isEqualTo("hello world");
    }

    @Test
    void input_clearsFirst() {
        assertThat(page.inputValue("#prefilled-input")).isEqualTo("old value");
        handler.input(page, params("selector", "#prefilled-input", "value", "new value", "clear", true), variables);
        assertThat(page.inputValue("#prefilled-input")).isEqualTo("new value");
    }

    @Test
    void select_choosesOption() {
        handler.select(page, params("selector", "#color-select", "value", "green"), variables);
        assertThat(page.inputValue("#color-select")).isEqualTo("green");
    }

    @Test
    void checkbox_toggles() {
        handler.checkbox(page, params("selector", "#checkbox-agree", "checked", true), variables);
        assertThat(page.isChecked("#checkbox-agree")).isTrue();

        handler.checkbox(page, params("selector", "#checkbox-agree", "checked", false), variables);
        assertThat(page.isChecked("#checkbox-agree")).isFalse();
    }

    @Test
    void scroll_toElement() {
        handler.scroll(page, params("selector", "#scroll-target"), variables);
        boolean inViewport = (boolean) page.evaluate(
                "document.getElementById('scroll-target').getBoundingClientRect().top < window.innerHeight");
        assertThat(inViewport).isTrue();
    }

    @Test
    void scroll_byCoordinates() {
        handler.scroll(page, params("x", 0, "y", 500), variables);
        int scrollY = (int) page.evaluate("window.scrollY");
        assertThat(scrollY).isGreaterThan(0);
    }

    @Test
    void hover_triggersState() {
        handler.hover(page, params("selector", "#hover-target"), variables);
        String classes = page.getAttribute("#hover-target", "class");
        assertThat(classes).contains("hovered");
    }

    @Test
    void keyboard_pressesKey() {
        page.focus("#text-input");
        handler.keyboard(page, params("key", "a"), variables);
        assertThat(page.inputValue("#text-input")).contains("a");
    }

    @Test
    void tryClick_succeeds() {
        HandlerResult r = handler.tryClick(page, params("selector", "#btn-click", "timeout", 3000), variables);
        assertThat(r.getMessage()).contains("Clicked");
        assertThat(page.textContent("#btn-click")).isEqualTo("Clicked!");
    }

    @Test
    void tryClick_skipsMissing() {
        HandlerResult r = handler.tryClick(page, params("selector", "#nonexistent", "timeout", 500), variables);
        assertThat(r.getMessage()).contains("Skipped");
    }
}
