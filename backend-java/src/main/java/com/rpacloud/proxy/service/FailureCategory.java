package com.rpacloud.proxy.service;

import java.util.List;

public enum FailureCategory {
    NONE,
    NETWORK_ERROR,
    TIMEOUT,
    UNRELATED;

    public boolean isProxyFault() {
        return this == NETWORK_ERROR || this == TIMEOUT;
    }

    public static FailureCategory fromErrorTypes(List<String> types) {
        if (types == null || types.isEmpty()) return NONE;
        for (String t : types) {
            if ("NETWORK_ERROR".equals(t) || "NAVIGATION_ERROR".equals(t)) return NETWORK_ERROR;
            if ("TIMEOUT".equals(t) || "PROCESS_TIMEOUT".equals(t)) return TIMEOUT;
        }
        return UNRELATED;
    }
}
