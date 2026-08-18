package com.wuho.erroralert.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public record ErrorTrendResult(
        Long projectId,
        String errorCode,
        String interval,
        List<ErrorTrendPointResult> points
) {

    private static final long WINDOW_SECONDS = 60;

    public ErrorTrendResult {
        points = List.copyOf(points);
    }

    public static ErrorTrendResult of(FindErrorTrendCommand command, List<Instant> occurredAts) {
        Map<Instant, Long> counts = occurredAts.stream()
                .collect(Collectors.groupingBy(
                        ErrorTrendResult::windowStart,
                        TreeMap::new,
                        Collectors.counting()
                ));
        List<ErrorTrendPointResult> points = buildZeroFilledPoints(command, counts);
        return new ErrorTrendResult(
                command.projectId(),
                command.errorCodeValue(),
                command.interval(),
                points
        );
    }

    private static List<ErrorTrendPointResult> buildZeroFilledPoints(
            FindErrorTrendCommand command,
            Map<Instant, Long> counts
    ) {
        List<ErrorTrendPointResult> points = new ArrayList<>();
        Instant current = windowStart(command.from());
        Instant end = windowStart(command.to());

        while (!current.isAfter(end)) {
            points.add(new ErrorTrendPointResult(current, counts.getOrDefault(current, 0L)));
            current = current.plusSeconds(WINDOW_SECONDS);
        }
        return points;
    }

    private static Instant windowStart(Instant instant) {
        long epochSecond = instant.getEpochSecond();
        return Instant.ofEpochSecond(Math.floorDiv(epochSecond, WINDOW_SECONDS) * WINDOW_SECONDS);
    }
}
