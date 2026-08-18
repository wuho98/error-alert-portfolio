package com.wuho.erroralert.service;

import com.wuho.erroralert.api.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiSummaryServiceTest {

    private final AlertSummaryInput input =
        new AlertSummaryInput(1L, "PAYMENT.PG_TIMEOUT", "2026-08-10T14:05:00", 27, 10, "PENDING");

    @Test
    void returnsSummaryFromAiClient() {
        AiSummaryService service = new AiSummaryService(new MockAiClient(), new SensitiveValueMasker());

        String result = service.summarize(input);

        assertTrue(result.startsWith("[Mock 요약]"));
    }

    @Test
    void aiFailureIsIsolatedAsApiException() {
        AiSummaryService service = new AiSummaryService(new FailingAiClient(), new SensitiveValueMasker());

        assertThrows(ApiException.class, () -> service.summarize(input));
    }

    @Test
    void masksSensitiveValuesBeforeSendingToAi() {
        RecordingAiClient recording = new RecordingAiClient();
        AiSummaryService service = new AiSummaryService(recording, new SensitiveValueMasker());
        AlertSummaryInput inputWithUrl =
            new AlertSummaryInput(1L, "PAYMENT.PG_TIMEOUT", "2026-08-10T14:05:00", 27, 10,
                "PENDING (webhook=https://hooks.example.com/pay)");

        service.summarize(inputWithUrl);

        assertTrue(recording.lastPrompt.contains("[MASKED]"));
        assertFalse(recording.lastPrompt.contains("https://"));
    }

    static class RecordingAiClient implements AiClient {
        String lastPrompt;

        @Override
        public String summarize(String prompt) {
            this.lastPrompt = prompt;
            return "ok";
        }
    }

    static class FailingAiClient implements AiClient {
        @Override
        public String summarize(String prompt) {
            throw new RuntimeException("ai server down");
        }
    }
}
