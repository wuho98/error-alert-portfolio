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
class ErrorEventRecentLookupPerformanceTest {

    // 기본 test/build에는 포함하지 않고 recentLookupPerformanceTest task로만 실행한다.
    private static final int MEASUREMENT_RUNS = 3;
    private static final long TOTAL_ERROR_ROWS = 1_000_000L;
    private static final int MESSAGE_LENGTH = 1_000;
    private static final String ADOPTED_INDEX = "idx_errors_project_occurred_at_desc";
    private static final String ERROR_CODE_INDEX = "idx_errors_project_error_code_occurred_at_desc";

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
    void recentLookupIndexEvidenceIsReproducibleWithWarmupRuns() throws IOException {
        migrateToVersion3();
        seedProjects();
        seedErrors();
        assertFixture();

        List<MeasurementResult> measurements = new ArrayList<>();
        measurements.add(measure("V3 FK only / P1 LIMIT 20", projectLookup(1, "limit 20"), "fk_errors_project", true));
        measurements.add(measure("V3 FK only / P1 LIMIT 100", projectLookup(1, "limit 100"), "fk_errors_project", true));
        measurements.add(measure("V3 FK only / P1 no limit", projectLookup(1, ""), "fk_errors_project", true));
        measurements.add(measure("V3 FK only / P4 LIMIT 20", projectLookup(4, "limit 20"), "fk_errors_project", true));
        measurements.add(measure("V3 FK only / P10 LIMIT 20", projectLookup(10, "limit 20"), "fk_errors_project", true));

        migrateToLatest();
        measurements.add(measure("V4 adopted index / P1 LIMIT 20", projectLookup(1, "limit 20"), ADOPTED_INDEX, false));
        measurements.add(measure("V4 adopted index / P1 LIMIT 100", projectLookup(1, "limit 100"), ADOPTED_INDEX, false));
        measurements.add(measure("V4 adopted index / P1 no limit", projectLookup(1, ""), ADOPTED_INDEX, false));
        measurements.add(measure("V4 adopted index / P4 LIMIT 20", projectLookup(4, "limit 20"), ADOPTED_INDEX, false));
        measurements.add(measure("V4 adopted index / P10 LIMIT 20", projectLookup(10, "limit 20"), ADOPTED_INDEX, false));
        measurements.add(measure("V4 count / P1", countProjectRows(1), ADOPTED_INDEX, false));
        measurements.add(measure("V4 count / P4", countProjectRows(4), ADOPTED_INDEX, false));
        measurements.add(measure("V4 count / P10", countProjectRows(10), ADOPTED_INDEX, false));

        jdbcTemplate.execute("""
                create index idx_errors_project_error_code_occurred_at_desc
                    on errors (project_id, error_code, occurred_at desc)
                """);
        measurements.add(measure(
                "candidate forced / project-only P1 LIMIT 20",
                projectLookupWithForceIndex(1, ERROR_CODE_INDEX, "limit 20"),
                ERROR_CODE_INDEX,
                true
        ));
        measurements.add(measure(
                "candidate forced / P1 PAYMENT.PG_TIMEOUT LIMIT 20",
                projectAndErrorCodeLookupWithForceIndex(1, "PAYMENT.PG_TIMEOUT", ERROR_CODE_INDEX, "limit 20"),
                ERROR_CODE_INDEX,
                false
        ));

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

    private void migrateToVersion3() {
        Flyway.configure()
                .dataSource(dataSource())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3"))
                .load()
                .migrate();
    }

    private void migrateToLatest() {
        Flyway.configure()
                .dataSource(dataSource())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void seedProjects() {
        for (int projectNumber = 1; projectNumber <= 10; projectNumber++) {
            jdbcTemplate.update("""
                    insert into project (name, created_at, updated_at)
                    values (?, '2026-08-01 00:00:00', '2026-08-01 00:00:00')
                    """, "recentlookup-p" + projectNumber);
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
                    rpad(concat('recentlookup-message-', n), 1000, 'x') as message,
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

    private MeasurementResult measure(String label, String sql, String expectedIndex, boolean expectsSort) {
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

        assertPlanShape(representativePlan, expectedIndex, expectsSort);
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

    private void assertPlanShape(String plan, String expectedIndex, boolean expectsSort) {
        String normalizedPlan = plan.toLowerCase(Locale.ROOT);

        assertThat(normalizedPlan)
                .as("Expected index '%s' not found in plan", expectedIndex)
                .contains(expectedIndex.toLowerCase(Locale.ROOT));
        if (expectsSort) {
            assertThat(normalizedPlan)
                    .as("Expected 'sort' in plan but not found")
                    .contains("sort");
            return;
        }
        assertThat(normalizedPlan)
                .as("Unexpected 'sort' found in plan")
                .doesNotContain("sort");
    }

    private void assertPayloadSizes() {
        assertMessagePayloadChars(1, null, 400_000_000L);
        assertMessagePayloadChars(1, 20, 20_000L);
        assertMessagePayloadChars(1, 100, 100_000L);
        assertMessagePayloadChars(4, null, 80_000_000L);
        assertMessagePayloadChars(10, null, 5_000_000L);
    }

    private void assertMessagePayloadChars(int projectId, Integer limit, long expectedChars) {
        String limitClause = limit == null ? "" : "limit " + limit;
        Long actualChars = jdbcTemplate.queryForObject("""
                select coalesce(sum(char_length(message)), 0)
                from (
                    select message
                    from errors
                    where project_id = ?
                    order by occurred_at desc
                    %s
                ) recent_errors
                """.formatted(limitClause), Long.class, projectId);

        assertThat(actualChars).isEqualTo(expectedChars);
    }

    private String projectLookup(int projectId, String limitClause) {
        return """
                select id, project_id, error_code, message, occurred_at, received_at
                from errors
                where project_id = %d
                order by occurred_at desc
                %s
                """.formatted(projectId, limitClause);
    }

    private String projectLookupWithForceIndex(int projectId, String indexName, String limitClause) {
        return """
                select id, project_id, error_code, message, occurred_at, received_at
                from errors force index (%s)
                where project_id = %d
                order by occurred_at desc
                %s
                """.formatted(indexName, projectId, limitClause);
    }

    private String projectAndErrorCodeLookupWithForceIndex(
            int projectId,
            String errorCode,
            String indexName,
            String limitClause
    ) {
        return """
                select id, project_id, error_code, message, occurred_at, received_at
                from errors force index (%s)
                where project_id = %d
                    and error_code = '%s'
                order by occurred_at desc
                %s
                """.formatted(indexName, projectId, errorCode, limitClause);
    }

    private String countProjectRows(int projectId) {
        return """
                select count(*)
                from errors
                where project_id = %d
                """.formatted(projectId);
    }

    private void writeReport(List<MeasurementResult> measurements) throws IOException {
        Path reportDir = Path.of(System.getProperty("recent.lookup.report.dir", "build/reports/recent-lookup"));
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("index-evidence.md"), renderReport(measurements), StandardCharsets.UTF_8);
    }

    private String renderReport(List<MeasurementResult> measurements) {
        StringBuilder report = new StringBuilder();
        report.append("# Recent lookup Recent Lookup Index Evidence\n\n");
        report.append("- MySQL image: `mysql:8.4`\n");
        report.append("- Fixture rows: `1,000,000`\n");
        report.append("- Message length: `1,000`\n");
        report.append("- Warm-up: each `EXPLAIN ANALYZE` query runs once before recorded measurements\n");
        report.append("- Recorded runs per scenario: `").append(MEASUREMENT_RUNS).append("`\n\n");
        report.append("| Scenario | median ms | min ms | max ms |\n");
        report.append("| --- | ---: | ---: | ---: |\n");
        for (MeasurementResult measurement : measurements) {
            report.append("| ")
                    .append(measurement.label())
                    .append(" | ")
                    .append(formatMillis(measurement.medianMillis()))
                    .append(" | ")
                    .append(formatMillis(measurement.minMillis()))
                    .append(" | ")
                    .append(formatMillis(measurement.maxMillis()))
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
    }
}
