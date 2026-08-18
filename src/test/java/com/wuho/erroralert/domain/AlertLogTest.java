package com.wuho.erroralert.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class AlertLogTest {

    private AlertLog pendingAlertLog() {
        return AlertLog.create(
            new Project("payment-service"),
            ErrorCode.PAYMENT_PG_TIMEOUT,
            Instant.parse("2026-08-12T14:05:00Z"),
            27, 10, AlertLogStatus.PENDING);
    }

    @Test
    void marksSentWithRetryCountAndSentAt() {
        AlertLog alertLog = pendingAlertLog();

        alertLog.markSent(2);

        assertEquals(AlertLogStatus.SENT, alertLog.getStatus());
        assertEquals(2, alertLog.getRetryCount());
        assertNotNull(alertLog.getSentAt());
    }

    @Test
    void marksFailedWithRetryCountAndNoSentAt() {
        AlertLog alertLog = pendingAlertLog();

        alertLog.markFailed(3);

        assertEquals(AlertLogStatus.FAILED, alertLog.getStatus());
        assertEquals(3, alertLog.getRetryCount());
        assertNull(alertLog.getSentAt());
    }
}
