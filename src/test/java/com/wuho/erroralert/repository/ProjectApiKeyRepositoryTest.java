package com.wuho.erroralert.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wuho.erroralert.config.JpaAuditingConfig;
import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.domain.ProjectApiKey;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ProjectApiKeyRepositoryTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectApiKeyRepository projectApiKeyRepository;

    @Test
    void savesApiKeyHashAndLeavesRevokedAtNull() {
        Project project = projectRepository.saveAndFlush(new Project("payment-service"));

        ProjectApiKey saved = projectApiKeyRepository.saveAndFlush(new ProjectApiKey(project, "hashed-key-value"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProject().getId()).isEqualTo(project.getId());
        assertThat(saved.getApiKeyHash()).isEqualTo("hashed-key-value");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void rejectsDuplicateApiKeyHash() {
        Project project = projectRepository.saveAndFlush(new Project("payment-service"));
        projectApiKeyRepository.saveAndFlush(new ProjectApiKey(project, "duplicate-hash"));

        assertThatThrownBy(() ->
                        projectApiKeyRepository.saveAndFlush(new ProjectApiKey(project, "duplicate-hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsActiveKeyByProjectId() {
        Project project = projectRepository.saveAndFlush(new Project("payment-service"));
        ProjectApiKey saved = projectApiKeyRepository.saveAndFlush(new ProjectApiKey(project, "hashed-key-value"));

        var found = projectApiKeyRepository.findByProjectIdAndRevokedAtIsNull(project.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findsKeyByApiKeyHash() {
        Project project = projectRepository.saveAndFlush(new Project("payment-service"));
        projectApiKeyRepository.saveAndFlush(new ProjectApiKey(project, "hashed-key-value"));

        var found = projectApiKeyRepository.findByApiKeyHash("hashed-key-value");

        assertThat(found).isPresent();
        assertThat(found.get().getProject().getId()).isEqualTo(project.getId());
    }

    @Test
    void hasNoPlainTextKeyField() {
        List<String> keyRelatedFieldNames = Arrays.stream(ProjectApiKey.class.getDeclaredFields())
                .map(Field::getName)
                .filter(name -> name.toLowerCase().contains("key"))
                .toList();

        assertThat(keyRelatedFieldNames).containsExactly("apiKeyHash");
    }
}
