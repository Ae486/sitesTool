package com.rpacloud.execution.engine.handlers;

import java.util.Map;

import com.microsoft.playwright.Page;
import com.rpacloud.execution.engine.StepType;
import com.rpacloud.execution.engine.VariableResolver;

public class NavigationHandler implements StepHandler {

    @Override
    public StepType[] supportedTypes() {
        return new StepType[]{StepType.NAVIGATE, StepType.NEW_TAB, StepType.SWITCH_TAB, StepType.CLOSE_TAB,
                StepType.FRAME_SWITCH, StepType.FRAME_MAIN};
    }

    @Override
    public HandlerResult execute(Page page, Map<String, Object> params, Map<String, Object> variables) {
        // Dispatch handled externally by PlaywrightExecutor based on StepType
        throw new UnsupportedOperationException("Use typed execute methods");
    }

    public HandlerResult navigate(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String url = VariableResolver.resolve((String) params.get("url"), variables);
        String waitUntil = params.getOrDefault("wait_until", "load").toString();
        var options = new Page.NavigateOptions();
        switch (waitUntil) {
            case "domcontentloaded" -> options.setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED);
            case "networkidle" -> options.setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE);
            case "commit" -> options.setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT);
            default -> options.setWaitUntil(com.microsoft.playwright.options.WaitUntilState.LOAD);
        }
        page.navigate(url, options);
        return HandlerResult.of("Navigated to " + url);
    }

    public HandlerResult newTab(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String url = VariableResolver.resolve((String) params.get("url"), variables);
        Page newPage = page.context().newPage();
        newPage.navigate(url);
        int tabIndex = page.context().pages().size() - 1;
        String tabVar = params.get("tab_variable") instanceof String s ? s : null;
        if (tabVar != null) {
            return HandlerResult.withData("Opened new tab (" + tabIndex + ") with URL: " + url,
                    Map.of(tabVar, tabIndex));
        }
        return HandlerResult.of("Opened new tab (" + tabIndex + ") with URL: " + url);
    }

    public HandlerResult switchTab(Page page, Map<String, Object> params, Map<String, Object> variables) {
        int tabIndex = ((Number) params.get("index")).intValue();
        var pages = page.context().pages();
        if (tabIndex < 0 || tabIndex >= pages.size()) {
            throw new IllegalArgumentException("Tab index " + tabIndex + " out of range (0-" + (pages.size() - 1) + ")");
        }
        pages.get(tabIndex).bringToFront();
        return HandlerResult.of("Switched to tab " + tabIndex);
    }

    public HandlerResult closeTab(Page page, Map<String, Object> params, Map<String, Object> variables) {
        page.close();
        return HandlerResult.of("Closed current tab");
    }

    public HandlerResult frameSwitch(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        page.frameLocator(selector).locator("body").waitFor();
        return HandlerResult.of("Switched to frame " + selector);
    }

    public HandlerResult frameMain(Page page, Map<String, Object> params, Map<String, Object> variables) {
        return HandlerResult.of("Returned to main frame");
    }
}
