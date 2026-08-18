package com.wuho.erroralert.service;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;

public record FindErrorTrendCommand(
        Long projectId,
        ErrorCode errorCode,
        Instant from,
        Instant to,
        String interval
) {

    public static final String SUPPORTED_INTERVAL = "60s";
    public static final Duration MAX_RANGE = Duration.ofHours(24);

    public FindErrorTrendCommand {
        if (projectId == null || from == null || to == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        if (!SUPPORTED_INTERVAL.equals(interval)) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        if (from.isAfter(to)) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
    }

    public static FindErrorTrendCommand of(
            Long projectId,
            String errorCode,
            Instant from,
            Instant to,
            String interval
    ) {
        return new FindErrorTrendCommand(
                projectId,
                parseErrorCode(errorCode),
                from,
                to,
                interval
        );
    }

    public String errorCodeValue() {
        if (errorCode == null) {
            return null;
        }
        return errorCode.getCode();
    }

    private static ErrorCode parseErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return null;
        }
        try {
            return ErrorCode.fromCode(errorCode.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
    }
}
