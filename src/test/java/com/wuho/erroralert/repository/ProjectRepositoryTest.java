package com.wuho.erroralert.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wuho.erroralert.config.JpaAuditingConfig;
import com.wuho.erroralert.domain.Project;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ProjectRepositoryTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void savesProjectAndFillsIdAndTimestamps() {
        Project project = new Project("payment-service");

        Project saved = projectRepository.saveAndFlush(project);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("payment-service");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsSavedProjectById() {
        Project saved = projectRepository.saveAndFlush(new Project("payment-service"));

        var found = projectRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("payment-service");
    }
}
