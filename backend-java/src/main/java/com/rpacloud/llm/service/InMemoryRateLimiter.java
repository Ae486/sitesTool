package com.rpacloud.llm.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!enterprise")
public class InMemoryRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int maxRpm) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMs.get() >= 60_000L) {
                return new Window(now);
            }
            return existing;
        });
        return window.count.incrementAndGet() <= maxRpm;
    }

    private static class Window {
        final AtomicLong startMs;
        final AtomicInteger count;

        Window(long startMs) {
            this.startMs = new AtomicLong(startMs);
            this.count = new AtomicInteger(0);
        }
    }
}
