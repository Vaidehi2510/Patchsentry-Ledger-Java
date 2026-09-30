package com.patchsentry.ledger.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    // 600 seconds after the epoch is minute window 10.
    private static final String KEY = "ledger:rl:token-1:10";

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private RedisRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RedisRateLimiter(redis, 3, Clock.fixed(Instant.ofEpochSecond(600), ZoneOffset.UTC));
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void firstRequestInAWindowSetsTheExpiry() {
        when(values.increment(KEY)).thenReturn(1L);

        assertThat(limiter.tryAcquire("token-1")).isTrue();
        verify(redis).expire(KEY, Duration.ofSeconds(120));
    }

    @Test
    void requestsAtTheLimitPassAndAboveItAreRejected() {
        when(values.increment(KEY)).thenReturn(3L, 4L);

        assertThat(limiter.tryAcquire("token-1")).isTrue();
        assertThat(limiter.tryAcquire("token-1")).isFalse();
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void failsOpenWhenRedisIsUnavailable() {
        when(values.increment(KEY)).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThat(limiter.tryAcquire("token-1")).isTrue();
    }
}
