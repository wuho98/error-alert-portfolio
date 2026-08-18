package com.wuho.erroralert.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wuho.erroralert.config.JpaAuditingConfig;
import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.domain.ProjectSetting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ProjectSettingRepositoryTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectSettingRepository projectSettingRepository;

    @Test
    void savesDefaultSettingForProject() {
        Project project = projectRepository.saveAndFlush(new Project("payment-service"));

        ProjectSetting saved = projectSettingRepository.saveAndFlush(new ProjectSetting(project));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProject().getId()).isEqualTo(project.getId());
        assertThat(saved.getThreshold()).isEqualTo(ProjectSetting.DEFAULT_THRESHOLD);
        assertThat(saved.getWindowSeconds()).isEqualTo(ProjectSetting.DEFAULT_WINDOW_SECONDS);
        assertThat(saved.getCooldownSeconds()).isEqualTo(ProjectSetting.DEFAULT_COOLDOWN_SECONDS);
        assertThat(saved.getWebhookUrl()).isNull();
        assertThat(saved.isWebhookEnabled()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsSecondSettingForSameProject() {
        Project project = projectRepository.saveAndFlush(new Project("payment-service"));
        projectSettingRepository.saveAndFlush(new ProjectSetting(project));

        assertThatThrownBy(() -> projectSettingRepository.saveAndFlush(new ProjectSetting(project)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsSettingByProjectId() {
        Project project = projectRepository.saveAndFlush(new Project("payment-service"));
        projectSettingRepository.saveAndFlush(new ProjectSetting(project));

        var found = projectSettingRepository.findByProjectId(project.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getProject().getId()).isEqualTo(project.getId());
    }
}
