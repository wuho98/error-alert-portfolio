package com.wuho.erroralert.api.error;

import com.wuho.erroralert.service.ReceiveErrorEventResult;
import java.time.Instant;

public record ReceiveErrorEventResponse(
        Long eventId,
        Long projectId,
        String errorCode,
        Instant receivedAt
) {

    public static ReceiveErrorEventResponse from(ReceiveErrorEventResult result) {
        return new ReceiveErrorEventResponse(
                result.eventId(),
                result.projectId(),
                result.errorCode(),
                result.receivedAt()
        );
    }
}
