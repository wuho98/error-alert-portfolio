package com.wuho.erroralert.service;

import com.wuho.erroralert.domain.ErrorEvent;
import java.util.List;
import org.springframework.data.domain.Page;

public record RecentErrorEventsResult(
        int page,
        int size,
        long totalElements,
        List<RecentErrorEventResult> content
) {

    public RecentErrorEventsResult {
        content = List.copyOf(content);
    }

    public static RecentErrorEventsResult from(Page<ErrorEvent> page) {
        List<RecentErrorEventResult> content = page.getContent().stream()
                .map(RecentErrorEventResult::from)
                .toList();
        return new RecentErrorEventsResult(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                content
        );
    }
}
