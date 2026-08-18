package com.wuho.erroralert.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wuho.erroralert.config.JpaAuditingConfig;
import com.wuho.erroralert.domain.AlertLog;
import com.wuho.erroralert.domain.AlertLogStatus;
import com.wuho.erroralert.domain.ErrorCode;
import com.wuho.erroralert.domain.Project;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class AlertLogRepositoryTest {

    private static final Instant WINDOW_STARTED_AT = Instant.parse("2026-08-11T01:23:00Z");

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AlertLogRepository alertLogRepository;

    @Test
    void savesAlertLog() {
        Project project = saveProject();

        AlertLog saved = alertLogRepository.saveAndFlush(alertLog(
                project, ErrorCode.PAYMENT_PG_TIMEOUT, WINDOW_STARTED_AT, AlertLogStatus.PENDING));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProject().getId()).isEqualTo(project.getId());
        assertThat(saved.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_PG_TIMEOUT);
        assertThat(saved.getWindowStartedAt()).isEqualTo(WINDOW_STARTED_AT);
        assertThat(saved.getObservedCount()).isEqualTo(10);
        assertThat(saved.getThreshold()).isEqualTo(10);
        assertThat(saved.getStatus()).isEqualTo(AlertLogStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getSentAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsDuplicateProjectErrorCodeAndWindow() {
        Project project = saveProject();
        alertLogRepository.saveAndFlush(alertLog(
                project, ErrorCode.PAYMENT_PG_TIMEOUT, WINDOW_STARTED_AT, AlertLogStatus.PENDING));

        assertThatThrownBy(() -> alertLogRepository.saveAndFlush(alertLog(
                project, ErrorCode.PAYMENT_PG_TIMEOUT, WINDOW_STARTED_AT, AlertLogStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesSkippedStatus() {
        Project project = saveProject();

        AlertLog saved = alertLogRepository.saveAndFlush(alertLog(
                project, ErrorCode.PAYMENT_PG_TIMEOUT, WINDOW_STARTED_AT, AlertLogStatus.SKIPPED));

        assertThat(saved.getStatus()).isEqualTo(AlertLogStatus.SKIPPED);
    }

    private Project saveProject() {
        return projectRepository.saveAndFlush(new Project("payment-service"));
    }

    private AlertLog alertLog(
            Project project,
            ErrorCode errorCode,
            Instant windowStartedAt,
            AlertLogStatus status
    ) {
        return AlertLog.create(project, errorCode, windowStartedAt, 10, 10, status);
    }
}
