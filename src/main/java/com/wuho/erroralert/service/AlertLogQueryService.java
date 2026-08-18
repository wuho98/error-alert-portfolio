package com.wuho.erroralert.service;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.domain.AlertLog;
import com.wuho.erroralert.repository.AlertLogRepository;
import com.wuho.erroralert.repository.ProjectRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertLogQueryService {

    private final AlertLogRepository alertLogRepository;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public AlertLogsResult findAlerts(FindAlertLogsCommand command) {
        if (!projectRepository.existsById(command.projectId())) {
            throw new ApiException(ApiErrorCode.PROJECT_NOT_FOUND);
        }

        Page<AlertLog> page = alertLogRepository.findAll(specification(command), command.toPageable());
        return AlertLogsResult.from(page);
    }

    private Specification<AlertLog> specification(FindAlertLogsCommand command) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("project").get("id"), command.projectId()));
            if (command.errorCode() != null) {
                predicates.add(criteriaBuilder.equal(root.get("errorCode"), command.errorCode()));
            }
            if (command.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), command.status()));
            }
            if (command.from() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("createdAt"), LocalDateTime.ofInstant(command.from(), ZoneOffset.UTC)));
            }
            if (command.to() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("createdAt"), LocalDateTime.ofInstant(command.to(), ZoneOffset.UTC)));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
