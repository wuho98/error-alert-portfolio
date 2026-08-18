package com.wuho.erroralert.api.alert;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.service.FindAlertLogsCommand;
import java.time.Instant;

public record AlertLogsRequest(
        Long projectId,
        String errorCode,
        String status,
        Instant from,
        Instant to,
        Integer page,
        Integer size
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    public FindAlertLogsCommand toCommand() {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        validate(resolvedPage, resolvedSize);
        return FindAlertLogsCommand.of(
                projectId,
                errorCode,
                status,
                from,
                to,
                resolvedPage,
                resolvedSize
        );
    }

    private void validate(int resolvedPage, int resolvedSize) {
        if (projectId == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        if (resolvedPage < 0) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        if (resolvedSize < 1 || resolvedSize > MAX_PAGE_SIZE) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
    }
}
