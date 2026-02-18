package com.rpacloud.execution.engine;

import java.util.Map;

/**
 * Resolves {{var}} and ${var} placeholders.
 * Port of Python variable_resolver.py.
 */
public final class VariableResolver {

    private VariableResolver() {}

    public static String resolve(String value, Map<String, Object> variables) {
        if (value == null || variables == null || variables.isEmpty()) return value;
        String result = value;
        for (var entry : variables.entrySet()) {
            String strVal = String.valueOf(entry.getValue());
            result = result.replace("{{" + entry.getKey() + "}}", strVal);
            result = result.replace("${" + entry.getKey() + "}", strVal);
        }
        return result;
    }

    public static Object resolveAny(Object value, Map<String, Object> variables) {
        if (value instanceof String s) return resolve(s, variables);
        return value;
    }
}
