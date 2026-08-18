package com.wuho.erroralert.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.hibernate.validator.constraints.URL;

public record ProjectSettingUpdateRequest(
        @Min(1) int threshold,
        @Min(1) int cooldownSeconds,
        @URL String webhookUrl,
        boolean webhookEnabled) {

    @AssertTrue(message = "webhookEnabled=true이면 webhookUrl이 필요합니다.")
    public boolean isWebhookUrlPresentWhenEnabled() {
        return !webhookEnabled || (webhookUrl != null && !webhookUrl.isBlank());
    }
}
