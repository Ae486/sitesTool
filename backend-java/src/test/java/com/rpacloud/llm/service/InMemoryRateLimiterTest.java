package com.rpacloud.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    @Test
    void allowsRequestsWithinLimit() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("user:1", 5)).isTrue();
        }
    }

    @Test
    void rejectsRequestsOverLimit() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();

        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("user:1", 3);
        }
        assertThat(limiter.tryAcquire("user:1", 3)).isFalse();
    }

    @Test
    void differentKeysAreIndependent() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();

        for (int i = 0; i < 2; i++) {
            limiter.tryAcquire("user:1", 2);
        }
        assertThat(limiter.tryAcquire("user:1", 2)).isFalse();
        assertThat(limiter.tryAcquire("user:2", 2)).isTrue();
    }
}
