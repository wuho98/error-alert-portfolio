package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wuho.erroralert.domain.AlertLog;
import com.wuho.erroralert.domain.AlertLogStatus;
import com.wuho.erroralert.domain.ErrorCode;
import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.domain.ProjectSetting;
import com.wuho.erroralert.repository.ProjectSettingRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ErrorEventSavedListenerTest {

    private static final long PROJECT_ID = 7L;
    private static final ErrorCode ERROR_CODE = ErrorCode.PAYMENT_PG_TIMEOUT;
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-10T12:34:46Z");

    @Mock
    private ErrorRateCounter errorRateCounter;

    @Mock
    private AlertCooldownService alertCooldownService;

    @Mock
    private ProjectSettingRepository projectSettingRepository;

    @Mock
    private AlertLogCreator alertLogCreator;

    @Mock
    private WebhookNotifier webhookNotifier;

    private ProjectSetting setting;
    private ErrorEventSavedListener listener;

    @BeforeEach
    void setUp() {
        setting = new ProjectSetting(new Project("payment-service"));
        listener = new ErrorEventSavedListener(
                errorRateCounter,
                alertCooldownService,
                projectSettingRepository,
                alertLogCreator,
                webhookNotifier
        );
        when(projectSettingRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(setting));
    }

    @Test
    void createsPendingAlertLogAndSendsWebhookWhenWebhookIsEnabled() {
        setting.update(10, 300, "https://example.com/webhook", true);
        arrangeThresholdExceededAndCooldownAcquired(12L);
        arrangeAlertLogCreated();

        listener.incrementErrorRate(event());

        assertThat(capturedStatus()).isEqualTo(AlertLogStatus.PENDING);
        assertThat(capturedWindowStartedAt()).isEqualTo(Instant.parse("2026-08-10T12:34:00Z"));
        assertThat(capturedObservedCount()).isEqualTo(12L);
        verify(webhookNotifier).send(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("https://example.com/webhook"),
                org.mockito.ArgumentMatchers.any(WebhookPayload.class));
    }

    @Test
    void createsSkippedAlertLogAndDoesNotSendWebhookWhenWebhookIsDisabled() {
        setting.update(10, 300, null, false);
        arrangeThresholdExceededAndCooldownAcquired(10L);
        arrangeAlertLogCreated();

        listener.incrementErrorRate(event());

        assertThat(capturedStatus()).isEqualTo(AlertLogStatus.SKIPPED);
        verify(webhookNotifier, never()).send(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotCreateAlertLogWhenCooldownCannotBeAcquired() {
        when(errorRateCounter.increment(PROJECT_ID, ERROR_CODE.getCode(), RECEIVED_AT)).thenReturn(10L);
        when(alertCooldownService.tryAcquire(PROJECT_ID, ERROR_CODE.getCode(), 300)).thenReturn(false);

        listener.incrementErrorRate(event());

        verify(alertLogCreator, never()).create(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotSendWebhookWhenAlertLogAlreadyExists() {
        setting.update(10, 300, "https://example.com/webhook", true);
        arrangeThresholdExceededAndCooldownAcquired(10L);
        when(alertLogCreator.create(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        assertThatCode(() -> listener.incrementErrorRate(event()))
                .doesNotThrowAnyException();

        verify(webhookNotifier, never()).send(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private void arrangeThresholdExceededAndCooldownAcquired(long count) {
        when(errorRateCounter.increment(PROJECT_ID, ERROR_CODE.getCode(), RECEIVED_AT)).thenReturn(count);
        when(alertCooldownService.tryAcquire(PROJECT_ID, ERROR_CODE.getCode(), 300)).thenReturn(true);
    }

    private void arrangeAlertLogCreated() {
        AlertLog saved = org.mockito.Mockito.mock(AlertLog.class);
        org.mockito.Mockito.lenient().when(saved.getId()).thenReturn(1L);
        when(alertLogCreator.create(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(saved));
    }

    private AlertLogStatus capturedStatus() {
        ArgumentCaptor<AlertLogStatus> captor = ArgumentCaptor.forClass(AlertLogStatus.class);
        verify(alertLogCreator).create(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                captor.capture());
        return captor.getValue();
    }

    private Instant capturedWindowStartedAt() {
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(alertLogCreator).create(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                captor.capture(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        return captor.getValue();
    }

    private long capturedObservedCount() {
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(alertLogCreator).create(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                captor.capture(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        return captor.getValue();
    }

    private ErrorEventSavedEvent event() {
        return new ErrorEventSavedEvent(PROJECT_ID, ERROR_CODE.getCode(), RECEIVED_AT);
    }
}
