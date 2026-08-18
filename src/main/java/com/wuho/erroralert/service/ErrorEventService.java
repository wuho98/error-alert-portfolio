package com.wuho.erroralert.service;

import com.wuho.erroralert.domain.ErrorCode;
import com.wuho.erroralert.domain.ErrorEvent;
import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.repository.ErrorEventRepository;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErrorEventService {

    private final ErrorEventRepository errorEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ErrorEventService(
            ErrorEventRepository errorEventRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.errorEventRepository = errorEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ErrorEvent save(
            Project project,
            ErrorCode errorCode,
            String message,
            Instant occurredAt,
            Instant receivedAt
    ) {
        ErrorEvent savedEvent = errorEventRepository.saveAndFlush(
                ErrorEvent.create(project, errorCode, message, occurredAt, receivedAt)
        );
        eventPublisher.publishEvent(
            new ErrorEventSavedEvent(
                project.getId(),
                errorCode.getCode(),
                receivedAt
            )
        );
        return savedEvent;
    }
}
