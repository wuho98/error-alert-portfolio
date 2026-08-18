package com.wuho.erroralert.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ErrorEventRecentFilterIndexPerformanceTest {

    // Keep this heavy evidence test out of default test/build tasks.
    private static final int MEASUREMENT_RUNS = 3;
    private static final long TOTAL_ERROR_ROWS = 1_000_000L;
    private static final int MESSAGE_LENGTH = 1_000;
    private static final String EXISTING_INDEX = "idx_errors_project_occurred_at_desc";
    private static final String CANDIDATE_INDEX = "idx_errors_project_error_code_occurred_at_desc";
    private static final String FILTER_ERROR_CODE = "PAYMENT.WEBHOOK_PROCESSING_FAILED";
    private static final String RANGE_FROM = "2026-08-05 00:00:00";
    private static final String RANGE_TO = "2026-08-12 13:46:39";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("error_alert")
            .withUsername("error_alert")
            .withPassword("replace_with_test_mysql_password")
            .withStartupTimeout(Duration.ofMinutes(3));

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void recentFilterIndexEvidenceIsReproducibleWithWarmupRuns() throws IOException {
        migrateToVersion4();
        seedProjects();
        seedErrors();
        createCandidateIndex();
        assertFixture();

        List<MeasurementResult> measurements = new ArrayList<>();

        addOptimizerMeasurements(measurements, "optimizer / P1 hot", 1);
        addOptimizerMeasurements(measurements, "optimizer / P4 medium", 4);
        addOptimizerMeasurements(measurements, "optimizer / P10 small", 10);

        addForcedErrorCodeComparison(measurements, "P1 hot", 1);
        addForcedErrorCodeComparison(measurements, "P4 medium", 4);
        addForcedErrorCodeComparison(measurements, "P10 small", 10);

        assertPayloadSizes();
        writeReport(measurements);
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(MYSQL.getDriverClassName());
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return dataSource;
    }

    private void migrateToVersion4() {
        Flyway.configure()
                .dataSource(dataSource())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("4"))
                .load()
                .migrate();
    }

    private void createCandidateIndex() {
        jdbcTemplate.execute("""
                create index idx_errors_project_error_code_occurred_at_desc
                    on errors (project_id, error_code, occurred_at desc)
                """);
    }

    private void seedProjects() {
        for (int projectNumber = 1; projectNumber <= 10; projectNumber++) {
            jdbcTemplate.update("""
                    insert into project (name, created_at, updated_at)
                    values (?, '2026-08-01 00:00:00', '2026-08-01 00:00:00')
                    """, "recentfilter-p" + projectNumber);
        }
    }

    private void seedErrors() {
        String numbersSql = """
                select d0.n
                    + d1.n * 10
                    + d2.n * 100
                    + d3.n * 1000
                    + d4.n * 10000
                    + d5.n * 100000 as n
                from %1$s d0
                cross join %1$s d1
                cross join %1$s d2
                cross join %1$s d3
                cross join %1$s d4
                cross join %1$s d5
                """.formatted(digitTable());

        jdbcTemplate.execute("""
                insert into errors (project_id, error_code, message, occurred_at, received_at)
                select
                    case
                        when project_bucket < 400 then 1
                        when project_bucket < 650 then 2
                        when project_bucket < 800 then 3
                        when project_bucket < 880 then 4
                        when project_bucket < 930 then 5
                        when project_bucket < 960 then 6
                        when project_bucket < 980 then 7
                        when project_bucket < 990 then 8
                        when project_bucket < 995 then 9
                        else 10
                    end as project_id,
                    case
                        when error_bucket < 55 then 'PAYMENT.PG_TIMEOUT'
                        when error_bucket < 80 then 'PAYMENT.PG_SERVER_ERROR'
                        when error_bucket < 95 then 'PAYMENT.APPROVAL_PROCESSING_FAILED'
                        else 'PAYMENT.WEBHOOK_PROCESSING_FAILED'
                    end as error_code,
                    rpad(concat('recentfilter-message-', n), 1000, 'x') as message,
                    timestampadd(second, n, '2026-08-01 00:00:00') as occurred_at,
                    timestampadd(second, n + 1, '2026-08-01 00:00:00') as received_at
                from (
                    select
                        n,
                        mod(n * 997 + (n div 1000) * 37, 1000) as project_bucket,
                        mod(n * 53 + 17, 100) as error_bucket
                    from (
                        %s
                    ) generated_numbers
                ) generated_errors
                """.formatted(numbersSql));
    }

    private String digitTable() {
        return """
                (
                    select 0 as n union all select 1 union all select 2 union all select 3 union all select 4
                    union all select 5 union all select 6 union all select 7 union all select 8 union all select 9
                )
                """;
    }

    private void assertFixture() {
        Long total = jdbcTemplate.queryForObject("select count(*) from errors", Long.class);
        assertThat(total).isEqualTo(TOTAL_ERROR_ROWS);

        assertThat(rowsPerProject()).containsExactly(400_000L, 250_000L, 150_000L, 80_000L, 50_000L,
                30_000L, 20_000L, 10_000L, 5_000L, 5_000L);
        assertThat(rowsPerErrorCode()).containsExactly(550_000L, 250_000L, 150_000L, 50_000L);
        assertThat(rowsForFilterErrorCode()).isEqualTo(50_000L);
        assertThat(rowsInRangeForFilterErrorCode()).isGreaterThan(30_000L);

        Long shortestMessage = jdbcTemplate.queryForObject("select min(char_length(message)) from errors", Long.class);
        Long longestMessage = jdbcTemplate.queryForObject("select max(char_length(message)) from errors", Long.class);
        assertThat(shortestMessage).isEqualTo((long) MESSAGE_LENGTH);
        assertThat(longestMessage).isEqualTo((long) MESSAGE_LENGTH);
    }

    private List<Long> rowsPerProject() {
        return jdbcTemplate.queryForList("""
                select count(*)
                from errors
                group by project_id
                order by project_id
                """, Long.class);
    }

    private List<Long> rowsPerErrorCode() {
        return jdbcTemplate.queryForList("""
                select count(*)
                from errors
                group by error_code
                order by count(*) desc
                """, Long.class);
    }

    private Long rowsForFilterErrorCode() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from errors
                where error_code = ?
                """, Long.class, FILTER_ERROR_CODE);
    }

    private Long rowsInRangeForFilterErrorCode() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from errors
                where error_code = ?
                  and occurred_at >= ?
                  and occurred_at <= ?
                """, Long.class, FILTER_ERROR_CODE, RANGE_FROM, RANGE_TO);
    }

    private void addOptimizerMeasurements(List<MeasurementResult> measurements, String labelPrefix, int projectId) {
        addContentAndCount(measurements, labelPrefix + " / projectId only",
                projectLookup(projectId, null, null, null, "limit 20"), countProjectRows(projectId, null, null, null));
        addContentAndCount(measurements, labelPrefix + " / projectId only",
                projectLookup(projectId, null, null, null, "limit 100"), null);
        addContentAndCount(measurements, labelPrefix + " / projectId + from/to",
                projectLookup(projectId, null, RANGE_FROM, RANGE_TO, "limit 20"),
                countProjectRows(projectId, null, RANGE_FROM, RANGE_TO));
        addContentAndCount(measurements, labelPrefix + " / projectId + from/to",
                projectLookup(projectId, null, RANGE_FROM, RANGE_TO, "limit 100"), null);
        addContentAndCount(measurements, labelPrefix + " / projectId + errorCode",
                projectLookup(projectId, FILTER_ERROR_CODE, null, null, "limit 20"),
                countProjectRows(projectId, FILTER_ERROR_CODE, null, null));
        addContentAndCount(measurements, labelPrefix + " / projectId + errorCode",
                projectLookup(projectId, FILTER_ERROR_CODE, null, null, "limit 100"), null);
        addContentAndCount(measurements, labelPrefix + " / projectId + errorCode + from/to",
                projectLookup(projectId, FILTER_ERROR_CODE, RANGE_FROM, RANGE_TO, "limit 20"),
                countProjectRows(projectId, FILTER_ERROR_CODE, RANGE_FROM, RANGE_TO));
        addContentAndCount(measurements, labelPrefix + " / projectId + errorCode + from/to",
                projectLookup(projectId, FILTER_ERROR_CODE, RANGE_FROM, RANGE_TO, "limit 100"), null);
    }

    private void addForcedErrorCodeComparison(List<MeasurementResult> measurements, String labelPrefix, int projectId) {
        List<String> indexes = List.of(EXISTING_INDEX, CANDIDATE_INDEX);
        for (String index : indexes) {
            addContentAndCount(measurements, "forced " + index + " / " + labelPrefix + " / projectId + errorCode",
                    projectLookupWithForceIndex(projectId, FILTER_ERROR_CODE, null, null, "limit 20", index),
                    countProjectRowsWithForceIndex(projectId, FILTER_ERROR_CODE, null, null, index));
            addContentAndCount(measurements, "forced " + index + " / " + labelPrefix + " / projectId + errorCode",
                    projectLookupWithForceIndex(projectId, FILTER_ERROR_CODE, null, null, "limit 100", index), null);
            addContentAndCount(measurements,
                    "forced " + index + " / " + labelPrefix + " / projectId + errorCode + from/to",
                    projectLookupWithForceIndex(projectId, FILTER_ERROR_CODE, RANGE_FROM, RANGE_TO, "limit 20", index),
                    countProjectRowsWithForceIndex(projectId, FILTER_ERROR_CODE, RANGE_FROM, RANGE_TO, index));
            addContentAndCount(measurements,
                    "forced " + index + " / " + labelPrefix + " / projectId + errorCode + from/to",
                    projectLookupWithForceIndex(projectId, FILTER_ERROR_CODE, RANGE_FROM, RANGE_TO, "limit 100", index),
                    null);
        }
    }

    private void addContentAndCount(
            List<MeasurementResult> measurements,
            String labelPrefix,
            String contentSql,
            String countSql
    ) {
        measurements.add(measure(labelPrefix + " / content / " + limitLabel(contentSql), contentSql));
        if (countSql != null) {
            measurements.add(measure(labelPrefix + " / count", countSql));
        }
    }

    private MeasurementResult measure(String label, String sql) {
        explainAnalyze(sql);

        List<Double> measuredMillis = new ArrayList<>();
        String representativePlan = "";
        for (int run = 0; run < MEASUREMENT_RUNS; run++) {
            String plan = explainAnalyze(sql);
            if (run == 0) {
                representativePlan = plan;
            }
            measuredMillis.add(extractFirstActualEndMillis(plan));
        }

        assertPlanShape(label, representativePlan);
        return new MeasurementResult(label, sql, measuredMillis, representativePlan);
    }

    private String explainAnalyze(String sql) {
        return jdbcTemplate.query("explain analyze " + sql, resultSet -> {
            List<String> lines = new ArrayList<>();
            while (resultSet.next()) {
                lines.add(resultSet.getString(1));
            }
            return String.join(System.lineSeparator(), lines);
        });
    }

    private double extractFirstActualEndMillis(String plan) {
        String actualTimePrefix = "actual time=";
        int start = plan.indexOf(actualTimePrefix);
        assertThat(start).isGreaterThanOrEqualTo(0);

        int rangeStart = start + actualTimePrefix.length();
        int rangeSeparator = plan.indexOf("..", rangeStart);
        int rangeEnd = plan.indexOf(' ', rangeSeparator);
        assertThat(rangeSeparator).isGreaterThan(rangeStart);
        assertThat(rangeEnd).isGreaterThan(rangeSeparator);

        return Double.parseDouble(plan.substring(rangeSeparator + 2, rangeEnd));
    }

    private void assertPlanShape(String label, String plan) {
        String normalizedLabel = label.toLowerCase(Locale.ROOT);
        String normalizedPlan = plan.toLowerCase(Locale.ROOT);

        if (normalizedLabel.startsWith("forced " + EXISTING_INDEX.toLowerCase(Locale.ROOT))) {
            assertThat(normalizedPlan).contains(EXISTING_INDEX.toLowerCase(Locale.ROOT));
        }
        if (normalizedLabel.startsWith("forced " + CANDIDATE_INDEX.toLowerCase(Locale.ROOT))) {
            assertThat(normalizedPlan).contains(CANDIDATE_INDEX.toLowerCase(Locale.ROOT));
        }
        if (normalizedLabel.startsWith("optimizer")
                && normalizedLabel.contains("projectid only")
                && normalizedLabel.contains("content")) {
            assertThat(normalizedPlan).contains(EXISTING_INDEX.toLowerCase(Locale.ROOT));
        }
        if (normalizedLabel.startsWith("optimizer")
                && normalizedLabel.contains("projectid + from/to")
                && normalizedLabel.contains("content")) {
            assertThat(normalizedPlan).contains(EXISTING_INDEX.toLowerCase(Locale.ROOT));
        }
        if (normalizedLabel.startsWith("optimizer") && normalizedLabel.contains("errorcode")) {
            assertThat(normalizedPlan).contains(CANDIDATE_INDEX.toLowerCase(Locale.ROOT));
        }
        assertThat(normalizedPlan).doesNotContain("sort");
    }

    private void assertPayloadSizes() {
        assertMessagePayloadChars(1, FILTER_ERROR_CODE, null, null, 20, 20_000L);
        assertMessagePayloadChars(1, FILTER_ERROR_CODE, null, null, 100, 100_000L);
        assertMessagePayloadChars(10, FILTER_ERROR_CODE, RANGE_FROM, RANGE_TO, 100, 100_000L);
    }

    private void assertMessagePayloadChars(
            int projectId,
            String errorCode,
            String from,
            String to,
            int limit,
            long expectedChars
    ) {
        Long actualChars = jdbcTemplate.queryForObject("""
                select coalesce(sum(char_length(message)), 0)
                from (
                    %s
                ) recent_errors
                """.formatted(projectLookup(projectId, errorCode, from, to, "limit " + limit)), Long.class);

        assertThat(actualChars).isEqualTo(expectedChars);
    }

    private String projectLookup(int projectId, String errorCode, String from, String to, String limitClause) {
        return """
                select id, project_id, error_code, message, occurred_at, received_at
                from errors
                where %s
                order by occurred_at desc
                %s
                """.formatted(whereClause(projectId, errorCode, from, to), limitClause);
    }

    private String projectLookupWithForceIndex(
            int projectId,
            String errorCode,
            String from,
            String to,
            String limitClause,
            String indexName
    ) {
        return """
                select id, project_id, error_code, message, occurred_at, received_at
                from errors force index (%s)
                where %s
                order by occurred_at desc
                %s
                """.formatted(indexName, whereClause(projectId, errorCode, from, to), limitClause);
    }

    private String countProjectRows(int projectId, String errorCode, String from, String to) {
        return """
                select count(*)
                from errors
                where %s
                """.formatted(whereClause(projectId, errorCode, from, to));
    }

    private String countProjectRowsWithForceIndex(
            int projectId,
            String errorCode,
            String from,
            String to,
            String indexName
    ) {
        return """
                select count(*)
                from errors force index (%s)
                where %s
                """.formatted(indexName, whereClause(projectId, errorCode, from, to));
    }

    private String whereClause(int projectId, String errorCode, String from, String to) {
        List<String> predicates = new ArrayList<>();
        predicates.add("project_id = " + projectId);
        if (errorCode != null) {
            predicates.add("error_code = '" + errorCode + "'");
        }
        if (from != null) {
            predicates.add("occurred_at >= '" + from + "'");
        }
        if (to != null) {
            predicates.add("occurred_at <= '" + to + "'");
        }
        return String.join(System.lineSeparator() + "  and ", predicates);
    }

    private String limitLabel(String sql) {
        String normalizedSql = sql.toLowerCase(Locale.ROOT);
        if (normalizedSql.contains("limit 100")) {
            return "LIMIT 100";
        }
        if (normalizedSql.contains("limit 20")) {
            return "LIMIT 20";
        }
        return "no limit";
    }

    private void writeReport(List<MeasurementResult> measurements) throws IOException {
        Path reportDir = Path.of(System.getProperty("recent.filter.index.report.dir", "build/reports/recent-filter-index"));
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("index-evidence.md"), renderReport(measurements), StandardCharsets.UTF_8);
    }

    private String renderReport(List<MeasurementResult> measurements) {
        StringBuilder report = new StringBuilder();
        report.append("# Recent filter index Recent Error Filter Index Evidence\n\n");
        report.append("- MySQL image: `mysql:8.4`\n");
        report.append("- Fixture rows: `1,000,000`\n");
        report.append("- Message length: `1,000`\n");
        report.append("- Filter errorCode: `").append(FILTER_ERROR_CODE).append("`\n");
        report.append("- Range filter: `").append(RANGE_FROM).append("` ~ `").append(RANGE_TO).append("`\n");
        report.append("- Existing index: `").append(EXISTING_INDEX).append("`\n");
        report.append("- Candidate index: `").append(CANDIDATE_INDEX).append("`\n");
        report.append("- Warm-up: each `EXPLAIN ANALYZE` query runs once before recorded measurements\n");
        report.append("- Recorded runs per scenario: `").append(MEASUREMENT_RUNS).append("`\n\n");
        report.append("| Scenario | median ms | min ms | max ms | index used | filesort |\n");
        report.append("| --- | ---: | ---: | ---: | --- | --- |\n");
        for (MeasurementResult measurement : measurements) {
            report.append("| ")
                    .append(measurement.label())
                    .append(" | ")
                    .append(formatMillis(measurement.medianMillis()))
                    .append(" | ")
                    .append(formatMillis(measurement.minMillis()))
                    .append(" | ")
                    .append(formatMillis(measurement.maxMillis()))
                    .append(" | ")
                    .append(measurement.indexUsed())
                    .append(" | ")
                    .append(measurement.hasFilesort())
                    .append(" |\n");
        }
        report.append("\n## Representative Plans\n\n");
        for (MeasurementResult measurement : measurements) {
            report.append("### ").append(measurement.label()).append("\n\n");
            report.append("```sql\n").append(measurement.sql().strip()).append("\n```\n\n");
            report.append("```text\n").append(measurement.plan()).append("\n```\n\n");
        }
        return report.toString();
    }

    private String formatMillis(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private record MeasurementResult(String label, String sql, List<Double> measuredMillis, String plan) {

        private double medianMillis() {
            List<Double> sorted = measuredMillis.stream()
                    .sorted()
                    .toList();
            return sorted.get(sorted.size() / 2);
        }

        private double minMillis() {
            return statistics().getMin();
        }

        private double maxMillis() {
            return statistics().getMax();
        }

        private DoubleSummaryStatistics statistics() {
            return measuredMillis.stream()
                    .mapToDouble(Double::doubleValue)
                    .summaryStatistics();
        }

        private String indexUsed() {
            String normalizedPlan = plan.toLowerCase(Locale.ROOT);
            if (normalizedPlan.contains(CANDIDATE_INDEX.toLowerCase(Locale.ROOT))) {
                return CANDIDATE_INDEX;
            }
            if (normalizedPlan.contains(EXISTING_INDEX.toLowerCase(Locale.ROOT))) {
                return EXISTING_INDEX;
            }
            if (normalizedPlan.contains("fk_errors_project")) {
                return "fk_errors_project";
            }
            return "unknown";
        }

        private boolean hasFilesort() {
            return plan.toLowerCase(Locale.ROOT).contains("sort");
        }
    }
}
