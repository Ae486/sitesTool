package com.rpacloud.execution.engine.handlers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.rpacloud.execution.engine.StepType;

public class ExtractHandler implements StepHandler {

    private final Path screenshotDir;

    public ExtractHandler(Path screenshotDir) {
        this.screenshotDir = screenshotDir;
    }

    @Override
    public StepType[] supportedTypes() {
        return new StepType[]{StepType.EXTRACT, StepType.EXTRACT_ALL, StepType.SCREENSHOT};
    }

    @Override
    public HandlerResult execute(Page page, Map<String, Object> params, Map<String, Object> variables) {
        throw new UnsupportedOperationException("Use typed execute methods");
    }

    public HandlerResult extract(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        String variable = (String) params.get("variable");
        String attribute = params.get("attribute") instanceof String s ? s : null;
        String value;
        if (attribute != null && !attribute.isBlank()) {
            value = page.getAttribute(selector, attribute);
        } else {
            value = page.textContent(selector);
        }
        return HandlerResult.withData("Extracted '" + value + "' from " + selector, Map.of(variable, value != null ? value : ""));
    }

    public HandlerResult extractAll(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        String variable = (String) params.get("variable");
        String attribute = params.get("attribute") instanceof String s ? s : null;
        Locator elements = page.locator(selector);
        int count = elements.count();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Locator el = elements.nth(i);
            String val = (attribute != null && !attribute.isBlank())
                    ? el.getAttribute(attribute)
                    : el.textContent();
            if (val != null && !val.isBlank()) values.add(val.strip());
        }
        return HandlerResult.withData("Extracted " + values.size() + " items from " + selector, Map.of(variable, values));
    }

    public HandlerResult screenshot(Page page, Map<String, Object> params, Map<String, Object> variables,
                                     long flowId, int index) {
        String filename = params.get("path") instanceof String s ? s : "flow_" + flowId + "_step_" + index + ".png";
        boolean fullPage = params.get("full_page") instanceof Boolean b && b;
        Path path = screenshotDir.resolve(filename);
        page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
        return HandlerResult.builder()
                .message("Screenshot saved to " + filename)
                .screenshotPath(path.toString())
                .build();
    }
}
