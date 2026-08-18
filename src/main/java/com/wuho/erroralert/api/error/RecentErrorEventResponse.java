package com.wuho.erroralert.api.error;

import com.wuho.erroralert.service.RecentErrorEventResult;
import java.time.Instant;

public record RecentErrorEventResponse(
        Long eventId,
        String errorCode,
        String message,
        Instant occurredAt,
        Instant receivedAt
) {

    public static RecentErrorEventResponse from(RecentErrorEventResult result) {
        return new RecentErrorEventResponse(
                result.eventId(),
                result.errorCode(),
                result.message(),
                result.occurredAt(),
                result.receivedAt()
        );
    }
}
