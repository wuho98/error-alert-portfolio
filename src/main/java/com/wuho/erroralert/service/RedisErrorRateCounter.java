package com.wuho.erroralert.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisErrorRateCounter implements ErrorRateCounter {

    static final long WINDOW_SECONDS = 60L;
    private static final Duration COUNTER_TTL = Duration.ofSeconds(WINDOW_SECONDS);
    private static final String KEY_PREFIX = "error:count";
    private static final DefaultRedisScript<Long> INCREMENT_SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local windowSeconds = tonumber(ARGV[1])
            local windowMillis = windowSeconds * 1000
            local keyPrefix = string.match(KEYS[1], '^(.*):%-?%d+$')
            if keyPrefix == nil then
                return redis.error_reply('Invalid error counter key')
            end

            local redisTime = redis.call('TIME')
            local nowSeconds = tonumber(redisTime[1])
            local nowMillis = (nowSeconds * 1000) + math.floor(tonumber(redisTime[2]) / 1000)
            local windowStart = math.floor(nowSeconds / windowSeconds) * windowSeconds
            local currentKey = keyPrefix .. ':' .. string.format('%.0f', windowStart)
            local previousKey = keyPrefix .. ':' .. string.format('%.0f', windowStart - windowSeconds)
            local cutoffMillis = nowMillis - windowMillis

            local function replaceLegacyCounter(key)
                local keyType = redis.call('TYPE', key)['ok']
                if keyType ~= 'none' and keyType ~= 'zset' then
                    redis.call('DEL', key)
                end
            end

            replaceLegacyCounter(currentKey)
            replaceLegacyCounter(previousKey)
            redis.call('ZREMRANGEBYSCORE', currentKey, '-inf', cutoffMillis)
            redis.call('ZREMRANGEBYSCORE', previousKey, '-inf', cutoffMillis)

            local currentSize = redis.call('ZCARD', currentKey)
            local member = redisTime[1] .. ':' .. redisTime[2] .. ':' .. currentSize
            redis.call('ZADD', currentKey, nowMillis, member)
            redis.call('PEXPIRE', currentKey, windowMillis)

            return redis.call('ZCARD', currentKey) + redis.call('ZCARD', previousKey)
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long counterTtlSeconds;

    @Autowired
    public RedisErrorRateCounter(StringRedisTemplate redisTemplate) {
        this(redisTemplate, COUNTER_TTL);
    }

    RedisErrorRateCounter(StringRedisTemplate redisTemplate, Duration counterTtl) {
        this.redisTemplate = redisTemplate;
        Objects.requireNonNull(counterTtl, "counterTtl must not be null");
        if (counterTtl.isZero() || counterTtl.isNegative() || counterTtl.getNano() != 0) {
            throw new IllegalArgumentException("counterTtl must be a positive whole-second duration");
        }
        this.counterTtlSeconds = counterTtl.toSeconds();
    }

    @Override
    public long increment(long projectId, String errorCode, Instant receivedAt) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");

        long windowStart = windowStartEpochSecond(receivedAt);
        String key = counterKey(projectId, errorCode, windowStart);

        Long count = redisTemplate.execute(
                INCREMENT_SLIDING_WINDOW_SCRIPT,
                List.of(key),
                Long.toString(counterTtlSeconds));

        if (count == null) {
            throw new IllegalStateException("Redis did not return the incremented error count");
        }
        return count;
    }

    static long windowStartEpochSecond(Instant receivedAt) {
        long epochSecond = receivedAt.getEpochSecond();
        return Math.floorDiv(epochSecond, WINDOW_SECONDS) * WINDOW_SECONDS;
    }

    static String counterKey(long projectId, String errorCode, long windowStart) {
        return "%s:%d:%s:%d".formatted(KEY_PREFIX, projectId, errorCode, windowStart);
    }
}
