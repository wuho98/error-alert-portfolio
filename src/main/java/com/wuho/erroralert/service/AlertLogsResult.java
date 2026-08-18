package com.wuho.erroralert.service;

import com.wuho.erroralert.domain.AlertLog;
import java.util.List;
import org.springframework.data.domain.Page;

public record AlertLogsResult(
        int page,
        int size,
        long totalElements,
        List<AlertLogResult> content
) {

    public AlertLogsResult {
        content = List.copyOf(content);
    }

    public static AlertLogsResult from(Page<AlertLog> page) {
        List<AlertLogResult> content = page.getContent().stream()
                .map(AlertLogResult::from)
                .toList();
        return new AlertLogsResult(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                content
        );
    }
}
