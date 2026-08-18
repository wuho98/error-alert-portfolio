package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 분리된 INCR/EXPIRE 사이의 실패로 TTL 없는 키가 남는 기준 사례와,
 * Lua 적용 후 카운트 증가·최초 TTL 설정이 원자적으로 실행되는 결과를 실제 Redis에서 검증한다.
 */
@Testcontainers
class RedisCounterLuaAtomicityPerformanceTest {

    private static final int CONCURRENT_EVENTS = 1_000;
    private static final int CONCURRENCY = 50;
    private static final Duration TEST_TTL = Duration.ofSeconds(2);
    private static final Duration CONCURRENCY_TEST_TTL = Duration.ofSeconds(30);
    private static final Duration EXPIRY_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final long EXPIRY_POLL_INTERVAL_MILLIS = 50L;
    private static final String ERROR_CODE = "PAYMENT.PG_TIMEOUT";
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-15T12:34:56Z");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(2));

    private static LettuceConnectionFactory redisConnectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void setUp() {
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        redisConnectionFactory = new LettuceConnectionFactory(redisConfiguration);
        redisConnectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(redisConnectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void tearDown() {
        if (redisConnectionFactory != null) {
            redisConnectionFactory.destroy();
        }
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void luaPreventsTheMissingTtlStateAndKeepsConcurrentCountsCorrect() throws Exception {
        String baselineKey = counterKey(67L);
        redisTemplate.delete(baselineKey);

        assertThatThrownBy(() -> incrementThenInjectFailureBeforeExpire(baselineKey))
                .isInstanceOf(InjectedClientFailure.class)
                .hasMessage("injected failure between INCR and EXPIRE");
        String baselineValue = redisTemplate.opsForValue().get(baselineKey);
        long baselineTtlSeconds = redisTemplate.getExpire(baselineKey, TimeUnit.SECONDS);
        assertThat(baselineValue).isEqualTo("1");
        assertThat(baselineTtlSeconds).isEqualTo(-1L);

        RedisErrorRateCounter counter = new RedisErrorRateCounter(redisTemplate, TEST_TTL);
        long firstCount = counter.increment(68L, ERROR_CODE, RECEIVED_AT);
        long firstTtlSeconds = redisTemplate.getExpire(counterKey(68L), TimeUnit.SECONDS);
        assertThat(firstCount).isEqualTo(1L);
        assertThat(firstTtlSeconds).isBetween(1L, TEST_TTL.toSeconds());

        Thread.sleep(1_100L);
        long secondCount = counter.increment(68L, ERROR_CODE, RECEIVED_AT);
        long secondTtlSeconds = redisTemplate.getExpire(counterKey(68L), TimeUnit.SECONDS);
        assertThat(secondCount).isEqualTo(2L);
        assertThat(secondTtlSeconds).isBetween(0L, 1L);

        boolean expiredAfterFirstTtl = awaitCondition(
                () -> Boolean.FALSE.equals(redisTemplate.hasKey(counterKey(68L))),
                EXPIRY_WAIT_TIMEOUT);
        assertThat(expiredAfterFirstTtl).isTrue();

        RedisErrorRateCounter concurrentCounter = new RedisErrorRateCounter(redisTemplate, CONCURRENCY_TEST_TTL);
        long finalConcurrentCount = runConcurrentIncrements(concurrentCounter, 69L);
        long concurrentTtlSeconds = redisTemplate.getExpire(counterKey(69L), TimeUnit.SECONDS);
        assertThat(finalConcurrentCount).isEqualTo(CONCURRENT_EVENTS);
        assertThat(concurrentTtlSeconds).isBetween(1L, CONCURRENCY_TEST_TTL.toSeconds());

        writeReport(new EvidenceResult(
                baselineValue,
                baselineTtlSeconds,
                firstCount,
                firstTtlSeconds,
                secondCount,
                secondTtlSeconds,
                expiredAfterFirstTtl,
                finalConcurrentCount,
                concurrentTtlSeconds));
    }

    private static void incrementThenInjectFailureBeforeExpire(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        assertThat(count).isEqualTo(1L);
        throw new InjectedClientFailure("injected failure between INCR and EXPIRE");
    }

    private static long runConcurrentIncrements(RedisErrorRateCounter counter, long projectId) throws Exception {
        redisTemplate.delete(counterKey(projectId));
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>(CONCURRENT_EVENTS);
        try {
            for (int event = 0; event < CONCURRENT_EVENTS; event++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return counter.increment(projectId, ERROR_CODE, RECEIVED_AT);
                }));
            }
            startGate.countDown();

            List<Long> counts = new ArrayList<>(CONCURRENT_EVENTS);
            for (Future<Long> future : futures) {
                counts.add(future.get());
            }
            assertThat(counts).containsExactlyInAnyOrderElementsOf(expectedCounts());
            return Long.parseLong(redisTemplate.opsForValue().get(counterKey(projectId)));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static List<Long> expectedCounts() {
        List<Long> expected = new ArrayList<>(CONCURRENT_EVENTS);
        for (long count = 1; count <= CONCURRENT_EVENTS; count++) {
            expected.add(count);
        }
        return expected;
    }

    private static boolean awaitCondition(CheckedBooleanSupplier condition, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(EXPIRY_POLL_INTERVAL_MILLIS);
        }
        return condition.getAsBoolean();
    }

    private static String counterKey(long projectId) {
        return RedisErrorRateCounter.counterKey(
                projectId,
                ERROR_CODE,
                RedisErrorRateCounter.windowStartEpochSecond(RECEIVED_AT));
    }

    private static void writeReport(EvidenceResult result) throws IOException {
        Path reportDir = Path.of(System.getProperty("redis.counter.atomicity.report.dir", "build/reports/redis-counter-atomicity"));
        Files.createDirectories(reportDir);
        Files.writeString(
                reportDir.resolve("redis-counter-lua-atomicity-evidence.md"),
                renderReport(result),
                StandardCharsets.UTF_8);
    }

    private static String renderReport(EvidenceResult result) {
        return """
                # Redis counter atomicity Redis Counter Lua Atomicity Evidence

                - Redis image: `redis:7-alpine`
                - Baseline fault injection: client processing stops after `INCR` and before `EXPIRE`
                - Lua result: `INCR` and first-count `EXPIRE` execute in one server-side script
                - Concurrent input: `%d` events with concurrency `%d`

                ## Result

                | Check | Result |
                | --- | --- |
                | Baseline value after injected failure | `%s` |
                | Baseline TTL after injected failure | `%d` (`-1` means no expiry) |
                | Lua first count / TTL | `%d` / `%d seconds` |
                | Lua second count / remaining TTL | `%d` / `%d seconds` |
                | Key expired from the first TTL without refresh | `%s` |
                | Concurrent final count | `%d` |
                | Concurrent key TTL | `%d seconds` |

                ## Scope

                - This is a deterministic client-failure injection, not a claim that a production Redis outage occurred.
                - It verifies the missing-TTL state of the former two-command flow and the atomic server-side execution of the Lua flow.
                - It does not verify RDB/Redis cross-system consistency, exact-once processing, or an exact sliding window.
                """.formatted(
                CONCURRENT_EVENTS,
                CONCURRENCY,
                result.baselineValue(),
                result.baselineTtlSeconds(),
                result.firstCount(),
                result.firstTtlSeconds(),
                result.secondCount(),
                result.secondTtlSeconds(),
                result.expiredAfterFirstTtl(),
                result.finalConcurrentCount(),
                result.concurrentTtlSeconds());
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {

        boolean getAsBoolean() throws Exception;
    }

    private record EvidenceResult(
            String baselineValue,
            long baselineTtlSeconds,
            long firstCount,
            long firstTtlSeconds,
            long secondCount,
            long secondTtlSeconds,
            boolean expiredAfterFirstTtl,
            long finalConcurrentCount,
            long concurrentTtlSeconds
    ) {
    }

    private static class InjectedClientFailure extends RuntimeException {

        InjectedClientFailure(String message) {
            super(message);
        }
    }
}
