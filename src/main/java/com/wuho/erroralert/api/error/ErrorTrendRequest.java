package com.wuho.erroralert.api.error;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.service.FindErrorTrendCommand;
import java.time.Instant;

public record ErrorTrendRequest(
        Long projectId,
        String errorCode,
        Instant from,
        Instant to,
        String interval
) {

    public FindErrorTrendCommand toCommand() {
        String resolvedInterval = interval == null
                ? FindErrorTrendCommand.SUPPORTED_INTERVAL
                : interval.trim();
        validate(resolvedInterval);
        return FindErrorTrendCommand.of(projectId, errorCode, from, to, resolvedInterval);
    }

    private void validate(String resolvedInterval) {
        if (projectId == null || from == null || to == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        if (resolvedInterval.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        if (from.isAfter(to)) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
    }
}
