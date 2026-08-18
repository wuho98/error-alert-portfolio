package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AlertCooldownServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AlertCooldownService alertCooldownService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        alertCooldownService = new AlertCooldownService(redisTemplate);
    }

    @Test
    void acquiresCooldownWithExpectedKeyAndConfiguredTtl() {
        String key = "alert:cooldown:7:PAYMENT_PG_TIMEOUT";
        when(valueOperations.setIfAbsent(key, "1", Duration.ofSeconds(300)))
                .thenReturn(true);

        boolean acquired = alertCooldownService.tryAcquire(7L, "PAYMENT_PG_TIMEOUT", 300);

        assertThat(acquired).isTrue();
        verify(valueOperations).setIfAbsent(key, "1", Duration.ofSeconds(300));
    }

    @Test
    void rejectsDuplicateCooldownWhenKeyAlreadyExists() {
        String key = "alert:cooldown:7:PAYMENT_PG_TIMEOUT";
        when(valueOperations.setIfAbsent(key, "1", Duration.ofSeconds(300)))
                .thenReturn(false);

        boolean acquired = alertCooldownService.tryAcquire(7L, "PAYMENT_PG_TIMEOUT", 300);

        assertThat(acquired).isFalse();
    }

    @Test
    void treatsMissingRedisResultAsAcquisitionFailure() {
        String key = "alert:cooldown:7:PAYMENT_PG_TIMEOUT";
        when(valueOperations.setIfAbsent(key, "1", Duration.ofSeconds(300)))
                .thenReturn(null);

        boolean acquired = alertCooldownService.tryAcquire(7L, "PAYMENT_PG_TIMEOUT", 300);

        assertThat(acquired).isFalse();
    }

    @Test
    void buildsDifferentKeysForDifferentProjectsAndErrorCodes() {
        assertThat(AlertCooldownService.cooldownKey(7L, "PAYMENT_PG_TIMEOUT"))
                .isNotEqualTo(AlertCooldownService.cooldownKey(8L, "PAYMENT_PG_TIMEOUT"))
                .isNotEqualTo(AlertCooldownService.cooldownKey(7L, "PAYMENT_DECLINED"));
    }

    @Test
    void rejectsZeroCooldownBeforeCallingRedis() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> alertCooldownService.tryAcquire(7L, "PAYMENT_PG_TIMEOUT", 0))
                .withMessage("cooldownSeconds must be greater than zero");
        verifyNoInteractions(valueOperations);
    }
}
