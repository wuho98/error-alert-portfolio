package com.wuho.erroralert.api.error;

import com.wuho.erroralert.api.error.validation.AllowedErrorCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ReceiveErrorEventRequest(

        @NotBlank
        @AllowedErrorCode
        String errorCode,

        @NotBlank
        String message,

        @NotNull
        Instant occurredAt
) {
}
