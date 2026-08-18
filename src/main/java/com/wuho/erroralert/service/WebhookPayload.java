package com.wuho.erroralert.service;

public record WebhookPayload(
    Long projectId,
    String errorCode,
    String windowStartedAt,
    long observedCount,
    long threshold
) {
}
