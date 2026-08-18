package com.wuho.erroralert.service;

import com.wuho.erroralert.repository.AlertLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaAlertSummaryProvider implements AlertSummaryProvider {

    private final AlertLogRepository alertLogRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<AlertSummaryInput> findAlert(Long alertId) {
        return alertLogRepository.findById(alertId)
            .map(alertLog -> new AlertSummaryInput(
                alertLog.getProject().getId(),
                alertLog.getErrorCode().getCode(),
                alertLog.getWindowStartedAt().toString(),
                alertLog.getObservedCount(),
                alertLog.getThreshold(),
                alertLog.getStatus().name()));
    }
}
