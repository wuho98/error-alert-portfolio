package com.wuho.erroralert.service;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.domain.ErrorCode;
import com.wuho.erroralert.domain.ErrorEvent;
import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.domain.ProjectApiKey;
import com.wuho.erroralert.repository.ProjectApiKeyRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErrorEventReceiveService {

    private final ProjectApiKeyRepository projectApiKeyRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final ErrorEventService errorEventService;
    private final Clock clock;

    public ErrorEventReceiveService(
            ProjectApiKeyRepository projectApiKeyRepository,
            ApiKeyGenerator apiKeyGenerator,
            ErrorEventService errorEventService,
            Clock clock
    ) {
        this.projectApiKeyRepository = projectApiKeyRepository;
        this.apiKeyGenerator = apiKeyGenerator;
        this.errorEventService = errorEventService;
        this.clock = clock;
    }

    @Transactional
    public ReceiveErrorEventResult receive(String rawApiKey, ReceiveErrorEventCommand command) {
        Project project = authenticate(rawApiKey);
        Instant receivedAt = Instant.now(clock);
        ErrorEvent savedEvent = errorEventService.save(
                project,
                ErrorCode.fromCode(command.errorCode()),
                command.message(),
                command.occurredAt(),
                receivedAt
        );
        return ReceiveErrorEventResult.from(savedEvent);
    }

    private Project authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED);
        }

        String apiKeyHash = apiKeyGenerator.hash(rawApiKey.trim());
        ProjectApiKey apiKey = projectApiKeyRepository.findByApiKeyHash(apiKeyHash)
                .orElseThrow(() -> new ApiException(ApiErrorCode.UNAUTHORIZED));
        if (apiKey.getRevokedAt() != null) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED);
        }
        return apiKey.getProject();
    }
}
