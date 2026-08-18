package com.wuho.erroralert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

@Getter
@Entity
@Table(name = "errors")
public class ErrorEvent {

    public static final int MESSAGE_MAX_LENGTH = 1000;

    private static final String MESSAGE_TRUNCATION_SUFFIX = "...";
    private static final int MESSAGE_TRUNCATION_PREFIX_LENGTH =
            MESSAGE_MAX_LENGTH - MESSAGE_TRUNCATION_SUFFIX.length();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Convert(converter = ErrorCodeConverter.class)
    @Column(name = "error_code", nullable = false, length = 100)
    private ErrorCode errorCode;

    @Column(nullable = false, length = MESSAGE_MAX_LENGTH)
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected ErrorEvent() {
    }

    private ErrorEvent(Project project, ErrorCode errorCode, String message, Instant occurredAt, Instant receivedAt) {
        this.project = Objects.requireNonNull(project, "project must not be null");
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.message = normalizeMessage(message);
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    }

    public static ErrorEvent create(
            Project project,
            ErrorCode errorCode,
            String message,
            Instant occurredAt,
            Instant receivedAt
    ) {
        return new ErrorEvent(project, errorCode, message, occurredAt, receivedAt);
    }

    private static String normalizeMessage(String message) {
        String requiredMessage = Objects.requireNonNull(message, "message must not be null");
        if (requiredMessage.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (requiredMessage.length() <= MESSAGE_MAX_LENGTH) {
            return requiredMessage;
        }
        // String.length()는 UTF-16 코드 유닛 기준입니다.
        // 이모지 지원 정책이 생기면 codePoint 기준 자르기를 재검토합니다.
        return requiredMessage.substring(0, MESSAGE_TRUNCATION_PREFIX_LENGTH) + MESSAGE_TRUNCATION_SUFFIX;
    }
}
