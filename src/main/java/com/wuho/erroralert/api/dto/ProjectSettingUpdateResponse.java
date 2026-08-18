package com.wuho.erroralert.api.dto;

import com.wuho.erroralert.domain.ProjectSetting;
import java.time.LocalDateTime;

public record ProjectSettingUpdateResponse(
        Long projectId,
        int threshold,
        int windowSeconds,
        int cooldownSeconds,
        String webhookUrl,
        boolean webhookEnabled,
        LocalDateTime updatedAt) {

    public static ProjectSettingUpdateResponse of(Long projectId, ProjectSetting setting) {
        return new ProjectSettingUpdateResponse(
                projectId,
                setting.getThreshold(),
                setting.getWindowSeconds(),
                setting.getCooldownSeconds(),
                setting.getWebhookUrl(),
                setting.isWebhookEnabled(),
                setting.getUpdatedAt());
    }
}
