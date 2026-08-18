package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.domain.ProjectApiKey;
import com.wuho.erroralert.repository.ProjectApiKeyRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ErrorEventReceiveServiceTest {

    @Autowired
    private ErrorEventReceiveService errorEventReceiveService;

    @SpyBean
    private PlatformTransactionManager transactionManager;

    @MockBean
    private ApiKeyGenerator apiKeyGenerator;

    @MockBean
    private ProjectApiKeyRepository projectApiKeyRepository;

    @MockBean
    private ErrorEventService errorEventService;

    @MockBean
    private ErrorRateCounter errorRateCounter;

    @Test
    void inlineErrorCodeConversionOpensOneTransactionBeforeSaveIsCalled() {
        when(apiKeyGenerator.hash("pk_live_valid")).thenReturn("hashed-key");
        when(projectApiKeyRepository.findByApiKeyHash("hashed-key"))
                .thenReturn(Optional.of(new ProjectApiKey(new Project("payment-service"), "hashed-key")));
        clearInvocations(transactionManager, apiKeyGenerator, projectApiKeyRepository, errorEventService);

        assertThatThrownBy(() -> errorEventReceiveService.receive(
                "pk_live_valid",
                invalidCommand()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported errorCode: PAYMENT.UNKNOWN");

        verify(transactionManager, times(1)).getTransaction(any(TransactionDefinition.class));
        verify(transactionManager, times(1)).rollback(any(TransactionStatus.class));
        verify(transactionManager, never()).commit(any(TransactionStatus.class));
        verify(apiKeyGenerator).hash("pk_live_valid");
        verify(projectApiKeyRepository).findByApiKeyHash("hashed-key");
        verifyNoInteractions(errorEventService);
    }

    private ReceiveErrorEventCommand invalidCommand() {
        return new ReceiveErrorEventCommand(
                "PAYMENT.UNKNOWN",
                "PG approval request timed out",
                Instant.parse("2026-08-04T12:34:45Z")
        );
    }
}
