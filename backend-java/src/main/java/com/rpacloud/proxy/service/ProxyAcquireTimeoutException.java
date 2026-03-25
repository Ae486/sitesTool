package com.rpacloud.proxy.service;

public class ProxyAcquireTimeoutException extends RuntimeException {
    public ProxyAcquireTimeoutException() {
        super("No proxy available within timeout");
    }

    public ProxyAcquireTimeoutException(String message) {
        super(message);
    }
}
