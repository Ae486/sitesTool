package com.rpacloud.integration.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpacloud.integration.BaseIntegrationTest;
import com.rpacloud.llm.service.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisRateLimiterIT extends BaseIntegrationTest {

    @Autowired private RateLimiter rateLimiter;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanUp() {
        var keys = redisTemplate.keys("rpa:ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void allowsWithinLimit() {
        assertThat(rateLimiter.tryAcquire("test-user", 5)).isTrue();
        assertThat(rateLimiter.tryAcquire("test-user", 5)).isTrue();
        assertThat(rateLimiter.tryAcquire("test-user", 5)).isTrue();
    }

    @Test
    void rejectsOverLimit() {
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryAcquire("over-limit", 3)).isTrue();
        }
        assertThat(rateLimiter.tryAcquire("over-limit", 3)).isFalse();
    }

    @Test
    void separateKeysIndependent() {
        for (int i = 0; i < 2; i++) {
            rateLimiter.tryAcquire("user-a", 2);
        }
        assertThat(rateLimiter.tryAcquire("user-a", 2)).isFalse();
        assertThat(rateLimiter.tryAcquire("user-b", 2)).isTrue();
    }

    @Test
    void isRedisImplementation() {
        assertThat(rateLimiter).isInstanceOf(com.rpacloud.llm.service.RedisRateLimiter.class);
    }
}
