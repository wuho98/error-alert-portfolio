package com.wuho.erroralert.service;

import com.wuho.erroralert.domain.ErrorEvent;
import java.time.Instant;

public record RecentErrorEventResult(
        Long eventId,
        String errorCode,
        String message,
        Instant occurredAt,
        Instant receivedAt
) {

    public static RecentErrorEventResult from(ErrorEvent event) {
        return new RecentErrorEventResult(
                event.getId(),
                event.getErrorCode().getCode(),
                event.getMessage(),
                event.getOccurredAt(),
                event.getReceivedAt()
        );
    }
}
