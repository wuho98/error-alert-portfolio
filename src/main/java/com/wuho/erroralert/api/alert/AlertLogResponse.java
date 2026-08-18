package com.wuho.erroralert.api.alert;

import com.wuho.erroralert.service.AlertLogResult;
import java.time.Instant;
import java.time.LocalDateTime;

public record AlertLogResponse(
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

    public static AlertLogResponse from(AlertLogResult result) {
        return new AlertLogResponse(
                result.alertId(),
                result.errorCode(),
                result.windowStartedAt(),
                result.observedCount(),
                result.threshold(),
                result.status(),
                result.retryCount(),
                result.sentAt(),
                result.createdAt()
        );
    }
}
