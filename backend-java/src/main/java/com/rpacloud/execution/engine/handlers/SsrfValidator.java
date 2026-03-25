package com.rpacloud.execution.engine.handlers;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

public final class SsrfValidator {

    private SsrfValidator() {}

    public static void validate(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Invalid URL: " + url);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
            throw new SecurityException("Only http/https schemes allowed, got: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("Missing host in URL: " + url);
        }

        if ("localhost".equalsIgnoreCase(host)) {
            throw new SecurityException("Blocked SSRF: localhost");
        }

        // DNS resolve and check IP
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SecurityException("Cannot resolve host: " + host);
        }

        for (InetAddress addr : addresses) {
            if (isBlocked(addr)) {
                throw new SecurityException("Blocked SSRF: resolved IP " + addr.getHostAddress() + " is private/reserved");
            }
        }
    }

    static boolean isBlocked(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress()
                || isCarrierGradeNat(addr);
    }

    private static boolean isCarrierGradeNat(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (bytes.length != 4) return false;
        // 100.64.0.0/10
        return (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xFF) >= 64 && (bytes[1] & 0xFF) <= 127;
    }
}
