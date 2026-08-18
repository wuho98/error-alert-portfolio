package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisErrorRateCounterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisErrorRateCounter counter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        counter = new RedisErrorRateCounter(redisTemplate);
    }

    @Test
    void incrementsTheSameCounterAndSetsItsTtlAtomically() {
        Instant receivedAt = Instant.parse("2026-08-09T12:34:45Z");
        String key = "error:count:7:E500:1786278840";
        when(redisTemplate.execute(anyRedisScript(), eq(List.of(key)), eq("60")))
                .thenReturn(1L, 2L);

        long firstCount = counter.increment(7L, "E500", receivedAt);
        long secondCount = counter.increment(7L, "E500", receivedAt);

        assertThat(firstCount).isEqualTo(1L);
        assertThat(secondCount).isEqualTo(2L);
        verify(redisTemplate, times(2))
                .execute(anyRedisScript(), eq(List.of(key)), eq("60"));
    }

    @Test
    void rejectsCounterTtlThatIsNotPositiveWholeSeconds() {
        assertThatThrownBy(() -> new RedisErrorRateCounter(redisTemplate, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("counterTtl must be a positive whole-second duration");
        assertThatThrownBy(() -> new RedisErrorRateCounter(redisTemplate, Duration.ofMillis(1_500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("counterTtl must be a positive whole-second duration");
    }

    @Test
    void usesDifferentKeysForDifferentProjectsAndErrorCodes() {
        long windowStart = 1_786_278_840L;

        assertThat(RedisErrorRateCounter.counterKey(7L, "E500", windowStart))
                .isNotEqualTo(RedisErrorRateCounter.counterKey(8L, "E500", windowStart))
                .isNotEqualTo(RedisErrorRateCounter.counterKey(7L, "E501", windowStart));
    }

    @Test
    void floorsReceivedAtToTheStartOfItsSixtySecondWindow() {
        Instant receivedAt = Instant.parse("2026-08-09T12:34:45Z");

        long windowStart = RedisErrorRateCounter.windowStartEpochSecond(receivedAt);

        assertThat(Instant.ofEpochSecond(windowStart))
                .isEqualTo(Instant.parse("2026-08-09T12:34:00Z"));
    }

    private static RedisScript<Long> anyRedisScript() {
        return any();
    }
}
