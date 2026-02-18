package com.rpacloud.execution.engine;

public enum ErrorType {
    TIMEOUT("TIMEOUT"),
    SELECTOR_NOT_FOUND("SELECTOR_NOT_FOUND"),
    NAVIGATION_ERROR("NAVIGATION_ERROR"),
    NETWORK_ERROR("NETWORK_ERROR"),
    JS_ERROR("JS_ERROR"),
    ASSERTION_ERROR("ASSERTION_ERROR"),
    BROWSER_CRASH("BROWSER_CRASH"),
    MANUAL_STOP("MANUAL_STOP"),
    PROCESS_TIMEOUT("PROCESS_TIMEOUT"),
    UNKNOWN("UNKNOWN"),
    ;

    private final String value;

    ErrorType(String value) { this.value = value; }

    public String value() { return value; }

    public static ErrorType classify(Throwable error) {
        if (error == null) return UNKNOWN;
        String msg = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
        if (msg.contains("timeout")) return TIMEOUT;
        if (msg.contains("selector") || msg.contains("not found") || msg.contains("no element")) return SELECTOR_NOT_FOUND;
        if (msg.contains("navigation") || msg.contains("net::err")) return NAVIGATION_ERROR;
        if (msg.contains("network")) return NETWORK_ERROR;
        if (msg.contains("javascript") || msg.contains("evaluation")) return JS_ERROR;
        if (msg.contains("assert")) return ASSERTION_ERROR;
        if (msg.contains("browser") && msg.contains("crash")) return BROWSER_CRASH;
        return UNKNOWN;
    }

    public static ErrorType classify(String errorText) {
        if (errorText == null || errorText.isBlank()) return UNKNOWN;
        String lower = errorText.toLowerCase();
        if (lower.contains("timeout")) return TIMEOUT;
        if (lower.contains("selector") || lower.contains("not found")) return SELECTOR_NOT_FOUND;
        if (lower.contains("navigation") || lower.contains("net::err")) return NAVIGATION_ERROR;
        if (lower.contains("network")) return NETWORK_ERROR;
        if (lower.contains("assert")) return ASSERTION_ERROR;
        if (lower.contains("browser") && lower.contains("crash")) return BROWSER_CRASH;
        return UNKNOWN;
    }
}
