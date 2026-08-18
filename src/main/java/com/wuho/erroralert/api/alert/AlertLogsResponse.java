package com.wuho.erroralert.api.alert;

import com.wuho.erroralert.service.AlertLogsResult;
import java.util.List;

public record AlertLogsResponse(
        int page,
        int size,
        long totalElements,
        List<AlertLogResponse> content
) {

    public AlertLogsResponse {
        content = List.copyOf(content);
    }

    public static AlertLogsResponse from(AlertLogsResult result) {
        List<AlertLogResponse> content = result.content().stream()
                .map(AlertLogResponse::from)
                .toList();
        return new AlertLogsResponse(
                result.page(),
                result.size(),
                result.totalElements(),
                content
        );
    }
}
