package com.rpacloud.llm.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Profile("enterprise")
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;

    // Sliding window counter: INCR + EXPIRE in a single Lua script
    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local max = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current = redis.call('INCR', key)
            if current == 1 then
                redis.call('EXPIRE', key, window)
            end
            if current > max then
                return 0
            end
            return 1
            """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    @Override
    public boolean tryAcquire(String key, int maxRpm) {
        String redisKey = "rpa:ratelimit:" + key;
        Long result = redisTemplate.execute(SCRIPT, List.of(redisKey), String.valueOf(maxRpm), "60");
        return result != null && result == 1L;
    }
}
