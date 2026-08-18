package com.wuho.erroralert.service;

public record AlertSummaryInput(
    Long projectId,
    String errorCode,
    String windowStartedAt,
    long observedCount,
    long threshold,
    String status
) {
}
