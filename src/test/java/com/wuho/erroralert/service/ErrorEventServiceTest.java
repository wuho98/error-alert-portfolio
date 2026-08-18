package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wuho.erroralert.domain.ErrorCode;
import com.wuho.erroralert.domain.ErrorEvent;
import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.repository.ErrorEventRepository;
import com.wuho.erroralert.repository.ProjectRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ErrorEventServiceTest {

    private static final ErrorCode ERROR_CODE = ErrorCode.PAYMENT_PG_TIMEOUT;
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-10T12:34:45Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-10T12:34:46Z");

    @Autowired
    private ErrorEventService errorEventService;

    @Autowired
    private ErrorEventRepository errorEventRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private ErrorRateCounter errorRateCounter;

    private Project project;

    @BeforeEach
    void setUp() {
        errorEventRepository.deleteAll();
        projectRepository.deleteAll();
        project = projectRepository.saveAndFlush(new Project("payment-service"));
    }

    @Test
    void incrementsRedisCounterAfterErrorEventCommit() {
        ErrorEvent savedEvent = saveErrorEvent();

        assertThat(savedEvent.getId()).isNotNull();
        assertThat(errorEventRepository.existsById(savedEvent.getId())).isTrue();
        verify(errorRateCounter).increment(project.getId(), ERROR_CODE.getCode(), RECEIVED_AT);
    }

    @Test
    void doesNotIncrementRedisCounterWhenTransactionRollsBack() {
        transactionTemplate.executeWithoutResult(status -> {
            saveErrorEvent();
            status.setRollbackOnly();
        });

        assertThat(errorEventRepository.count()).isZero();
        verify(errorRateCounter, never()).increment(anyLong(), anyString(), any(Instant.class));
    }

    @Test
    void redisFailureDoesNotRollBackSavedErrorEvent() {
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(errorRateCounter)
                .increment(project.getId(), ERROR_CODE.getCode(), RECEIVED_AT);

        ErrorEvent savedEvent = saveErrorEvent();

        assertThat(errorEventRepository.existsById(savedEvent.getId())).isTrue();
        verify(errorRateCounter).increment(project.getId(), ERROR_CODE.getCode(), RECEIVED_AT);
    }

    private ErrorEvent saveErrorEvent() {
        return errorEventService.save(
                project,
                ERROR_CODE,
                "PG approval request timed out",
                OCCURRED_AT,
                RECEIVED_AT
        );
    }
}
