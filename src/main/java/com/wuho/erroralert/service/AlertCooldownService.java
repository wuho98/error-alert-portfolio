package com.wuho.erroralert.service;

import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlertCooldownService {

    private static final String KEY_PREFIX = "alert:cooldown";
    private static final String LOCK_VALUE = "1";

    private final StringRedisTemplate redisTemplate;

    public AlertCooldownService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(long projectId, String errorCode, int cooldownSeconds) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (cooldownSeconds <= 0) {
            throw new IllegalArgumentException("cooldownSeconds must be greater than zero");
        }

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                cooldownKey(projectId, errorCode),
                LOCK_VALUE,
                Duration.ofSeconds(cooldownSeconds)
        );
        return Boolean.TRUE.equals(acquired);
    }

    static String cooldownKey(long projectId, String errorCode) {
        return "%s:%d:%s".formatted(KEY_PREFIX, projectId, errorCode);
    }
}
