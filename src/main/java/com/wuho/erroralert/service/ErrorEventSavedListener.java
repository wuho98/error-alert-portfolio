package com.wuho.erroralert.service;

import com.wuho.erroralert.domain.AlertLogStatus;
import com.wuho.erroralert.domain.ProjectSetting;
import com.wuho.erroralert.repository.ProjectSettingRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ErrorEventSavedListener {

    private static final Logger log = LoggerFactory.getLogger(ErrorEventSavedListener.class);

    private final ErrorRateCounter errorRateCounter;
    private final AlertCooldownService alertCooldownService;
    private final ProjectSettingRepository projectSettingRepository;
    private final AlertLogCreator alertLogCreator;
    private final WebhookNotifier webhookNotifier;

    public ErrorEventSavedListener(
        ErrorRateCounter errorRateCounter,
        AlertCooldownService alertCooldownService,
        ProjectSettingRepository projectSettingRepository,
        AlertLogCreator alertLogCreator, WebhookNotifier webhookNotifier
    ) {
        this.errorRateCounter = errorRateCounter;
        this.alertCooldownService = alertCooldownService;
        this.projectSettingRepository = projectSettingRepository;
        this.alertLogCreator = alertLogCreator;
        this.webhookNotifier = webhookNotifier;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void incrementErrorRate(ErrorEventSavedEvent event) {
        try {
            long count = errorRateCounter.increment(event.projectId(), event.errorCode(), event.receivedAt());
            ProjectSetting setting = projectSettingRepository.findByProjectId(event.projectId())
                    .orElseThrow(() -> new IllegalStateException(
                            "ProjectSetting not found: projectId=" + event.projectId()));

            if (count < setting.getThreshold()) {
                return;
            }

            boolean acquired = alertCooldownService.tryAcquire(
                    event.projectId(),
                    event.errorCode(),
                    setting.getCooldownSeconds()
            );
            if (!acquired) {
                log.debug(
                        "Cooldown active, skipping alert: projectId={}, errorCode={}",
                        event.projectId(),
                        event.errorCode()
                );
                return;
            }

            createAlertLog(event, count, setting);
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to process alert threshold after ErrorEvent commit: projectId={}, errorCode={}",
                    event.projectId(),
                    event.errorCode(),
                    exception
            );
        }
    }

    private void createAlertLog(ErrorEventSavedEvent event, long count, ProjectSetting setting) {
        Instant windowStartedAt = Instant.ofEpochSecond(
                RedisErrorRateCounter.windowStartEpochSecond(event.receivedAt()));
        AlertLogStatus status = setting.isWebhookEnabled()
                ? AlertLogStatus.PENDING
                : AlertLogStatus.SKIPPED;
        alertLogCreator.create(
                        event.projectId(),
                        event.errorCode(),
                        windowStartedAt,
                        count,
                        setting,
                        status)
                .filter(saved -> status == AlertLogStatus.PENDING)
                .ifPresent(saved -> webhookNotifier.send(
                        saved.getId(),
                        setting.getWebhookUrl(),
                        new WebhookPayload(
                                event.projectId(),
                                event.errorCode(),
                                windowStartedAt.toString(),
                                count,
                                setting.getThreshold())));
    }
}
