package com.rpacloud.execution.engine.handlers;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.rpacloud.execution.engine.StepType;

public class WaitHandler implements StepHandler {

    @Override
    public StepType[] supportedTypes() {
        return new StepType[]{StepType.WAIT_FOR, StepType.WAIT_TIME, StepType.RANDOM_DELAY};
    }

    @Override
    public HandlerResult execute(Page page, Map<String, Object> params, Map<String, Object> variables) {
        throw new UnsupportedOperationException("Use typed execute methods");
    }

    public HandlerResult waitFor(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String selector = (String) params.get("selector");
        double timeout = toDouble(params.getOrDefault("timeout", 10000));
        String stateStr = params.getOrDefault("state", "visible").toString();
        WaitForSelectorState state = switch (stateStr) {
            case "attached" -> WaitForSelectorState.ATTACHED;
            case "detached" -> WaitForSelectorState.DETACHED;
            case "hidden" -> WaitForSelectorState.HIDDEN;
            default -> WaitForSelectorState.VISIBLE;
        };
        page.waitForSelector(selector, new Page.WaitForSelectorOptions().setState(state).setTimeout(timeout));
        return HandlerResult.of("Waited for " + selector);
    }

    public HandlerResult waitTime(Page page, Map<String, Object> params, Map<String, Object> variables)
            throws InterruptedException {
        int duration = toInt(params.get("duration"));
        Thread.sleep(duration);
        return HandlerResult.of("Waited " + duration + "ms");
    }

    public HandlerResult randomDelay(Page page, Map<String, Object> params, Map<String, Object> variables)
            throws InterruptedException {
        int min = toInt(params.get("min"));
        int max = toInt(params.get("max"));
        int delay = ThreadLocalRandom.current().nextInt(min, max + 1);
        Thread.sleep(delay);
        return HandlerResult.of("Random delay: " + delay + "ms (range: " + min + "-" + max + ")");
    }

    private static double toDouble(Object val) {
        return val instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(val));
    }

    private static int toInt(Object val) {
        return val instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(val));
    }
}
