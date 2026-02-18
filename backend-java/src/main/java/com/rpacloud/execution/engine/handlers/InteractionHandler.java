package com.rpacloud.execution.engine.handlers;

import java.nio.file.Path;
import java.util.Map;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.rpacloud.execution.engine.StepType;
import com.rpacloud.execution.engine.VariableResolver;

public class InteractionHandler implements StepHandler {

    @Override
    public StepType[] supportedTypes() {
        return new StepType[]{
                StepType.CLICK, StepType.INPUT, StepType.SELECT, StepType.CHECKBOX,
                StepType.SCROLL, StepType.HOVER, StepType.KEYBOARD, StepType.TRY_CLICK,
                StepType.DRAG_DROP, StepType.UPLOAD_FILE
        };
    }

    @Override
    public HandlerResult execute(Page page, Map<String, Object> params, Map<String, Object> variables) {
        throw new UnsupportedOperationException("Use typed execute methods");
    }

    public HandlerResult click(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        double timeout = toDouble(params.getOrDefault("timeout", 5000));
        page.click(selector, new Page.ClickOptions().setTimeout(timeout));
        return HandlerResult.of("Clicked " + selector);
    }

    public HandlerResult input(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        String value = VariableResolver.resolve(String.valueOf(params.get("value")), variables);
        boolean clear = toBoolean(params.getOrDefault("clear", true));
        if (clear) page.fill(selector, "");
        page.fill(selector, value);
        return HandlerResult.of("Input '" + value + "' into " + selector);
    }

    public HandlerResult select(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        String value = VariableResolver.resolve(String.valueOf(params.get("value")), variables);
        page.selectOption(selector, value);
        return HandlerResult.of("Selected '" + value + "' in " + selector);
    }

    public HandlerResult checkbox(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        boolean checked = toBoolean(params.get("checked"));
        if (checked) { page.check(selector); } else { page.uncheck(selector); }
        return HandlerResult.of((checked ? "Checked " : "Unchecked ") + selector);
    }

    public HandlerResult scroll(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = params.get("selector") instanceof String s ? s : null;
        if (selector != null && !selector.isBlank()) {
            page.locator(selector).scrollIntoViewIfNeeded();
            return HandlerResult.of("Scrolled to " + selector);
        }
        int x = toInt(params.getOrDefault("x", 0));
        int y = toInt(params.getOrDefault("y", 0));
        page.evaluate("([x, y]) => window.scrollBy(x, y)", java.util.List.of(x, y));
        return HandlerResult.of("Scrolled by (" + x + ", " + y + ")");
    }

    public HandlerResult hover(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        page.hover(selector);
        return HandlerResult.of("Hovered over " + selector);
    }

    public HandlerResult keyboard(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String key = (String) params.get("key");
        page.keyboard().press(key);
        return HandlerResult.of("Pressed key: " + key);
    }

    public HandlerResult tryClick(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        double timeout = toDouble(params.getOrDefault("timeout", 3000));
        try {
            Locator locator = page.locator(selector);
            locator.waitFor(new Locator.WaitForOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(timeout));
            locator.click();
            return HandlerResult.of("Clicked " + selector);
        } catch (Exception e) {
            return HandlerResult.of("Skipped click: " + selector + " not found");
        }
    }

    public HandlerResult dragDrop(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String source = (String) params.get("source_selector");
        String target = (String) params.get("target_selector");
        page.dragAndDrop(source, target);
        return HandlerResult.of("Dragged " + source + " to " + target);
    }

    public HandlerResult uploadFile(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        String filePath = VariableResolver.resolve((String) params.get("file_path"), variables);
        page.setInputFiles(selector, Path.of(filePath));
        return HandlerResult.of("Uploaded file " + filePath + " via " + selector);
    }

    private static double toDouble(Object val) {
        return val instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(val));
    }

    private static int toInt(Object val) {
        return val instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(val));
    }

    private static boolean toBoolean(Object val) {
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(val));
    }
}
