package com.wuho.erroralert.service;

import com.wuho.erroralert.repository.AlertLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Webhook 발송 결과를 AlertLog 상태로 반영한다.
 * 발송은 @Async 스레드에서 트랜잭션 없이 실행되므로 상태 저장은 별도 빈의 새 트랜잭션에서 처리한다.
 * (같은 클래스 내부 호출은 프록시를 거치지 않아 @Transactional이 적용되지 않는다.)
 */
@Service
@RequiredArgsConstructor
public class AlertLogStatusUpdater {

    private final AlertLogRepository alertLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long alertId, int retryCount) {
        alertLogRepository.findById(alertId)
                .ifPresent(alertLog -> alertLog.markSent(retryCount));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long alertId, int retryCount) {
        alertLogRepository.findById(alertId)
                .ifPresent(alertLog -> alertLog.markFailed(retryCount));
    }
}
