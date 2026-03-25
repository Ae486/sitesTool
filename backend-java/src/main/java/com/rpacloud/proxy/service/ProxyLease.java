package com.rpacloud.proxy.service;

import lombok.Getter;
import lombok.Setter;

@Getter
public class ProxyLease {
    private final Long proxyId;
    private final String url;
    @Setter private double score;
    @Setter private long checkoutAt;
    @Setter private String executionId;

    public ProxyLease(Long proxyId, String url, double score) {
        this.proxyId = proxyId;
        this.url = url;
        this.score = score;
    }
}
