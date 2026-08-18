package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 Redis와 MySQL에서 SET NX 사용 전후의 동시 요청을 비교해,
 * 최종 AlertLog 1건을 유지하면서 중복 DB 쓰기를 줄이는지 검증한다.
 */
@Testcontainers
class AlertCooldownConcurrencyPerformanceTest {

    // 기본 test/build에는 포함하지 않고 alertCooldownConcurrencyPerformanceTest task로만 실행한다.
    private static final int INCOMING_EVENTS = 1_000;
    private static final int THRESHOLD = 10;
    // observedCount >= THRESHOLD인 요청 수: 1,000 - (10 - 1) = 991
    private static final int AT_OR_ABOVE_THRESHOLD_REQUESTS = INCOMING_EVENTS - (THRESHOLD - 1);
    private static final int CONCURRENCY = 50;
    private static final int COOLDOWN_SECONDS = 30;
    private static final int TTL_VERIFICATION_SECONDS = 2;
    private static final Duration TTL_EXPIRY_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final long TTL_POLL_INTERVAL_MILLIS = 50L;
    // 첫 실행의 클래스 로딩과 Redis·MySQL 연결 준비 영향을 줄이기 위한 최소 워밍업이다.
    private static final int WARMUP_RUNS = 1;
    private static final int MEASUREMENT_RUNS = 5;
    private static final String ERROR_CODE = "PAYMENT.PG_TIMEOUT";
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-13T12:34:56Z");
    private static final Instant WINDOW_STARTED_AT = Instant.parse("2026-08-13T12:34:00Z");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("error_alert")
            .withUsername("error_alert")
            .withPassword("replace_with_test_mysql_password")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(2));

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static LettuceConnectionFactory redisConnectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisErrorRateCounter errorRateCounter;
    private static AlertCooldownService cooldownService;
    private static long projectId;

    @BeforeAll
    static void setUp() {
        dataSource = dataSource();
        jdbcTemplate = new JdbcTemplate(dataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        seedProject();

        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        redisConnectionFactory = new LettuceConnectionFactory(redisConfiguration);
        redisConnectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(redisConnectionFactory);
        redisTemplate.afterPropertiesSet();
        errorRateCounter = new RedisErrorRateCounter(redisTemplate);
        cooldownService = new AlertCooldownService(redisTemplate);
    }

    @AfterAll
    static void tearDown() {
        if (redisConnectionFactory != null) {
            redisConnectionFactory.destroy();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void cooldownSuppressesDuplicateAlertWritesUnderConcurrentLoad() throws Exception {
        for (int run = 0; run < WARMUP_RUNS; run++) {
            runScenario(false);
            runScenario(true);
        }

        List<RunResult> baselineRuns = new ArrayList<>();
        List<RunResult> cooldownRuns = new ArrayList<>();
        for (int run = 0; run < MEASUREMENT_RUNS; run++) {
            baselineRuns.add(runScenario(false));
            cooldownRuns.add(runScenario(true));
        }

        assertScenarioResults(baselineRuns, false);
        assertScenarioResults(cooldownRuns, true);
        TtlResult ttlResult = verifyCooldownTtlAndReacquisition();
        writeReport(baselineRuns, cooldownRuns, ttlResult);
    }

    private static HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(MYSQL.getDriverClassName());
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setMaximumPoolSize(CONCURRENCY);
        config.setMinimumIdle(CONCURRENCY);
        config.setPoolName("alert-cooldown-mysql-pool");
        return new HikariDataSource(config);
    }

    private static void seedProject() {
        jdbcTemplate.update("""
                insert into project (name, created_at, updated_at)
                values ('alert-cooldown-cooldown-evidence', '2026-08-13 00:00:00', '2026-08-13 00:00:00')
                """);
        projectId = jdbcTemplate.queryForObject(
                "select id from project where name = 'alert-cooldown-cooldown-evidence'", Long.class);
    }

    private RunResult runScenario(boolean cooldownEnabled) throws Exception {
        resetScenarioState();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<TaskResult>> futures = new ArrayList<>(INCOMING_EVENTS);
        for (int event = 0; event < INCOMING_EVENTS; event++) {
            futures.add(executor.submit(() -> processEvent(startGate, cooldownEnabled)));
        }

        long startedAt = System.nanoTime();
        startGate.countDown();

        List<TaskResult> taskResults = new ArrayList<>(INCOMING_EVENTS);
        for (Future<TaskResult> future : futures) {
            taskResults.add(future.get());
        }
        long elapsedNanos = System.nanoTime() - startedAt;

        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        long belowThreshold = taskResults.stream().filter(result -> result.outcome() == Outcome.BELOW_THRESHOLD).count();
        long inserted = taskResults.stream().filter(result -> result.outcome() == Outcome.INSERTED).count();
        long duplicate = taskResults.stream().filter(result -> result.outcome() == Outcome.DUPLICATE_CONSTRAINT).count();
        long lockFailure = taskResults.stream().filter(result -> result.outcome() == Outcome.DB_LOCK_FAILURE).count();
        long suppressed = taskResults.stream().filter(result -> result.outcome() == Outcome.COOLDOWN_SUPPRESSED).count();
        long databaseAttempts = inserted + duplicate + lockFailure;
        long finalRedisCount = Long.parseLong(redisTemplate.opsForValue().get(counterKey()));
        long finalAlertLogs = jdbcTemplate.queryForObject("select count(*) from alert_log", Long.class);
        long counterTtlSeconds = redisTemplate.getExpire(counterKey(), TimeUnit.SECONDS);
        long cooldownTtlSeconds = cooldownEnabled
                ? redisTemplate.getExpire(cooldownKey(), TimeUnit.SECONDS)
                : -2L;

        List<Double> taskLatenciesMillis = taskResults.stream()
                .map(result -> result.elapsedNanos() / 1_000_000.0)
                .sorted()
                .toList();

        return new RunResult(
                cooldownEnabled,
                elapsedNanos / 1_000_000.0,
                percentile(taskLatenciesMillis, 50),
                percentile(taskLatenciesMillis, 95),
                belowThreshold,
                databaseAttempts,
                inserted,
                duplicate,
                lockFailure,
                suppressed,
                finalRedisCount,
                finalAlertLogs,
                counterTtlSeconds,
                cooldownTtlSeconds
        );
    }

    private TaskResult processEvent(CountDownLatch startGate, boolean cooldownEnabled) throws InterruptedException {
        startGate.await();
        long startedAt = System.nanoTime();
        long observedCount = errorRateCounter.increment(projectId, ERROR_CODE, RECEIVED_AT);

        if (observedCount < THRESHOLD) {
            return new TaskResult(Outcome.BELOW_THRESHOLD, System.nanoTime() - startedAt);
        }
        if (cooldownEnabled && !cooldownService.tryAcquire(projectId, ERROR_CODE, COOLDOWN_SECONDS)) {
            return new TaskResult(Outcome.COOLDOWN_SUPPRESSED, System.nanoTime() - startedAt);
        }

        Outcome outcome = insertAlertLog(observedCount);
        return new TaskResult(outcome, System.nanoTime() - startedAt);
    }

    private Outcome insertAlertLog(long observedCount) {
        try {
            jdbcTemplate.update("""
                    insert into alert_log (
                        project_id, error_code, window_started_at, observed_count, threshold,
                        status, retry_count, sent_at, created_at
                    ) values (?, ?, ?, ?, ?, 'SKIPPED', 0, null, ?)
                    """,
                    projectId,
                    ERROR_CODE,
                    Timestamp.from(WINDOW_STARTED_AT),
                    Math.toIntExact(observedCount),
                    THRESHOLD,
                    Timestamp.from(WINDOW_STARTED_AT)
            );
            return Outcome.INSERTED;
        } catch (DataIntegrityViolationException exception) {
            return Outcome.DUPLICATE_CONSTRAINT;
        } catch (CannotAcquireLockException exception) {
            return Outcome.DB_LOCK_FAILURE;
        }
    }

    private void resetScenarioState() {
        jdbcTemplate.update("delete from alert_log");
        redisTemplate.delete(List.of(counterKey(), cooldownKey()));
    }

    private String counterKey() {
        return RedisErrorRateCounter.counterKey(
                projectId,
                ERROR_CODE,
                RedisErrorRateCounter.windowStartEpochSecond(RECEIVED_AT)
        );
    }

    private String cooldownKey() {
        return AlertCooldownService.cooldownKey(projectId, ERROR_CODE);
    }

    private void assertScenarioResults(List<RunResult> runs, boolean cooldownEnabled) {
        assertThat(runs).hasSize(MEASUREMENT_RUNS);
        for (RunResult run : runs) {
            assertThat(run.cooldownEnabled()).isEqualTo(cooldownEnabled);
            assertThat(run.belowThreshold()).isEqualTo(THRESHOLD - 1L);
            assertThat(run.finalRedisCount()).isEqualTo(INCOMING_EVENTS);
            assertThat(run.finalAlertLogs()).isOne();
            assertThat(run.inserted()).isOne();
            assertThat(run.counterTtlSeconds()).isBetween(1L, 60L);
            if (cooldownEnabled) {
                assertThat(run.databaseAttempts()).isOne();
                assertThat(run.duplicateConstraint()).isZero();
                assertThat(run.databaseLockFailure()).isZero();
                assertThat(run.cooldownSuppressed()).isEqualTo(AT_OR_ABOVE_THRESHOLD_REQUESTS - 1L);
                assertThat(run.cooldownTtlSeconds()).isBetween(1L, (long) COOLDOWN_SECONDS);
            } else {
                assertThat(run.databaseAttempts()).isEqualTo(AT_OR_ABOVE_THRESHOLD_REQUESTS);
                assertThat(run.duplicateConstraint() + run.databaseLockFailure())
                        .isEqualTo(AT_OR_ABOVE_THRESHOLD_REQUESTS - 1L);
                assertThat(run.cooldownSuppressed()).isZero();
                assertThat(run.cooldownTtlSeconds()).isEqualTo(-2L);
            }
        }
    }

    private TtlResult verifyCooldownTtlAndReacquisition() throws InterruptedException {
        redisTemplate.delete(cooldownKey());
        boolean firstAcquisition = cooldownService.tryAcquire(projectId, ERROR_CODE, TTL_VERIFICATION_SECONDS);
        long initialTtlSeconds = redisTemplate.getExpire(cooldownKey(), TimeUnit.SECONDS);
        boolean duplicateAcquisition = cooldownService.tryAcquire(projectId, ERROR_CODE, TTL_VERIFICATION_SECONDS);

        awaitCondition(() -> Boolean.FALSE.equals(redisTemplate.hasKey(cooldownKey())), TTL_EXPIRY_WAIT_TIMEOUT);
        boolean acquisitionAfterExpiry = cooldownService.tryAcquire(projectId, ERROR_CODE, TTL_VERIFICATION_SECONDS);

        assertThat(firstAcquisition).isTrue();
        assertThat(initialTtlSeconds).isBetween(1L, (long) TTL_VERIFICATION_SECONDS);
        assertThat(duplicateAcquisition).isFalse();
        assertThat(acquisitionAfterExpiry).isTrue();
        return new TtlResult(firstAcquisition, initialTtlSeconds, duplicateAcquisition, acquisitionAfterExpiry);
    }

    private void awaitCondition(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(TTL_POLL_INTERVAL_MILLIS);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private double percentile(List<Double> sortedValues, int percentile) {
        assertThat(sortedValues).isNotEmpty();
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, index));
    }

    private void writeReport(
            List<RunResult> baselineRuns,
            List<RunResult> cooldownRuns,
            TtlResult ttlResult
    ) throws IOException {
        Path reportDirectory = Path.of(System.getProperty("alert.cooldown.report.dir", "build/reports/alert-cooldown"));
        Files.createDirectories(reportDirectory);

        double baselineElapsedMedian = median(baselineRuns.stream().map(RunResult::elapsedMillis).toList());
        double cooldownElapsedMedian = median(cooldownRuns.stream().map(RunResult::elapsedMillis).toList());
        double baselineTaskP95Median = median(baselineRuns.stream().map(RunResult::taskP95Millis).toList());
        double cooldownTaskP95Median = median(cooldownRuns.stream().map(RunResult::taskP95Millis).toList());
        double baselineThroughput = INCOMING_EVENTS / (baselineElapsedMedian / 1_000.0);
        double cooldownThroughput = INCOMING_EVENTS / (cooldownElapsedMedian / 1_000.0);
        double elapsedReductionPercent = percentReduction(baselineElapsedMedian, cooldownElapsedMedian);
        double taskP95ReductionPercent = percentReduction(baselineTaskP95Median, cooldownTaskP95Median);
        double throughputIncreasePercent = percentIncrease(baselineThroughput, cooldownThroughput);
        double throughputMultiplier = cooldownThroughput / baselineThroughput;
        double databaseAttemptReductionPercent = percentReduction(AT_OR_ABOVE_THRESHOLD_REQUESTS, 1);

        StringBuilder report = new StringBuilder();
        report.append("# Alert cooldown cooldown concurrency generated report\n\n");
        report.append("- MySQL: ").append(MYSQL.getDockerImageName()).append("\n");
        report.append("- Redis: ").append(REDIS.getDockerImageName()).append("\n");
        report.append("- incoming events: ").append(INCOMING_EVENTS).append("\n");
        report.append("- threshold: ").append(THRESHOLD).append("\n");
        report.append("- requests at or above threshold: ").append(AT_OR_ABOVE_THRESHOLD_REQUESTS).append("\n");
        report.append("- concurrency: ").append(CONCURRENCY).append("\n");
        report.append("- warmup runs: ").append(WARMUP_RUNS).append("\n");
        report.append("- measurement runs: ").append(MEASUREMENT_RUNS).append("\n\n");
        report.append("## Runs\n\n");
        report.append("| scenario | run | elapsed ms | task p50 ms | task p95 ms | DB attempts | inserted | duplicate constraints | DB lock failures | stopped before DB by SET NX | Redis count | AlertLog | counter TTL s | cooldown TTL s |\n");
        report.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        appendRuns(report, "without SET NX", baselineRuns);
        appendRuns(report, "with SET NX", cooldownRuns);

        report.append("\n## Summary\n\n");
        report.append("- without SET NX elapsed median: ").append(format(baselineElapsedMedian)).append("ms\n");
        report.append("- with SET NX elapsed median: ").append(format(cooldownElapsedMedian)).append("ms\n");
        report.append("- elapsed median reduction: ").append(format(elapsedReductionPercent)).append("%\n");
        report.append("- without SET NX task p95 median: ").append(format(baselineTaskP95Median)).append("ms\n");
        report.append("- with SET NX task p95 median: ").append(format(cooldownTaskP95Median)).append("ms\n");
        report.append("- task p95 median reduction: ").append(format(taskP95ReductionPercent)).append("%\n");
        report.append("- without SET NX isolated throughput: ").append(format(baselineThroughput)).append(" events/s\n");
        report.append("- with SET NX isolated throughput: ").append(format(cooldownThroughput)).append(" events/s\n");
        report.append("- isolated throughput increase: ")
                .append(formatOneDecimal(throughputIncreasePercent))
                .append("% (approximately ")
                .append(formatTwoDecimals(throughputMultiplier))
                .append("x)\n");
        report.append("- DB attempts: ").append(AT_OR_ABOVE_THRESHOLD_REQUESTS).append(" -> 1\n");
        report.append("- DB attempt reduction: ").append(format(databaseAttemptReductionPercent)).append("%\n");
        report.append("- DB write failures: ").append(AT_OR_ABOVE_THRESHOLD_REQUESTS - 1).append(" -> 0\n");
        report.append("- without SET NX duplicate constraints range: ")
                .append(min(baselineRuns.stream().map(RunResult::duplicateConstraint).toList()))
                .append("-")
                .append(max(baselineRuns.stream().map(RunResult::duplicateConstraint).toList()))
                .append("\n");
        report.append("- without SET NX DB lock failures range: ")
                .append(min(baselineRuns.stream().map(RunResult::databaseLockFailure).toList()))
                .append("-")
                .append(max(baselineRuns.stream().map(RunResult::databaseLockFailure).toList()))
                .append("\n");
        report.append("- final AlertLog per run: 1 -> 1\n");
        report.append("- final Redis count per run: ").append(INCOMING_EVENTS).append("\n");
        report.append("- TTL first acquisition: ").append(ttlResult.firstAcquisition()).append("\n");
        report.append("- TTL initial seconds: ").append(ttlResult.initialTtlSeconds()).append("\n");
        report.append("- TTL duplicate acquisition: ").append(ttlResult.duplicateAcquisition()).append("\n");
        report.append("- TTL acquisition after expiry: ").append(ttlResult.acquisitionAfterExpiry()).append("\n");

        Files.writeString(
                reportDirectory.resolve("cooldown-concurrency-evidence.md"),
                report.toString(),
                StandardCharsets.UTF_8
        );
    }

    private void appendRuns(StringBuilder report, String scenario, List<RunResult> runs) {
        for (int index = 0; index < runs.size(); index++) {
            RunResult run = runs.get(index);
            report.append(String.format(
                    Locale.ROOT,
                    "| %s | %d | %.3f | %.3f | %.3f | %d | %d | %d | %d | %d | %d | %d | %d | %d |%n",
                    scenario,
                    index + 1,
                    run.elapsedMillis(),
                    run.taskP50Millis(),
                    run.taskP95Millis(),
                    run.databaseAttempts(),
                    run.inserted(),
                    run.duplicateConstraint(),
                    run.databaseLockFailure(),
                    run.cooldownSuppressed(),
                    run.finalRedisCount(),
                    run.finalAlertLogs(),
                    run.counterTtlSeconds(),
                    run.cooldownTtlSeconds()
            ));
        }
    }

    private double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private double percentReduction(double before, double after) {
        return (before - after) / before * 100.0;
    }

    private double percentIncrease(double before, double after) {
        return (after - before) / before * 100.0;
    }

    private long min(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).min().orElseThrow();
    }

    private long max(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).max().orElseThrow();
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private String formatOneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String formatTwoDecimals(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private enum Outcome {
        BELOW_THRESHOLD,
        INSERTED,
        DUPLICATE_CONSTRAINT,
        DB_LOCK_FAILURE,
        COOLDOWN_SUPPRESSED
    }

    private record TaskResult(Outcome outcome, long elapsedNanos) {
    }

    private record RunResult(
            boolean cooldownEnabled,
            double elapsedMillis,
            double taskP50Millis,
            double taskP95Millis,
            long belowThreshold,
            long databaseAttempts,
            long inserted,
            long duplicateConstraint,
            long databaseLockFailure,
            long cooldownSuppressed,
            long finalRedisCount,
            long finalAlertLogs,
            long counterTtlSeconds,
            long cooldownTtlSeconds
    ) {
    }

    private record TtlResult(
            boolean firstAcquisition,
            long initialTtlSeconds,
            boolean duplicateAcquisition,
            boolean acquisitionAfterExpiry
    ) {
    }
}
