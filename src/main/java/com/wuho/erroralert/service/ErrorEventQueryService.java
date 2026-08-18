package com.wuho.erroralert.service;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.domain.ErrorEvent;
import com.wuho.erroralert.repository.ErrorEventRepository;
import com.wuho.erroralert.repository.ProjectRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class ErrorEventQueryService {

    private final ErrorEventRepository errorEventRepository;
    private final ProjectRepository projectRepository;

    public ErrorEventQueryService(ErrorEventRepository errorEventRepository, ProjectRepository projectRepository) {
        this.errorEventRepository = errorEventRepository;
        this.projectRepository = projectRepository;
    }

    public RecentErrorEventsResult findRecent(FindRecentErrorEventsCommand command) {
        if (!projectRepository.existsById(command.projectId())) {
            throw new ApiException(ApiErrorCode.PROJECT_NOT_FOUND);
        }

        Page<ErrorEvent> page = errorEventRepository.findAll(specification(command), command.toPageable());
        return RecentErrorEventsResult.from(page);
    }

    public ErrorTrendResult findTrend(FindErrorTrendCommand command) {
        if (!projectRepository.existsById(command.projectId())) {
            throw new ApiException(ApiErrorCode.PROJECT_NOT_FOUND);
        }

        List<Instant> occurredAts = findOccurredAtsForTrend(command);
        return ErrorTrendResult.of(command, occurredAts);
    }

    private List<Instant> findOccurredAtsForTrend(FindErrorTrendCommand command) {
        if (command.errorCode() == null) {
            return errorEventRepository.findOccurredAtForTrend(command.projectId(), command.from(), command.to());
        }
        return errorEventRepository.findOccurredAtForTrend(
                command.projectId(),
                command.errorCode(),
                command.from(),
                command.to()
        );
    }

    private Specification<ErrorEvent> specification(FindRecentErrorEventsCommand command) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("project").get("id"), command.projectId()));
            if (command.errorCode() != null) {
                predicates.add(criteriaBuilder.equal(root.get("errorCode"), command.errorCode()));
            }
            if (command.from() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), command.from()));
            }
            if (command.to() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), command.to()));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
