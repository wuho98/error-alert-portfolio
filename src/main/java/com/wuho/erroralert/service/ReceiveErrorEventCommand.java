package com.wuho.erroralert.service;

import java.time.Instant;

public record ReceiveErrorEventCommand(
        String errorCode,
        String message,
        Instant occurredAt
) {
}
