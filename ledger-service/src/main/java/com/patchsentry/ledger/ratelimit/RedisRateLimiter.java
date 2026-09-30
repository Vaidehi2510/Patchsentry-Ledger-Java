package com.patchsentry.ledger.ratelimit;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Fixed-window limiter shared by every replica through Redis: INCR is atomic, so
 * the count is correct no matter which pod serves the request.
 *
 * <p>Fails open: if Redis is unreachable, uploads continue and a warning is
 * logged. Losing QA results is worse than briefly losing the limit.
 */
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    static final Duration KEY_TTL = Duration.ofSeconds(120);

    private final StringRedisTemplate redis;
    private final int limitPerMinute;
    private final Clock clock;

    public RedisRateLimiter(StringRedisTemplate redis, int limitPerMinute, Clock clock) {
        this.redis = redis;
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key) {
        long window = clock.instant().getEpochSecond() / 60;
        String redisKey = "ledger:rl:" + key + ":" + window;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redis.expire(redisKey, KEY_TTL);
            }
            return count == null || count <= limitPerMinute;
        } catch (DataAccessException e) {
            log.warn("Rate limiter unavailable; allowing request: {}", e.getMessage());
            return true;
        }
    }
}
