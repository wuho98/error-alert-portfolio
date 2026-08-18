package com.wuho.erroralert.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.api.common.auth.TemporaryAuthHeaderVerifier;
import com.wuho.erroralert.domain.ErrorCode;
import com.wuho.erroralert.service.ErrorEventQueryService;
import com.wuho.erroralert.service.ErrorEventReceiveService;
import com.wuho.erroralert.service.ErrorTrendPointResult;
import com.wuho.erroralert.service.ErrorTrendResult;
import com.wuho.erroralert.service.FindErrorTrendCommand;
import com.wuho.erroralert.service.FindRecentErrorEventsCommand;
import com.wuho.erroralert.service.RecentErrorEventResult;
import com.wuho.erroralert.service.RecentErrorEventsResult;
import com.wuho.erroralert.service.ReceiveErrorEventResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ErrorEventController.class)
@Import(TemporaryAuthHeaderVerifier.class)
class ErrorEventControllerTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-04T12:34:45.812Z");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-04T12:34:45Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ErrorEventReceiveService errorEventReceiveService;

    @MockBean
    private ErrorEventQueryService errorEventQueryService;

    @BeforeEach
    void setUp() {
        when(errorEventQueryService.findRecent(any())).thenReturn(recentResult(0, 20));
        when(errorEventQueryService.findTrend(any())).thenReturn(trendResult());
    }

    @Test
    void operatorCanFindRecentErrorsWithFilters() throws Exception {
        when(errorEventQueryService.findRecent(any())).thenReturn(recentResult(1, 10));

        mockMvc.perform(get("/api/v1/errors")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "1")
                        .param("errorCode", "PAYMENT.PG_TIMEOUT")
                        .param("from", "2026-08-04T12:00:00Z")
                        .param("to", "2026-08-04T13:00:00Z")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].eventId").value(1024))
                .andExpect(jsonPath("$.data.content[0].errorCode").value("PAYMENT.PG_TIMEOUT"))
                .andExpect(jsonPath("$.data.content[0].message").value("PG approval request timed out after 5000ms"))
                .andExpect(jsonPath("$.data.content[0].occurredAt").value("2026-08-04T12:34:45Z"))
                .andExpect(jsonPath("$.data.content[0].receivedAt").value("2026-08-04T12:34:45.812Z"));

        ArgumentCaptor<FindRecentErrorEventsCommand> commandCaptor =
                ArgumentCaptor.forClass(FindRecentErrorEventsCommand.class);
        verify(errorEventQueryService).findRecent(commandCaptor.capture());
        FindRecentErrorEventsCommand command = commandCaptor.getValue();
        assertThat(command.projectId()).isEqualTo(1L);
        assertThat(command.errorCode()).isEqualTo(ErrorCode.PAYMENT_PG_TIMEOUT);
        assertThat(command.from()).isEqualTo(Instant.parse("2026-08-04T12:00:00Z"));
        assertThat(command.to()).isEqualTo(Instant.parse("2026-08-04T13:00:00Z"));
        assertThat(command.page()).isEqualTo(1);
        assertThat(command.size()).isEqualTo(10);
    }

    @Test
    void adminCanFindRecentErrorsWithDefaultPaging() throws Exception {
        mockMvc.perform(get("/api/v1/errors")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN")
                        .param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));

        ArgumentCaptor<FindRecentErrorEventsCommand> commandCaptor =
                ArgumentCaptor.forClass(FindRecentErrorEventsCommand.class);
        verify(errorEventQueryService).findRecent(commandCaptor.capture());
        FindRecentErrorEventsCommand command = commandCaptor.getValue();
        assertThat(command.page()).isEqualTo(0);
        assertThat(command.size()).isEqualTo(20);
        assertThat(command.errorCode()).isNull();
    }

    @Test
    void recentErrorsMissingAuthHeadersReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/errors")
                        .param("projectId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void recentErrorsUnsupportedRoleReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/errors")
                        .header("X-User-Id", "3")
                        .header("X-Role", "VIEWER")
                        .param("projectId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void recentErrorsMissingProjectIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/errors")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void recentErrorsUnsupportedErrorCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/errors")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "1")
                        .param("errorCode", "PAYMENT.UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void recentErrorsSizeOverMaxReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/errors")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "1")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void recentErrorsMalformedPageReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/errors")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "1")
                        .param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void recentErrorsFromAfterToReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/errors")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "1")
                        .param("from", "2026-08-04T13:00:00Z")
                        .param("to", "2026-08-04T12:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void recentErrorsMissingProjectReturns404() throws Exception {
        when(errorEventQueryService.findRecent(any()))
                .thenThrow(new ApiException(ApiErrorCode.PROJECT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/errors")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    void operatorCanFindErrorTrendWithFilters() throws Exception {
        mockMvc.perform(get("/api/v1/errors/trend")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "1")
                        .param("errorCode", "PAYMENT.PG_TIMEOUT")
                        .param("from", "2026-08-04T12:00:00Z")
                        .param("to", "2026-08-04T12:02:00Z")
                        .param("interval", "60s"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(1))
                .andExpect(jsonPath("$.data.errorCode").value("PAYMENT.PG_TIMEOUT"))
                .andExpect(jsonPath("$.data.interval").value("60s"))
                .andExpect(jsonPath("$.data.points[0].windowStartedAt").value("2026-08-04T12:00:00Z"))
                .andExpect(jsonPath("$.data.points[0].count").value(2))
                .andExpect(jsonPath("$.data.points[1].windowStartedAt").value("2026-08-04T12:01:00Z"))
                .andExpect(jsonPath("$.data.points[1].count").value(0))
                .andExpect(jsonPath("$.data.points[2].windowStartedAt").value("2026-08-04T12:02:00Z"))
                .andExpect(jsonPath("$.data.points[2].count").value(1));

        ArgumentCaptor<FindErrorTrendCommand> commandCaptor =
                ArgumentCaptor.forClass(FindErrorTrendCommand.class);
        verify(errorEventQueryService).findTrend(commandCaptor.capture());
        FindErrorTrendCommand command = commandCaptor.getValue();
        assertThat(command.projectId()).isEqualTo(1L);
        assertThat(command.errorCode()).isEqualTo(ErrorCode.PAYMENT_PG_TIMEOUT);
        assertThat(command.from()).isEqualTo(Instant.parse("2026-08-04T12:00:00Z"));
        assertThat(command.to()).isEqualTo(Instant.parse("2026-08-04T12:02:00Z"));
        assertThat(command.interval()).isEqualTo("60s");
    }

    @Test
    void adminCanFindErrorTrendWithDefaultInterval() throws Exception {
        mockMvc.perform(get("/api/v1/errors/trend")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN")
                        .param("projectId", "1")
                        .param("from", "2026-08-04T12:00:00Z")
                        .param("to", "2026-08-04T12:02:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interval").value("60s"));

        ArgumentCaptor<FindErrorTrendCommand> commandCaptor =
                ArgumentCaptor.forClass(FindErrorTrendCommand.class);
        verify(errorEventQueryService).findTrend(commandCaptor.capture());
        assertThat(commandCaptor.getValue().interval()).isEqualTo("60s");
    }

    @Test
    void errorTrendMissingAuthHeadersReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/errors/trend")
                        .param("projectId", "1")
                        .param("from", "2026-08-04T12:00:00Z")
                        .param("to", "2026-08-04T12:02:00Z"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void errorTrendUnsupportedRoleReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/errors/trend")
                        .header("X-User-Id", "3")
                        .header("X-Role", "VIEWER")
                        .param("projectId", "1")
                        .param("from", "2026-08-04T12:00:00Z")
                        .param("to", "2026-08-04T12:02:00Z"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void errorTrendMissingRequiredConditionReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/errors/trend")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "1")
                        .param("from", "2026-08-04T12:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void errorTrendMalformedFromReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/errors/trend")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "1")
                        .param("from", "not-a-time")
                        .param("to", "2026-08-04T12:02:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void errorTrendUnsupportedErrorCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/errors/trend")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "1")
                        .param("errorCode", "PAYMENT.UNKNOWN")
                        .param("from", "2026-08-04T12:00:00Z")
                        .param("to", "2026-08-04T12:02:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void errorTrendUnsupportedIntervalReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/errors/trend")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "1")
                        .param("from", "2026-08-04T12:00:00Z")
                        .param("to", "2026-08-04T12:02:00Z")
                        .param("interval", "5m"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void errorTrendFromAfterToReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/errors/trend")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "1")
                        .param("from", "2026-08-04T12:02:01Z")
                        .param("to", "2026-08-04T12:02:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void errorTrendRangeOver24HoursReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/errors/trend")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "1")
                        .param("from", "2026-08-04T12:00:00Z")
                        .param("to", "2026-08-05T12:00:01Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(errorEventQueryService);
    }

    @Test
    void errorTrendMissingProjectReturns404() throws Exception {
        when(errorEventQueryService.findTrend(any()))
                .thenThrow(new ApiException(ApiErrorCode.PROJECT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/errors/trend")
                        .header("X-User-Id", "3")
                        .header("X-Role", "OPERATOR")
                        .param("projectId", "999")
                        .param("from", "2026-08-04T12:00:00Z")
                        .param("to", "2026-08-04T12:02:00Z"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    void validRequestReturns201() throws Exception {
        when(errorEventReceiveService.receive(eq("pk_live_valid"), any()))
                .thenReturn(new ReceiveErrorEventResult(1024L, 1L, "PAYMENT.PG_TIMEOUT", RECEIVED_AT));

        mockMvc.perform(post("/api/v1/errors")
                        .header("X-Api-Key", "pk_live_valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.eventId").value(1024))
                .andExpect(jsonPath("$.data.projectId").value(1))
                .andExpect(jsonPath("$.data.errorCode").value("PAYMENT.PG_TIMEOUT"))
                .andExpect(jsonPath("$.data.receivedAt").value("2026-08-04T12:34:45.812Z"));
    }

    @Test
    void missingApiKeyReturns401() throws Exception {
        when(errorEventReceiveService.receive(isNull(), any()))
                .thenThrow(new ApiException(ApiErrorCode.UNAUTHORIZED));

        mockMvc.perform(post("/api/v1/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void unsupportedErrorCodeReturns400WithC002() throws Exception {
        mockMvc.perform(post("/api/v1/errors")
                        .header("X-Api-Key", "pk_live_valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "errorCode": "PAYMENT.UNKNOWN",
                                  "message": "PG approval request timed out after 5000ms",
                                  "occurredAt": "2026-08-04T12:34:45Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));

        verifyNoInteractions(errorEventReceiveService);
    }

    @Test
    void malformedOccurredAtReturns400WithC001() throws Exception {
        mockMvc.perform(post("/api/v1/errors")
                        .header("X-Api-Key", "pk_live_valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "errorCode": "PAYMENT.PG_TIMEOUT",
                                  "message": "PG approval request timed out after 5000ms",
                                  "occurredAt": "not-a-time"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(errorEventReceiveService);
    }

    @Test
    void storageFailureReturns503() throws Exception {
        when(errorEventReceiveService.receive(eq("pk_live_valid"), any()))
                .thenThrow(new DataAccessResourceFailureException("DB unavailable"));

        mockMvc.perform(post("/api/v1/errors")
                        .header("X-Api-Key", "pk_live_valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("C004"));
    }

    private String validBody() {
        return """
                {
                  "errorCode": "PAYMENT.PG_TIMEOUT",
                  "message": "PG approval request timed out after 5000ms",
                  "occurredAt": "2026-08-04T12:34:45Z"
                }
                """;
    }

    private RecentErrorEventsResult recentResult(int page, int size) {
        return new RecentErrorEventsResult(
                page,
                size,
                1,
                List.of(new RecentErrorEventResult(
                        1024L,
                        "PAYMENT.PG_TIMEOUT",
                        "PG approval request timed out after 5000ms",
                        OCCURRED_AT,
                        RECEIVED_AT
                ))
        );
    }

    private ErrorTrendResult trendResult() {
        return new ErrorTrendResult(
                1L,
                "PAYMENT.PG_TIMEOUT",
                "60s",
                List.of(
                        new ErrorTrendPointResult(Instant.parse("2026-08-04T12:00:00Z"), 2),
                        new ErrorTrendPointResult(Instant.parse("2026-08-04T12:01:00Z"), 0),
                        new ErrorTrendPointResult(Instant.parse("2026-08-04T12:02:00Z"), 1)
                )
        );
    }
}
