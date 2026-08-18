package com.wuho.erroralert.service;

import com.wuho.erroralert.domain.AlertLog;
import com.wuho.erroralert.domain.AlertLogStatus;
import com.wuho.erroralert.domain.ErrorCode;
import com.wuho.erroralert.domain.ProjectSetting;
import com.wuho.erroralert.repository.AlertLogRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AFTER_COMMIT 리스너는 트랜잭션 밖에서 실행되므로 AlertLog 저장을 별도 트랜잭션으로 분리한다.
 * 같은 클래스 내부 호출은 프록시를 거치지 않아 트랜잭션이 적용되지 않으므로 별도 빈으로 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertLogCreator {

    private final AlertLogRepository alertLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AlertLog> create(
            long projectId,
            String errorCode,
            Instant windowStartedAt,
            long count,
            ProjectSetting setting,
            AlertLogStatus status
    ) {
        AlertLog alertLog = AlertLog.create(
                setting.getProject(),
                ErrorCode.fromCode(errorCode),
                windowStartedAt,
                Math.toIntExact(count),
                setting.getThreshold(),
                status
        );

        try {
            return Optional.of(alertLogRepository.saveAndFlush(alertLog));
        } catch (DataIntegrityViolationException exception) {
            log.info(
                    "AlertLog already exists: projectId={}, errorCode={}, windowStartedAt={}",
                    projectId,
                    errorCode,
                    windowStartedAt
            );
            return Optional.empty();
        }
    }
}
