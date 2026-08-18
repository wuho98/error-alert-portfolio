package com.wuho.erroralert.service;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.domain.AlertLogStatus;
import com.wuho.erroralert.domain.ErrorCode;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record FindAlertLogsCommand(
        Long projectId,
        ErrorCode errorCode,
        AlertLogStatus status,
        Instant from,
        Instant to,
        int page,
        int size
) {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String FIXED_SORT_PROPERTY = "createdAt";

    public FindAlertLogsCommand {
        if (projectId == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        if (page < 0) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
    }

    public static FindAlertLogsCommand of(
            Long projectId,
            String errorCode,
            String status,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        return new FindAlertLogsCommand(
                projectId,
                parseErrorCode(errorCode),
                parseStatus(status),
                from,
                to,
                page,
                size
        );
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, FIXED_SORT_PROPERTY));
    }

    private static ErrorCode parseErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return null;
        }
        try {
            return ErrorCode.fromCode(errorCode);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
    }

    private static AlertLogStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AlertLogStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
    }
}
