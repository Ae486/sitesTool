package com.rpacloud.execution.engine.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.rpacloud.execution.engine.StepType;
import com.rpacloud.execution.engine.VariableResolver;

public class NetworkHandler implements StepHandler {

    @Override
    public StepType[] supportedTypes() {
        return new StepType[]{StepType.CAPTURE_NETWORK, StepType.WAIT_FOR_NETWORK};
    }

    @Override
    public HandlerResult execute(Page page, Map<String, Object> params, Map<String, Object> variables) {
        throw new UnsupportedOperationException("Use typed execute methods");
    }

    public HandlerResult captureNetwork(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String urlPattern = VariableResolver.resolve((String) params.get("url_pattern"), variables);
        String saveTo = (String) params.get("save_to");
        Pattern pattern = Pattern.compile(urlPattern);

        page.onResponse(response -> {
            if (pattern.matcher(response.url()).find()) {
                try {
                    variables.put(saveTo + "_url", response.url());
                    variables.put(saveTo + "_status", response.status());
                    variables.put(saveTo, response.text());
                } catch (Exception ignored) {}
            }
        });

        Map<String, Object> data = new HashMap<>();
        if (saveTo != null) data.put(saveTo, "");
        return HandlerResult.withData("Network capture registered for pattern: " + urlPattern, data);
    }

    public HandlerResult waitForNetwork(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String urlPattern = VariableResolver.resolve((String) params.get("url_pattern"), variables);
        String saveTo = params.get("save_to") instanceof String s ? s : null;
        double timeout = params.get("timeout") instanceof Number n ? n.doubleValue() : 30000;

        page.setDefaultTimeout(timeout);
        Response response = page.waitForResponse(
                r -> r.url().contains(urlPattern),
                () -> { /* no-op: response arrives from page activity */ });

        Map<String, Object> data = new HashMap<>();
        if (saveTo != null) {
            data.put(saveTo + "_url", response.url());
            data.put(saveTo + "_status", response.status());
            try { data.put(saveTo, response.text()); } catch (Exception e) { data.put(saveTo, ""); }
        }
        return HandlerResult.withData("Captured response from " + response.url() + " (status=" + response.status() + ")", data);
    }
}
