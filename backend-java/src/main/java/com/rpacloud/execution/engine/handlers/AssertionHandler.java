package com.rpacloud.execution.engine.handlers;

import java.util.Map;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.rpacloud.execution.engine.StepType;
import com.rpacloud.execution.engine.VariableResolver;

public class AssertionHandler implements StepHandler {

    @Override
    public StepType[] supportedTypes() {
        return new StepType[]{StepType.ASSERT_TEXT, StepType.ASSERT_VISIBLE, StepType.IF_EXISTS};
    }

    @Override
    public HandlerResult execute(Page page, Map<String, Object> params, Map<String, Object> variables) {
        throw new UnsupportedOperationException("Use typed execute methods");
    }

    public HandlerResult assertText(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        String expected = VariableResolver.resolve((String) params.get("expected"), variables);
        String actual = page.textContent(selector);
        if (actual == null || !actual.contains(expected)) {
            throw new AssertionError("Assertion failed: element " + selector + " does not contain '" + expected + "'. Actual: '" + actual + "'");
        }
        return HandlerResult.of("Assertion passed: '" + expected + "' found in " + selector);
    }

    public HandlerResult assertVisible(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        double timeout = toDouble(params.getOrDefault("timeout", 5000));
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(timeout));
        return HandlerResult.of("Assertion passed: " + selector + " is visible");
    }

    public HandlerResult ifExists(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        String variable = (String) params.get("variable");
        double timeout = toDouble(params.getOrDefault("timeout", 3000));
        boolean exists;
        try {
            page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.ATTACHED).setTimeout(timeout));
            exists = true;
        } catch (Exception e) {
            exists = false;
        }
        return HandlerResult.withData("Element " + selector + " exists: " + exists, Map.of(variable, exists));
    }

    private static double toDouble(Object val) {
        return val instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(val));
    }
}
