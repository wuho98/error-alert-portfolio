package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.api.dto.ProjectApiKeyCreateResponse;
import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.repository.ProjectApiKeyRepository;
import com.wuho.erroralert.repository.ProjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProjectApiKeyServiceTest {

    @Autowired
    private ProjectApiKeyService projectApiKeyService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectApiKeyRepository projectApiKeyRepository;

    @AfterEach
    void cleanUp() {
        projectApiKeyRepository.deleteAll();
        projectRepository.deleteAll();
    }

    @Test
    void issuesKeyAndStoresOnlyHash() {
        Project project = projectRepository.saveAndFlush(new Project("payment-service"));

        ProjectApiKeyCreateResponse response = projectApiKeyService.create(project.getId());

        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(response.apiKey()).startsWith("pk_live_");
        assertThat(response.createdAt()).isNotNull();

        var saved = projectApiKeyRepository.findByProjectIdAndRevokedAtIsNull(project.getId()).orElseThrow();
        assertThat(saved.getApiKeyHash())
                .isNotEqualTo(response.apiKey())
                .hasSize(64); // SHA-256 hex 인코딩 길이
    }

    @Test
    void throwsNotFoundForMissingProject() {
        assertThatThrownBy(() -> projectApiKeyService.create(999L))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    void throwsConflictWhenActiveKeyAlreadyExists() {
        Project project = projectRepository.saveAndFlush(new Project("payment-service"));
        projectApiKeyService.create(project.getId());

        assertThatThrownBy(() -> projectApiKeyService.create(project.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.PROJECT_CONFLICT);
    }
}
