package com.wuho.erroralert.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ProjectCreateRequest(@NotBlank String name) {
}
