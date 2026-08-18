package com.wuho.erroralert.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wuho.erroralert.api.dto.ProjectApiKeyCreateResponse;
import com.wuho.erroralert.api.dto.ProjectCreateResponse;
import com.wuho.erroralert.domain.ErrorEvent;
import com.wuho.erroralert.repository.ErrorEventRepository;
import com.wuho.erroralert.repository.ProjectApiKeyRepository;
import com.wuho.erroralert.repository.ProjectRepository;
import com.wuho.erroralert.repository.ProjectSettingRepository;
import com.wuho.erroralert.service.ErrorRateCounter;
import com.wuho.erroralert.service.ProjectApiKeyService;
import com.wuho.erroralert.service.ProjectService;
import com.wuho.erroralert.support.SqlCaptureStatementInspector;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.wuho.erroralert.support.SqlCaptureStatementInspector"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorEventControllerIntegrationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-04T12:34:45Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-04T12:34:45.812Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectApiKeyService projectApiKeyService;

    @Autowired
    private ErrorEventRepository errorEventRepository;

    @Autowired
    private ProjectApiKeyRepository projectApiKeyRepository;

    @Autowired
    private ProjectSettingRepository projectSettingRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockBean
    private ErrorRateCounter errorRateCounter;

    @MockBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        errorEventRepository.deleteAll();
        projectApiKeyRepository.deleteAll();
        projectSettingRepository.deleteAll();
        projectRepository.deleteAll();

        when(clock.instant()).thenReturn(RECEIVED_AT);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(errorRateCounter.increment(anyLong(), anyString(), any(Instant.class))).thenReturn(1L);
    }

    @Test
    void validApiKeyRequestStoresErrorEvent() throws Exception {
        ProjectApiKeyCreateResponse apiKey = issueApiKey();

        mockMvc.perform(post("/api/v1/errors")
                        .header("X-Api-Key", apiKey.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Request(
                                "PAYMENT.PG_TIMEOUT",
                                "PG approval request timed out after 5000ms",
                                OCCURRED_AT))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectId").value(apiKey.projectId()))
                .andExpect(jsonPath("$.data.errorCode").value("PAYMENT.PG_TIMEOUT"))
                .andExpect(jsonPath("$.data.receivedAt").value("2026-08-04T12:34:45.812Z"));

        List<ErrorEvent> savedEvents = errorEventRepository.findAll();
        assertThat(savedEvents).hasSize(1);
        ErrorEvent savedEvent = savedEvents.get(0);
        assertThat(savedEvent.getProject().getId()).isEqualTo(apiKey.projectId());
        assertThat(savedEvent.getErrorCode().getCode()).isEqualTo("PAYMENT.PG_TIMEOUT");
        assertThat(savedEvent.getMessage()).isEqualTo("PG approval request timed out after 5000ms");
        assertThat(savedEvent.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(savedEvent.getReceivedAt()).isEqualTo(RECEIVED_AT);
    }

    @Test
    void invalidApiKeyReturns401AndDoesNotStoreErrorEvent() throws Exception {
        mockMvc.perform(post("/api/v1/errors")
                        .header("X-Api-Key", "pk_live_invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Request(
                                "PAYMENT.PG_TIMEOUT",
                                "PG approval request timed out after 5000ms",
                                OCCURRED_AT))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));

        assertThat(errorEventRepository.count()).isZero();
    }

    @Test
    void messageOver1000CharactersIsAcceptedAndTruncated() throws Exception {
        ProjectApiKeyCreateResponse apiKey = issueApiKey();
        String longMessage = "a".repeat(ErrorEvent.MESSAGE_MAX_LENGTH + 1);

        mockMvc.perform(post("/api/v1/errors")
                        .header("X-Api-Key", apiKey.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Request(
                                "PAYMENT.PG_TIMEOUT",
                                longMessage,
                                OCCURRED_AT))))
                .andExpect(status().isCreated());

        ErrorEvent savedEvent = errorEventRepository.findAll().get(0);
        assertThat(savedEvent.getMessage())
                .hasSize(ErrorEvent.MESSAGE_MAX_LENGTH)
                .endsWith("...");
    }

    @Test
    void validApiKeyRequestDoesNotSelectProject() throws Exception {
        ProjectApiKeyCreateResponse apiKey = issueApiKey();
        Statistics statistics = statistics();
        statistics.clear();
        SqlCaptureStatementInspector.clear();

        mockMvc.perform(post("/api/v1/errors")
                        .header("X-Api-Key", apiKey.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Request(
                                "PAYMENT.PG_TIMEOUT",
                                "PG approval request timed out after 5000ms",
                                OCCURRED_AT))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectId").value(apiKey.projectId()));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(SqlCaptureStatementInspector.selectStatements())
                .hasSize(2)
                .anyMatch(sql -> accessesTable(sql, "project_api_key"))
                .anyMatch(sql -> accessesTable(sql, "project_setting"))
                .noneMatch(sql -> accessesTable(sql, "project"));
    }

    private ProjectApiKeyCreateResponse issueApiKey() {
        ProjectCreateResponse project = projectService.create("payment-service");
        return projectApiKeyService.create(project.projectId());
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private boolean accessesTable(String sql, String tableName) {
        String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        Pattern pattern = Pattern.compile("\\b(from|join)\\s+\"?" + Pattern.quote(tableName) + "\"?\\b");
        return pattern.matcher(normalized).find();
    }

    private record Request(String errorCode, String message, Instant occurredAt) {
    }
}
