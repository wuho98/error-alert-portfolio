package com.wuho.erroralert.api.alert;

import java.util.List;

public record AlertSummaryResponse(
    Long alertId,
    String situation,
    List<String> checkFirst,
    List<String> needMoreInfo
) {

    public static AlertSummaryResponse of(Long alertId, String situation) {
        return new AlertSummaryResponse(alertId, situation,
            List.of("의존 외부 서비스(PG사) 응답 지연 여부", "애플리케이션 커넥션 풀 고갈 여부"),
            List.of("동일 구간의 외부 서비스 상태 공지"));
    }
}
