package com.wuho.erroralert.api.dto;

import java.time.LocalDateTime;

public record ProjectApiKeyCreateResponse(Long projectId, String apiKey, LocalDateTime createdAt) {
}
