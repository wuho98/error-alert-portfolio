package com.wuho.erroralert.api.error;

import com.wuho.erroralert.service.ErrorTrendPointResult;
import java.time.Instant;

public record ErrorTrendPointResponse(
        Instant windowStartedAt,
        long count
) {

    public static ErrorTrendPointResponse from(ErrorTrendPointResult result) {
        return new ErrorTrendPointResponse(result.windowStartedAt(), result.count());
    }
}
