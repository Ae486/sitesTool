package com.rpacloud.execution.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class DistributedLockTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    private DistributedLock lock;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lock = new DistributedLock(redis);
    }

    @AfterEach
    void tearDown() {
        lock.shutdown();
    }

    @Test
    void tryLockSucceedsWhenKeyAbsent() {
        when(valueOps.setIfAbsent("test:key", "val1", Duration.ofSeconds(30))).thenReturn(true);
        assertThat(lock.tryLock("test:key", "val1", 30)).isTrue();
    }

    @Test
    void tryLockFailsWhenKeyExists() {
        when(valueOps.setIfAbsent("test:key", "val1", Duration.ofSeconds(30))).thenReturn(false);
        assertThat(lock.tryLock("test:key", "val1", 30)).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void unlockReleasesLock() {
        when(redis.execute(any(RedisScript.class), eq(List.of("test:key")), eq("val1")))
                .thenReturn(1L);
        assertThat(lock.unlock("test:key", "val1")).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void unlockFailsIfValueMismatch() {
        when(redis.execute(any(RedisScript.class), eq(List.of("test:key")), eq("wrong")))
                .thenReturn(0L);
        assertThat(lock.unlock("test:key", "wrong")).isFalse();
    }
}
