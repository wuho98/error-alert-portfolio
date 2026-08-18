package com.wuho.erroralert.api.dto;

import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.domain.ProjectSetting;
import java.time.LocalDateTime;

public record ProjectCreateResponse(
        Long projectId,
        String name,
        int threshold,
        int windowSeconds,
        int cooldownSeconds,
        String webhookUrl,
        boolean webhookEnabled,
        LocalDateTime createdAt) {

    public static ProjectCreateResponse of(Project project, ProjectSetting setting) {
        return new ProjectCreateResponse(
                project.getId(),
                project.getName(),
                setting.getThreshold(),
                setting.getWindowSeconds(),
                setting.getCooldownSeconds(),
                setting.getWebhookUrl(),
                setting.isWebhookEnabled(),
                project.getCreatedAt());
    }
}
