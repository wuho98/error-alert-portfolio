package com.wuho.erroralert.service;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.api.dto.ProjectApiKeyCreateResponse;
import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.domain.ProjectApiKey;
import com.wuho.erroralert.repository.ProjectApiKeyRepository;
import com.wuho.erroralert.repository.ProjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectApiKeyService {

    private final ProjectRepository projectRepository;
    private final ProjectApiKeyRepository projectApiKeyRepository;
    private final ApiKeyGenerator apiKeyGenerator;

    public ProjectApiKeyService(
            ProjectRepository projectRepository,
            ProjectApiKeyRepository projectApiKeyRepository,
            ApiKeyGenerator apiKeyGenerator) {
        this.projectRepository = projectRepository;
        this.projectApiKeyRepository = projectApiKeyRepository;
        this.apiKeyGenerator = apiKeyGenerator;
    }

    @Transactional
    public ProjectApiKeyCreateResponse create(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PROJECT_NOT_FOUND));

        projectApiKeyRepository.findByProjectIdAndRevokedAtIsNull(projectId)
                .ifPresent(existing -> {
                    throw new ApiException(ApiErrorCode.PROJECT_CONFLICT);
                });

        String rawKey = apiKeyGenerator.generateRawKey();
        String apiKeyHash = apiKeyGenerator.hash(rawKey);

        try {
            ProjectApiKey saved = projectApiKeyRepository.saveAndFlush(new ProjectApiKey(project, apiKeyHash));
            return new ProjectApiKeyCreateResponse(project.getId(), rawKey, saved.getCreatedAt());
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ApiErrorCode.PROJECT_CONFLICT);
        }
    }
}
