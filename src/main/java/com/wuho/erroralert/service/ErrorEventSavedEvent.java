package com.wuho.erroralert.service;

import java.time.Instant;

record ErrorEventSavedEvent(
    long projectId,
    String errorCode,
    Instant receivedAt
) {
}
