package com.wuho.erroralert.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
public class WebhookNotifier {

    private final MeterRegistry meterRegistry;
    private final RestClient webhookRestClient;
    private final AlertLogStatusUpdater alertLogStatusUpdater;

    private static final int MAX_RETRY_COUNT = 3;

    @Async
    @Retryable(retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 4, backoff = @Backoff(delay = 1000, multiplier = 2))

    public void send(Long alertId, String webhookUrl, WebhookPayload payload) {

        var retryContext = RetrySynchronizationManager.getContext();
        int retryCount = retryContext == null ? 0 : retryContext.getRetryCount();
        if (retryCount > 0) {
            meterRegistry.counter("webhook.send.retry").increment();
        }

        log.info("웹훅 발송 시작 - thread={},alertId={}, payload={}",
            Thread.currentThread().getName(), alertId, payload);

        webhookRestClient.post().uri(webhookUrl).body(payload).retrieve().toBodilessEntity();
        meterRegistry.counter("webhook.send.success").increment();
        alertLogStatusUpdater.markSent(alertId, retryCount);
        log.info("웹훅 발송 완료 - alertId={}, payload={}", alertId, payload);

    }

    @Recover
    public void recover(Exception e, Long alertId, String webhookUrl, WebhookPayload payload) {
        log.error("웹훅 최종 발송 실패 - alertId={}, url={}, payload={}", alertId, webhookUrl, payload, e);
        meterRegistry.counter("webhook.send.failure").increment();
        alertLogStatusUpdater.markFailed(alertId, MAX_RETRY_COUNT);
    }

}
