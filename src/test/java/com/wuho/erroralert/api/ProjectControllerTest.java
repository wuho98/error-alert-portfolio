package com.wuho.erroralert.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.api.common.auth.TemporaryAuthHeaderVerifier;
import com.wuho.erroralert.api.dto.ProjectApiKeyCreateResponse;
import com.wuho.erroralert.api.dto.ProjectCreateResponse;
import com.wuho.erroralert.api.dto.ProjectSettingUpdateResponse;
import com.wuho.erroralert.service.ProjectApiKeyService;
import com.wuho.erroralert.service.ProjectService;
import com.wuho.erroralert.service.ProjectSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
@Import(TemporaryAuthHeaderVerifier.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private ProjectApiKeyService projectApiKeyService;

    @MockBean
    private ProjectSettingService projectSettingService;

    @Test
    void adminRequestReturns201WithDefaultSettings() throws Exception {
        when(projectService.create("payment-service")).thenReturn(new ProjectCreateResponse(
                1L, "payment-service", 10, 60, 300, null, false, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/projects")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRequest("payment-service"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectId").value(1))
                .andExpect(jsonPath("$.data.name").value("payment-service"))
                .andExpect(jsonPath("$.data.threshold").value(10))
                .andExpect(jsonPath("$.data.windowSeconds").value(60))
                .andExpect(jsonPath("$.data.cooldownSeconds").value(300))
                .andExpect(jsonPath("$.data.webhookEnabled").value(false));
    }

    @Test
    void operatorRequestReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("X-User-Id", "1")
                        .header("X-Role", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRequest("payment-service"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    void missingHeadersReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRequest("payment-service"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void blankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    void adminRequestReturns201WithRawApiKey() throws Exception {
        when(projectApiKeyService.create(1L)).thenReturn(
                new ProjectApiKeyCreateResponse(1L, "pk_live_abc123", LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/projects/1/api-keys")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectId").value(1))
                .andExpect(jsonPath("$.data.apiKey").value("pk_live_abc123"));
    }

    @Test
    void apiKeyOperatorRequestReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/projects/1/api-keys")
                        .header("X-User-Id", "1")
                        .header("X-Role", "OPERATOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    void apiKeyMissingHeadersReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/projects/1/api-keys"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void apiKeyMissingProjectReturns404() throws Exception {
        when(projectApiKeyService.create(999L)).thenThrow(new ApiException(ApiErrorCode.PROJECT_NOT_FOUND));

        mockMvc.perform(post("/api/v1/projects/999/api-keys")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    void apiKeyActiveKeyExistsReturns409() throws Exception {
        when(projectApiKeyService.create(1L)).thenThrow(new ApiException(ApiErrorCode.PROJECT_CONFLICT));

        mockMvc.perform(post("/api/v1/projects/1/api-keys")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("P002"));
    }

    @Test
    void adminSettingUpdateReturns200WithUpdatedSetting() throws Exception {
        when(projectSettingService.update(eq(1L), any()))
                .thenReturn(new ProjectSettingUpdateResponse(
                        1L, 20, 60, 600, "https://hooks.example.com/ops/payment-v2", true, LocalDateTime.now()));

        mockMvc.perform(put("/api/v1/projects/1/settings")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SettingUpdateRequest(20, 600, "https://hooks.example.com/ops/payment-v2", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(1))
                .andExpect(jsonPath("$.data.threshold").value(20))
                .andExpect(jsonPath("$.data.windowSeconds").value(60))
                .andExpect(jsonPath("$.data.cooldownSeconds").value(600))
                .andExpect(jsonPath("$.data.webhookEnabled").value(true));
    }

    @Test
    void settingUpdateOperatorRequestReturns403() throws Exception {
        mockMvc.perform(put("/api/v1/projects/1/settings")
                        .header("X-User-Id", "1")
                        .header("X-Role", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingUpdateRequest(20, 600, null, false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    void settingUpdateMissingHeadersReturns401() throws Exception {
        mockMvc.perform(put("/api/v1/projects/1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingUpdateRequest(20, 600, null, false))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void settingUpdateMissingProjectReturns404() throws Exception {
        when(projectSettingService.update(eq(999L), any()))
                .thenThrow(new ApiException(ApiErrorCode.PROJECT_NOT_FOUND));

        mockMvc.perform(put("/api/v1/projects/999/settings")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingUpdateRequest(20, 600, null, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    void settingUpdateNonPositiveThresholdReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/projects/1/settings")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingUpdateRequest(0, 600, null, false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    void settingUpdateNonPositiveCooldownReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/projects/1/settings")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingUpdateRequest(20, 0, null, false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    void settingUpdateInvalidWebhookUrlFormatReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/projects/1/settings")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SettingUpdateRequest(20, 600, "not-a-url", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    void settingUpdateWebhookEnabledWithoutUrlReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/projects/1/settings")
                        .header("X-User-Id", "1")
                        .header("X-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingUpdateRequest(20, 600, null, true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    private record CreateRequest(String name) {
    }

    private record SettingUpdateRequest(int threshold, int cooldownSeconds, String webhookUrl, boolean webhookEnabled) {
    }
}
