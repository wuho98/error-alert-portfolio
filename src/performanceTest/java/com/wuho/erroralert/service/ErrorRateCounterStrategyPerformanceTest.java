package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ErrorRateCounterStrategyPerformanceTest {

    private static final int EVENT_COUNT = 1_000;
    private static final int MEASUREMENT_RUNS = 3;
    private static final int THRESHOLD = 10;
    private static final long PROJECT_ID = 1L;
    private static final String ERROR_CODE = "PAYMENT.PG_TIMEOUT";
    private static final String MESSAGE = "PG approval request timed out";
    private static final LocalDateTime WINDOW_STARTED_AT = LocalDateTime.of(2026, 8, 13, 12, 34);
    private static final LocalDateTime WINDOW_ENDED_AT = WINDOW_STARTED_AT.plusSeconds(60);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("error_alert")
            .withUsername("error_alert")
            .withPassword("replace_with_test_mysql_password")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(2));

    private JdbcTemplate jdbcTemplate;
    private LettuceConnectionFactory redisConnectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource());
        migrateToLatest();
        resetDatabase();

        redisConnectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        redisConnectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(redisConnectionFactory);
        redisTemplate.afterPropertiesSet();
        flushRedis();
    }

    @AfterEach
    void tearDown() {
        if (redisConnectionFactory != null) {
            redisConnectionFactory.destroy();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void counterStrategyEvidenceIsReproducibleWithThreeRuns() throws IOException {
        List<RunResult> results = new ArrayList<>();

        for (int run = 1; run <= MEASUREMENT_RUNS; run++) {
            results.add(measure("DB period count", run, ignored -> new DbPeriodCountStrategy()));
            results.add(measure("in-memory counter", run, ignored -> new InMemoryCounterStrategy()));
            results.add(measure("Redis INCR + EXPIRE", run, RedisCounterStrategy::new));
        }

        assertThat(results).hasSize(MEASUREMENT_RUNS * 3);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.finalCount()).isEqualTo(EVENT_COUNT);
            assertThat(result.firstThresholdEventIndex()).isEqualTo(THRESHOLD);
        });
        assertThat(results.stream()
                .filter(result -> result.strategy().equals("DB period count"))
                .mapToLong(RunResult::dbAggregateQueryCount))
                .containsOnly((long) EVENT_COUNT);
        assertThat(results.stream()
                .filter(result -> result.strategy().equals("in-memory counter"))
                .map(RunResult::retainsStateAfterApplicationRestart)
                .toList())
                .containsOnly(false);
        assertThat(results.stream()
                .filter(result -> !result.strategy().equals("in-memory counter"))
                .map(RunResult::retainsStateAfterApplicationRestart)
                .toList())
                .containsOnly(true);

        writeReport(results);
    }

    private RunResult measure(String strategyName, int run, StrategyFactory strategyFactory) {
        resetScenarioState();
        CounterStrategy strategy = strategyFactory.create(redisTemplate);

        List<Long> counterLatencyNanos = new ArrayList<>(EVENT_COUNT);
        List<Long> writeAndDetectLatencyNanos = new ArrayList<>(EVENT_COUNT);
        int firstThresholdEventIndex = -1;
        long finalCount = 0L;

        for (int i = 0; i < EVENT_COUNT; i++) {
            LocalDateTime receivedAt = WINDOW_STARTED_AT.plusNanos(TimeUnit.MILLISECONDS.toNanos(i));

            long totalStartedAt = System.nanoTime();
            insertErrorEvent(receivedAt);

            long counterStartedAt = System.nanoTime();
            long count = strategy.increment(PROJECT_ID, ERROR_CODE, receivedAt);
            long counterElapsedNanos = System.nanoTime() - counterStartedAt;
            long totalElapsedNanos = System.nanoTime() - totalStartedAt;

            counterLatencyNanos.add(counterElapsedNanos);
            writeAndDetectLatencyNanos.add(totalElapsedNanos);
            finalCount = count;

            if (firstThresholdEventIndex == -1 && count >= THRESHOLD) {
                firstThresholdEventIndex = i + 1;
            }
        }

        long persistedErrorRows = countErrorRows();
        boolean retainsStateAfterApplicationRestart = strategy.retainsStateAfterApplicationRestart(
                redisTemplate, PROJECT_ID, ERROR_CODE, WINDOW_STARTED_AT);

        return new RunResult(
                strategyName,
                run,
                finalCount,
                persistedErrorRows,
                firstThresholdEventIndex,
                counterLatencyNanos,
                writeAndDetectLatencyNanos,
                EVENT_COUNT,
                strategy.dbAggregateQueryCount(),
                strategy.redisCommandCount(),
                retainsStateAfterApplicationRestart
        );
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(MYSQL.getDriverClassName());
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return dataSource;
    }

    private void migrateToLatest() {
        Flyway.configure()
                .dataSource(dataSource())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void resetDatabase() {
        jdbcTemplate.update("delete from alert_log");
        jdbcTemplate.update("delete from errors");
        jdbcTemplate.update("delete from project_api_key");
        jdbcTemplate.update("delete from project_setting");
        jdbcTemplate.update("delete from project");
        jdbcTemplate.update("""
                insert into project (id, name, created_at, updated_at)
                values (?, ?, ?, ?)
                """, PROJECT_ID, "counter-strategy-evidence", timestamp(WINDOW_STARTED_AT), timestamp(WINDOW_STARTED_AT));
    }

    private void resetScenarioState() {
        jdbcTemplate.update("delete from errors");
        flushRedis();
    }

    private void flushRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    private void insertErrorEvent(LocalDateTime receivedAt) {
        jdbcTemplate.update("""
                insert into errors (project_id, error_code, message, occurred_at, received_at)
                values (?, ?, ?, ?, ?)
                """, PROJECT_ID, ERROR_CODE, MESSAGE, timestamp(receivedAt), timestamp(receivedAt));
    }

    private long countErrorRows() {
        Long count = jdbcTemplate.queryForObject("select count(*) from errors", Long.class);
        assertThat(count).isNotNull();
        return count;
    }

    private static Timestamp timestamp(LocalDateTime dateTime) {
        return Timestamp.valueOf(dateTime);
    }

    private void writeReport(List<RunResult> results) throws IOException {
        Path reportDir = Path.of(System.getProperty(
                "counter.strategy.report.dir",
                "build/reports/error-rate-counter-strategy"));
        Files.createDirectories(reportDir);
        Files.writeString(
                reportDir.resolve("counter-strategy-evidence.md"),
                renderReport(results),
                StandardCharsets.UTF_8);
    }

    private String renderReport(List<RunResult> results) {
        StringBuilder report = new StringBuilder();
        report.append("# Error Rate Counter Strategy Evidence\n\n");
        report.append("- Scenario: `counter strategy comparison`\n");
        report.append("- MySQL image: `mysql:8.4`\n");
        report.append("- Redis image: `redis:7-alpine`\n");
        report.append("- Input: same project and same errorCode, `").append(EVENT_COUNT).append("` events in one 60-second window\n");
        report.append("- Error code: `").append(ERROR_CODE).append("`\n");
        report.append("- Window: `").append(WINDOW_STARTED_AT).append("` ~ `").append(WINDOW_ENDED_AT).append("`\n");
        report.append("- Threshold: `").append(THRESHOLD).append("`\n");
        report.append("- Recorded runs per strategy: `").append(MEASUREMENT_RUNS).append("`\n");
        report.append("- Latency unit: milliseconds measured with `System.nanoTime()` in the JUnit process\n\n");

        report.append("## Evidence Scope\n\n");
        report.append("- Directly measured: strategy latency median/p95, DB writes, DB aggregate query counts, Redis command counts, and counter state after an application-process restart simulation.\n");
        report.append("- Not isolated: why Redis commands take the measured time. Network round trips, serialization, and Docker/Testcontainers effects are not measured separately in this evidence.\n");
        report.append("- Follow-up trigger: if Redis command latency exceeds the operations target, measure Redis command latency, pipelining, and Lua script options separately.\n\n");

        report.append("## Result Summary\n\n");
        report.append("| Strategy | counter median ms | counter p95 ms | write+detect median ms | write+detect p95 ms | DB writes/run | DB count queries/run | Redis commands/run | app restart state |\n");
        report.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (StrategySummary summary : summarize(results)) {
            report.append("| ")
                    .append(summary.strategy())
                    .append(" | ")
                    .append(formatMillis(summary.counterMedianMillis()))
                    .append(" | ")
                    .append(formatMillis(summary.counterP95Millis()))
                    .append(" | ")
                    .append(formatMillis(summary.writeAndDetectMedianMillis()))
                    .append(" | ")
                    .append(formatMillis(summary.writeAndDetectP95Millis()))
                    .append(" | ")
                    .append(summary.dbWriteCount())
                    .append(" | ")
                    .append(summary.dbAggregateQueryCount())
                    .append(" | ")
                    .append(summary.redisCommandCount())
                    .append(" | ")
                    .append(summary.retainsStateAfterApplicationRestart() ? "retained" : "lost")
                    .append(" |\n");
        }

        report.append("\n## Runs\n\n");
        report.append("| Strategy | run | final count | persisted rows | first threshold event | counter median ms | counter p95 ms | write+detect median ms | write+detect p95 ms |\n");
        report.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (RunResult result : results) {
            report.append("| ")
                    .append(result.strategy())
                    .append(" | ")
                    .append(result.run())
                    .append(" | ")
                    .append(result.finalCount())
                    .append(" | ")
                    .append(result.persistedErrorRows())
                    .append(" | ")
                    .append(result.firstThresholdEventIndex())
                    .append(" | ")
                    .append(formatMillis(result.counterMedianMillis()))
                    .append(" | ")
                    .append(formatMillis(result.counterP95Millis()))
                    .append(" | ")
                    .append(formatMillis(result.writeAndDetectMedianMillis()))
                    .append(" | ")
                    .append(formatMillis(result.writeAndDetectP95Millis()))
                    .append(" |\n");
        }

        report.append("\n## Interpretation\n\n");
        report.append("- DB period count executes one extra `COUNT(*)` query per received error, so the same 1,000 events add 1,000 aggregate reads on top of the 1,000 inserts.\n");
        report.append("- In-memory counting avoids extra DB queries but loses counter state when the application process restarts and does not work across multiple application instances.\n");
        report.append("- Redis counting avoids DB aggregate reads and keeps counter state outside the application process. The current MVP limitation remains fixed-window `INCR + EXPIRE`, not an exact sliding window.\n");

        return report.toString();
    }

    private List<StrategySummary> summarize(List<RunResult> results) {
        return results.stream()
                .collect(java.util.stream.Collectors.groupingBy(RunResult::strategy))
                .entrySet()
                .stream()
                .map(entry -> StrategySummary.from(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(summary -> switch (summary.strategy()) {
                    case "DB period count" -> 0;
                    case "in-memory counter" -> 1;
                    case "Redis INCR + EXPIRE" -> 2;
                    default -> 3;
                }))
                .toList();
    }

    private static double medianMillis(List<Long> nanos) {
        List<Long> sorted = nanos.stream()
                .sorted()
                .toList();
        return nanosToMillis(sorted.get(sorted.size() / 2));
    }

    private static double p95Millis(List<Long> nanos) {
        List<Long> sorted = nanos.stream()
                .sorted()
                .toList();
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return nanosToMillis(sorted.get(Math.max(0, Math.min(index, sorted.size() - 1))));
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    private String formatMillis(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private interface StrategyFactory {

        CounterStrategy create(StringRedisTemplate redisTemplate);
    }

    private interface CounterStrategy {

        long increment(long projectId, String errorCode, LocalDateTime receivedAt);

        long dbAggregateQueryCount();

        long redisCommandCount();

        boolean retainsStateAfterApplicationRestart(
                StringRedisTemplate redisTemplate,
                long projectId,
                String errorCode,
                LocalDateTime receivedAt);
    }

    private class DbPeriodCountStrategy implements CounterStrategy {

        private long aggregateQueryCount;

        @Override
        public long increment(long projectId, String errorCode, LocalDateTime receivedAt) {
            aggregateQueryCount++;
            return countRows(projectId, errorCode, receivedAt);
        }

        @Override
        public long dbAggregateQueryCount() {
            return aggregateQueryCount;
        }

        @Override
        public long redisCommandCount() {
            return 0L;
        }

        @Override
        public boolean retainsStateAfterApplicationRestart(
                StringRedisTemplate redisTemplate,
                long projectId,
                String errorCode,
                LocalDateTime receivedAt) {
            return countRows(projectId, errorCode, receivedAt) == EVENT_COUNT;
        }

        private long countRows(long projectId, String errorCode, LocalDateTime receivedAt) {
            LocalDateTime windowStart = windowStart(receivedAt);
            LocalDateTime windowEnd = windowStart.plusSeconds(60);
            Long count = jdbcTemplate.queryForObject("""
                    select count(*)
                    from errors
                    where project_id = ?
                      and error_code = ?
                      and received_at >= ?
                      and received_at < ?
                    """, Long.class, projectId, errorCode, timestamp(windowStart), timestamp(windowEnd));
            assertThat(count).isNotNull();
            return count;
        }
    }

    private static class InMemoryCounterStrategy implements CounterStrategy {

        private final Map<String, Long> counts = new ConcurrentHashMap<>();

        @Override
        public long increment(long projectId, String errorCode, LocalDateTime receivedAt) {
            return counts.merge(counterKey(projectId, errorCode, receivedAt), 1L, Long::sum);
        }

        @Override
        public long dbAggregateQueryCount() {
            return 0L;
        }

        @Override
        public long redisCommandCount() {
            return 0L;
        }

        @Override
        public boolean retainsStateAfterApplicationRestart(
                StringRedisTemplate redisTemplate,
                long projectId,
                String errorCode,
                LocalDateTime receivedAt) {
            InMemoryCounterStrategy restarted = new InMemoryCounterStrategy();
            return restarted.currentCount(projectId, errorCode, receivedAt) == EVENT_COUNT;
        }

        private long currentCount(long projectId, String errorCode, LocalDateTime receivedAt) {
            return counts.getOrDefault(counterKey(projectId, errorCode, receivedAt), 0L);
        }
    }

    private static class RedisCounterStrategy implements CounterStrategy {

        private static final Duration COUNTER_TTL = Duration.ofSeconds(60);

        private final StringRedisTemplate redisTemplate;
        private long redisCommandCount;

        RedisCounterStrategy(StringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        @Override
        public long increment(long projectId, String errorCode, LocalDateTime receivedAt) {
            String key = counterKey(projectId, errorCode, receivedAt);
            Long count = redisTemplate.opsForValue().increment(key);
            redisCommandCount++;
            redisTemplate.expire(key, COUNTER_TTL);
            redisCommandCount++;
            assertThat(count).isNotNull();
            return count;
        }

        @Override
        public long dbAggregateQueryCount() {
            return 0L;
        }

        @Override
        public long redisCommandCount() {
            return redisCommandCount;
        }

        @Override
        public boolean retainsStateAfterApplicationRestart(
                StringRedisTemplate redisTemplate,
                long projectId,
                String errorCode,
                LocalDateTime receivedAt) {
            String value = redisTemplate.opsForValue().get(counterKey(projectId, errorCode, receivedAt));
            return Long.toString(EVENT_COUNT).equals(value);
        }
    }

    private static String counterKey(long projectId, String errorCode, LocalDateTime receivedAt) {
        return "%s:%d:%s:%d".formatted(
                "error:count",
                projectId,
                errorCode,
                windowStart(receivedAt).toEpochSecond(ZoneOffset.UTC));
    }

    private static LocalDateTime windowStart(LocalDateTime receivedAt) {
        long epochSecond = receivedAt.toEpochSecond(ZoneOffset.UTC);
        long windowStartEpochSecond = Math.floorDiv(epochSecond, 60L) * 60L;
        return LocalDateTime.ofEpochSecond(windowStartEpochSecond, 0, ZoneOffset.UTC);
    }

    private record RunResult(
            String strategy,
            int run,
            long finalCount,
            long persistedErrorRows,
            int firstThresholdEventIndex,
            List<Long> counterLatencyNanos,
            List<Long> writeAndDetectLatencyNanos,
            long dbWriteCount,
            long dbAggregateQueryCount,
            long redisCommandCount,
            boolean retainsStateAfterApplicationRestart
    ) {

        private double counterMedianMillis() {
            return medianMillis(counterLatencyNanos);
        }

        private double counterP95Millis() {
            return p95Millis(counterLatencyNanos);
        }

        private double writeAndDetectMedianMillis() {
            return medianMillis(writeAndDetectLatencyNanos);
        }

        private double writeAndDetectP95Millis() {
            return p95Millis(writeAndDetectLatencyNanos);
        }
    }

    private record StrategySummary(
            String strategy,
            double counterMedianMillis,
            double counterP95Millis,
            double writeAndDetectMedianMillis,
            double writeAndDetectP95Millis,
            long dbWriteCount,
            long dbAggregateQueryCount,
            long redisCommandCount,
            boolean retainsStateAfterApplicationRestart
    ) {

        private static StrategySummary from(String strategy, List<RunResult> results) {
            return new StrategySummary(
                    strategy,
                    medianOf(results, RunResult::counterMedianMillis),
                    medianOf(results, RunResult::counterP95Millis),
                    medianOf(results, RunResult::writeAndDetectMedianMillis),
                    medianOf(results, RunResult::writeAndDetectP95Millis),
                    first(results).dbWriteCount(),
                    first(results).dbAggregateQueryCount(),
                    first(results).redisCommandCount(),
                    results.stream().allMatch(RunResult::retainsStateAfterApplicationRestart)
            );
        }

        private static RunResult first(List<RunResult> results) {
            assertThat(results).isNotEmpty();
            return results.get(0);
        }

        private static double medianOf(List<RunResult> results, java.util.function.ToDoubleFunction<RunResult> mapper) {
            List<Double> values = results.stream()
                    .mapToDouble(mapper)
                    .boxed()
                    .sorted()
                    .toList();
            return values.get(values.size() / 2);
        }
    }
}
