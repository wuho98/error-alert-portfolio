package com.wuho.erroralert.api.error;

import com.wuho.erroralert.service.ErrorTrendResult;
import java.util.List;

public record ErrorTrendResponse(
        Long projectId,
        String errorCode,
        String interval,
        List<ErrorTrendPointResponse> points
) {

    public ErrorTrendResponse {
        points = List.copyOf(points);
    }

    public static ErrorTrendResponse from(ErrorTrendResult result) {
        List<ErrorTrendPointResponse> points = result.points().stream()
                .map(ErrorTrendPointResponse::from)
                .toList();
        return new ErrorTrendResponse(
                result.projectId(),
                result.errorCode(),
                result.interval(),
                points
        );
    }
}
