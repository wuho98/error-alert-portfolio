package com.wuho.erroralert.api.error;

import com.wuho.erroralert.service.RecentErrorEventsResult;
import java.util.List;

public record RecentErrorEventsResponse(
        int page,
        int size,
        long totalElements,
        List<RecentErrorEventResponse> content
) {

    public RecentErrorEventsResponse {
        content = List.copyOf(content);
    }

    public static RecentErrorEventsResponse from(RecentErrorEventsResult result) {
        List<RecentErrorEventResponse> content = result.content().stream()
                .map(RecentErrorEventResponse::from)
                .toList();
        return new RecentErrorEventsResponse(
                result.page(),
                result.size(),
                result.totalElements(),
                content
        );
    }
}
