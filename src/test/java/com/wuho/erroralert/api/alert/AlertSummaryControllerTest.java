package com.wuho.erroralert.api.alert;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wuho.erroralert.api.common.GlobalExceptionHandler;
import com.wuho.erroralert.api.common.auth.TemporaryAuthHeaderVerifier;
import com.wuho.erroralert.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AlertSummaryControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AlertSummaryController(
                new TemporaryAuthHeaderVerifier(),
                new FakeAlertSummaryProvider(),
                new AiSummaryService(new MockAiClient(), new SensitiveValueMasker())))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void summarizeReturns200WithMockSummary() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/1/summary")
                .header("X-User-Id", "3")
                .header("X-Role", "OPERATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alertId").value(1))
            .andExpect(jsonPath("$.data.situation").exists())
            .andExpect(jsonPath("$.data.checkFirst").isArray())
            .andExpect(jsonPath("$.data.checkFirst.length()").value(2))
            .andExpect(jsonPath("$.data.needMoreInfo").isArray());

    }

    @Test
    void unknownAlertReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/999/summary")
                .header("X-User-Id", "3")
                .header("X-Role", "OPERATOR"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("AL001"));
    }

    @Test
    void missingAuthHeadersReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/1/summary"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }
    static class FakeAlertSummaryProvider implements AlertSummaryProvider {
        @Override
        public java.util.Optional<AlertSummaryInput> findAlert(Long alertId) {
            if (alertId == 1L) {
                return java.util.Optional.of(new AlertSummaryInput(
                    1L, "PAYMENT.PG_TIMEOUT", "2026-08-10T14:05:00Z", 27, 10, "PENDING"));
            }
            return java.util.Optional.empty();
        }
    }
}
