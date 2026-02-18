package com.rpacloud.llm.service;

public interface RateLimiter {

    boolean tryAcquire(String key, int maxRpm);
}
