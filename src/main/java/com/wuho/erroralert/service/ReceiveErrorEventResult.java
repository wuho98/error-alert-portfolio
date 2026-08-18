package com.wuho.erroralert.service;

import com.wuho.erroralert.domain.ErrorEvent;
import java.time.Instant;

public record ReceiveErrorEventResult(
        Long eventId,
        Long projectId,
        String errorCode,
        Instant receivedAt
) {

    public static ReceiveErrorEventResult from(ErrorEvent event) {
        return new ReceiveErrorEventResult(
                event.getId(),
                event.getProject().getId(),
                event.getErrorCode().getCode(),
                event.getReceivedAt()
        );
    }
}
