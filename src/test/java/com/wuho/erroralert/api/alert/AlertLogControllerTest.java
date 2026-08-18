package com.wuho.erroralert.api.alert;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wuho.erroralert.api.common.GlobalExceptionHandler;
import com.wuho.erroralert.api.common.auth.TemporaryAuthHeaderVerifier;
import com.wuho.erroralert.service.AlertLogQueryService;
import com.wuho.erroralert.service.AlertLogResult;
import com.wuho.erroralert.service.AlertLogsResult;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AlertLogControllerTest {

    @Mock
    private AlertLogQueryService alertLogQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AlertLogController(new TemporaryAuthHeaderVerifier(), alertLogQueryService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void findAlertsReturns200WithPageAndContent() throws Exception {
        when(alertLogQueryService.findAlerts(any())).thenReturn(new AlertLogsResult(0, 20, 1,
            List.of(new AlertLogResult(7L, "PAYMENT.PG_TIMEOUT",
                Instant.parse("2026-08-12T04:55:00Z"), 27, 10, "SENT", 0,
                Instant.parse("2026-08-12T04:55:12Z"), LocalDateTime.now()))));

        mockMvc.perform(get("/api/v1/alerts?projectId=1")
                .header("X-User-Id", "3")
                .header("X-Role", "OPERATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].alertId").value(7))
            .andExpect(jsonPath("$.data.content[0].status").value("SENT"));
    }

    @Test
    void invalidStatusReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/alerts?projectId=1&status=UNKNOWN")
                .header("X-User-Id", "3")
                .header("X-Role", "OPERATOR"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void missingProjectIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/alerts")
                .header("X-User-Id", "3")
                .header("X-Role", "OPERATOR"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void missingAuthHeadersReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/alerts?projectId=1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }
}
