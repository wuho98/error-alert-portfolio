package com.wuho.erroralert.service;

import java.time.Instant;

public record ErrorTrendPointResult(
        Instant windowStartedAt,
        long count
) {
}
