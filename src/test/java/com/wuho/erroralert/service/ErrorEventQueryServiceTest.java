package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.config.JpaAuditingConfig;
import com.wuho.erroralert.domain.ErrorCode;
import com.wuho.erroralert.domain.ErrorEvent;
import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.repository.ErrorEventRepository;
import com.wuho.erroralert.repository.ProjectRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ErrorEventQueryServiceTest {

    @Autowired
    private ErrorEventRepository errorEventRepository;

    @Autowired
    private ProjectRepository projectRepository;

    private ErrorEventQueryService errorEventQueryService;

    @BeforeEach
    void setUp() {
        errorEventQueryService = new ErrorEventQueryService(errorEventRepository, projectRepository);
    }

    @Test
    void findsProjectEventsNewestFirstWithPaging() {
        Project project = saveProject("payment-service");
        Project otherProject = saveProject("order-service");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "old timeout", "2026-08-04T12:00:00Z");
        saveEvent(project, ErrorCode.PAYMENT_PG_SERVER_ERROR, "middle server error", "2026-08-04T12:10:00Z");
        saveEvent(project, ErrorCode.PAYMENT_WEBHOOK_PROCESSING_FAILED, "new webhook error", "2026-08-04T12:20:00Z");
        saveEvent(otherProject, ErrorCode.PAYMENT_PG_TIMEOUT, "other project latest", "2026-08-04T12:30:00Z");
        errorEventRepository.flush();

        RecentErrorEventsResult result = errorEventQueryService.findRecent(
                new FindRecentErrorEventsCommand(project.getId(), null, null, null, 0, 2));

        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.content())
                .extracting(RecentErrorEventResult::message)
                .containsExactly("new webhook error", "middle server error");
    }

    @Test
    void filtersByErrorCodeAndOccurredAtRangeInclusively() {
        Project project = saveProject("payment-service");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "before range", "2026-08-04T11:59:59Z");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "range start", "2026-08-04T12:00:00Z");
        saveEvent(project, ErrorCode.PAYMENT_PG_SERVER_ERROR, "different code", "2026-08-04T12:10:00Z");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "range end", "2026-08-04T12:20:00Z");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "after range", "2026-08-04T12:20:01Z");
        errorEventRepository.flush();

        RecentErrorEventsResult result = errorEventQueryService.findRecent(
                new FindRecentErrorEventsCommand(
                        project.getId(),
                        ErrorCode.PAYMENT_PG_TIMEOUT,
                        Instant.parse("2026-08-04T12:00:00Z"),
                        Instant.parse("2026-08-04T12:20:00Z"),
                        0,
                        10
                ));

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content())
                .extracting(RecentErrorEventResult::message)
                .containsExactly("range end", "range start");
    }

    @Test
    void findsTrendWithZeroFilled60SecondBuckets() {
        Project project = saveProject("payment-service");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "first timeout", "2026-08-04T12:00:15Z");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "second timeout", "2026-08-04T12:00:45Z");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "third timeout", "2026-08-04T12:02:00Z");
        errorEventRepository.flush();

        ErrorTrendResult result = errorEventQueryService.findTrend(new FindErrorTrendCommand(
                project.getId(),
                null,
                Instant.parse("2026-08-04T12:00:10Z"),
                Instant.parse("2026-08-04T12:02:30Z"),
                "60s"
        ));

        assertThat(result.projectId()).isEqualTo(project.getId());
        assertThat(result.errorCode()).isNull();
        assertThat(result.interval()).isEqualTo("60s");
        assertThat(result.points())
                .extracting(ErrorTrendPointResult::windowStartedAt, ErrorTrendPointResult::count)
                .containsExactly(
                        tuple(Instant.parse("2026-08-04T12:00:00Z"), 2L),
                        tuple(Instant.parse("2026-08-04T12:01:00Z"), 0L),
                        tuple(Instant.parse("2026-08-04T12:02:00Z"), 1L)
                );
    }

    @Test
    void trendFiltersByProjectAndErrorCode() {
        Project project = saveProject("payment-service");
        Project otherProject = saveProject("order-service");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "target timeout", "2026-08-04T12:00:15Z");
        saveEvent(project, ErrorCode.PAYMENT_PG_SERVER_ERROR, "different code", "2026-08-04T12:00:30Z");
        saveEvent(otherProject, ErrorCode.PAYMENT_PG_TIMEOUT, "other project timeout", "2026-08-04T12:00:45Z");
        errorEventRepository.flush();

        ErrorTrendResult result = errorEventQueryService.findTrend(new FindErrorTrendCommand(
                project.getId(),
                ErrorCode.PAYMENT_PG_TIMEOUT,
                Instant.parse("2026-08-04T12:00:00Z"),
                Instant.parse("2026-08-04T12:01:00Z"),
                "60s"
        ));

        assertThat(result.errorCode()).isEqualTo("PAYMENT.PG_TIMEOUT");
        assertThat(result.points())
                .extracting(ErrorTrendPointResult::windowStartedAt, ErrorTrendPointResult::count)
                .containsExactly(
                        tuple(Instant.parse("2026-08-04T12:00:00Z"), 1L),
                        tuple(Instant.parse("2026-08-04T12:01:00Z"), 0L)
                );
    }

    @Test
    void trendIncludesFromAndToBoundaries() {
        Project project = saveProject("payment-service");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "before range", "2026-08-04T11:59:59Z");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "range start", "2026-08-04T12:00:00Z");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "range end", "2026-08-04T12:01:00Z");
        saveEvent(project, ErrorCode.PAYMENT_PG_TIMEOUT, "after range", "2026-08-04T12:01:01Z");
        errorEventRepository.flush();

        ErrorTrendResult result = errorEventQueryService.findTrend(new FindErrorTrendCommand(
                project.getId(),
                ErrorCode.PAYMENT_PG_TIMEOUT,
                Instant.parse("2026-08-04T12:00:00Z"),
                Instant.parse("2026-08-04T12:01:00Z"),
                "60s"
        ));

        assertThat(result.points())
                .extracting(ErrorTrendPointResult::windowStartedAt, ErrorTrendPointResult::count)
                .containsExactly(
                        tuple(Instant.parse("2026-08-04T12:00:00Z"), 1L),
                        tuple(Instant.parse("2026-08-04T12:01:00Z"), 1L)
                );
    }

    @Test
    void trendReturnsZeroFilledPointsForExistingProjectWithoutEvents() {
        Project project = saveProject("payment-service");

        ErrorTrendResult result = errorEventQueryService.findTrend(new FindErrorTrendCommand(
                project.getId(),
                null,
                Instant.parse("2026-08-04T12:00:00Z"),
                Instant.parse("2026-08-04T12:01:00Z"),
                "60s"
        ));

        assertThat(result.points())
                .extracting(ErrorTrendPointResult::windowStartedAt, ErrorTrendPointResult::count)
                .containsExactly(
                        tuple(Instant.parse("2026-08-04T12:00:00Z"), 0L),
                        tuple(Instant.parse("2026-08-04T12:01:00Z"), 0L)
                );
    }

    @Test
    void returnsEmptyPageForExistingProjectWithoutEvents() {
        Project project = saveProject("payment-service");

        RecentErrorEventsResult result = errorEventQueryService.findRecent(
                new FindRecentErrorEventsCommand(project.getId(), null, null, null, 0, 20));

        assertThat(result.totalElements()).isZero();
        assertThat(result.content()).isEmpty();
    }

    @Test
    void missingProjectThrowsProjectNotFound() {
        assertThatThrownBy(() -> errorEventQueryService.findRecent(
                new FindRecentErrorEventsCommand(999L, null, null, null, 0, 20)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.PROJECT_NOT_FOUND));

        assertThatThrownBy(() -> errorEventQueryService.findTrend(
                new FindErrorTrendCommand(
                        999L,
                        null,
                        Instant.parse("2026-08-04T12:00:00Z"),
                        Instant.parse("2026-08-04T12:01:00Z"),
                        "60s"
                )))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void commandRejectsInvalidQueryCondition() {
        assertThatThrownBy(() -> FindRecentErrorEventsCommand.of(
                1L,
                "PAYMENT.UNKNOWN",
                null,
                null,
                0,
                20
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> new FindRecentErrorEventsCommand(
                1L,
                null,
                Instant.parse("2026-08-04T13:00:00Z"),
                Instant.parse("2026-08-04T12:00:00Z"),
                0,
                20
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> new FindRecentErrorEventsCommand(1L, null, null, null, -1, 20))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> new FindRecentErrorEventsCommand(1L, null, null, null, 0, 0))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));
    }

    @Test
    void trendCommandRejectsInvalidQueryCondition() {
        assertThatThrownBy(() -> FindErrorTrendCommand.of(
                1L,
                "PAYMENT.UNKNOWN",
                Instant.parse("2026-08-04T12:00:00Z"),
                Instant.parse("2026-08-04T12:01:00Z"),
                "60s"
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> new FindErrorTrendCommand(
                1L,
                null,
                null,
                Instant.parse("2026-08-04T12:01:00Z"),
                "60s"
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> new FindErrorTrendCommand(
                1L,
                null,
                Instant.parse("2026-08-04T12:01:01Z"),
                Instant.parse("2026-08-04T12:01:00Z"),
                "60s"
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> new FindErrorTrendCommand(
                1L,
                null,
                Instant.parse("2026-08-04T12:00:00Z"),
                Instant.parse("2026-08-04T12:01:00Z"),
                "5m"
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> new FindErrorTrendCommand(
                1L,
                null,
                Instant.parse("2026-08-04T12:00:00Z"),
                Instant.parse("2026-08-05T12:00:01Z"),
                "60s"
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));
    }

    @Test
    void commandBuildsFixedOccurredAtDescPageable() {
        FindRecentErrorEventsCommand command = new FindRecentErrorEventsCommand(1L, null, null, null, 2, 50);

        Pageable pageable = command.toPageable();

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "occurredAt"));
    }

    private Project saveProject(String name) {
        return projectRepository.saveAndFlush(new Project(name));
    }

    private void saveEvent(Project project, ErrorCode errorCode, String message, String occurredAt) {
        Instant occurred = Instant.parse(occurredAt);
        errorEventRepository.save(ErrorEvent.create(project, errorCode, message, occurred, occurred.plusSeconds(1)));
    }
}
