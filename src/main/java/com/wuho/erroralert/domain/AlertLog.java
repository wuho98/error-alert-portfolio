package com.wuho.erroralert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 같은 프로젝트와 오류 코드의 한 감지 구간에는 하나의 알림 기록만 존재한다.
 */
@Entity
@Table(
        name = "alert_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_alert_log_project_error_window",
                columnNames = {"project_id", "error_code", "window_started_at"}))
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AlertLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Convert(converter = ErrorCodeConverter.class)
    @Column(name = "error_code", nullable = false, length = 100)
    private ErrorCode errorCode;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "observed_count", nullable = false)
    private int observedCount;

    @Column(nullable = false)
    private int threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertLogStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private AlertLog(
            Project project,
            ErrorCode errorCode,
            Instant windowStartedAt,
            int observedCount,
            int threshold,
            AlertLogStatus status
    ) {
        this.project = Objects.requireNonNull(project, "project must not be null");
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.windowStartedAt = Objects.requireNonNull(windowStartedAt, "windowStartedAt must not be null");
        if (observedCount < 0) {
            throw new IllegalArgumentException("observedCount must not be negative");
        }
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        this.observedCount = observedCount;
        this.threshold = threshold;
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public static AlertLog create(
            Project project,
            ErrorCode errorCode,
            Instant windowStartedAt,
            int observedCount,
            int threshold,
            AlertLogStatus status
    ) {
        return new AlertLog(project, errorCode, windowStartedAt, observedCount, threshold, status);
    }
    public void markSent(int retryCount) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        this.status = AlertLogStatus.SENT;
        this.retryCount = retryCount;
        this.sentAt = Instant.now();
    }

    public void markFailed(int retryCount) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        this.status = AlertLogStatus.FAILED;
        this.retryCount = retryCount;
    }
}
