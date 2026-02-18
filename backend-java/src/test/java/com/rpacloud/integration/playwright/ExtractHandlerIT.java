package com.rpacloud.integration.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import com.rpacloud.execution.engine.handlers.ExtractHandler;
import com.rpacloud.execution.engine.handlers.HandlerResult;
import org.junit.jupiter.api.Test;

class ExtractHandlerIT extends PlaywrightBaseIT {

    private final ExtractHandler handler = new ExtractHandler(screenshotDir);

    @Test
    void extract_getsTextContent() {
        HandlerResult r = handler.extract(page, params("selector", "#greeting", "variable", "text"), variables);
        assertThat(r.getExtractedData().get("text")).isEqualTo("Hello, World!");
    }

    @Test
    void extract_getsAttribute() {
        HandlerResult r = handler.extract(page, params("selector", "#link-example", "variable", "href", "attribute", "href"), variables);
        assertThat(r.getExtractedData().get("href")).isEqualTo("https://example.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void extractAll_getsMultipleItems() {
        HandlerResult r = handler.extractAll(page, params("selector", ".item", "variable", "items"), variables);
        var items = (java.util.List<String>) r.getExtractedData().get("items");
        assertThat(items).containsExactly("Item A", "Item B", "Item C");
    }

    @Test
    void screenshot_savesFile() {
        HandlerResult r = handler.screenshot(page, params(), variables, 99L, 0);
        assertThat(r.getScreenshotPath()).isNotNull();
        assertThat(Path.of(r.getScreenshotPath())).exists();
    }
}
