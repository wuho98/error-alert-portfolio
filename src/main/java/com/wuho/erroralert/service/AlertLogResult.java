package com.wuho.erroralert.service;

import com.wuho.erroralert.domain.AlertLog;
import java.time.Instant;
import java.time.LocalDateTime;

public record AlertLogResult(
        Long alertId,
        String errorCode,
        Instant windowStartedAt,
        int observedCount,
        int threshold,
        String status,
        int retryCount,
        Instant sentAt,
        LocalDateTime createdAt
) {

    public static AlertLogResult from(AlertLog alertLog) {
        return new AlertLogResult(
                alertLog.getId(),
                alertLog.getErrorCode().getCode(),
                alertLog.getWindowStartedAt(),
                alertLog.getObservedCount(),
                alertLog.getThreshold(),
                alertLog.getStatus().name(),
                alertLog.getRetryCount(),
                alertLog.getSentAt(),
                alertLog.getCreatedAt()
        );
    }
}
