package com.rpacloud.execution.engine.handlers;

import java.util.Map;

import com.microsoft.playwright.Page;
import com.rpacloud.execution.engine.StepType;
import com.rpacloud.execution.engine.VariableResolver;

public class MiscHandler implements StepHandler {

    @Override
    public StepType[] supportedTypes() {
        return new StepType[]{StepType.SET_VARIABLE, StepType.EVAL_JS, StepType.DIALOG_HANDLE};
    }

    @Override
    public HandlerResult execute(Page page, Map<String, Object> params, Map<String, Object> variables) {
        throw new UnsupportedOperationException("Use typed execute methods");
    }

    public HandlerResult setVariable(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String variable = (String) params.get("variable");
        Object value = VariableResolver.resolveAny(params.get("value"), variables);
        return HandlerResult.withData("Set variable " + variable + " = " + value, Map.of(variable, value));
    }

    public HandlerResult evalJs(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String script = VariableResolver.resolve((String) params.get("script"), variables);
        String variable = params.get("variable") instanceof String s ? s : null;
        Object result = page.evaluate(script);
        if (variable != null) {
            return HandlerResult.withData("Executed JS, result stored in " + variable,
                    Map.of(variable, result != null ? result : ""));
        }
        return HandlerResult.of("Executed JS, result: " + result);
    }

    public HandlerResult dialogHandle(Page page, Map<String, Object> params, Map<String, Object> variables) {
        String action = (String) params.getOrDefault("action", "accept");
        String promptText = params.get("prompt_text") instanceof String s ? s : null;
        page.onDialog(dialog -> {
            if ("dismiss".equals(action)) {
                dialog.dismiss();
            } else {
                dialog.accept(promptText);
            }
        });
        return HandlerResult.of("Dialog handler registered: " + action);
    }
}
