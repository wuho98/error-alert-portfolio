package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.api.dto.ProjectSettingUpdateRequest;
import com.wuho.erroralert.api.dto.ProjectSettingUpdateResponse;
import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.domain.ProjectSetting;
import com.wuho.erroralert.repository.ProjectRepository;
import com.wuho.erroralert.repository.ProjectSettingRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProjectSettingServiceTest {

    @Autowired
    private ProjectSettingService projectSettingService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectSettingRepository projectSettingRepository;

    @AfterEach
    void cleanUp() {
        projectSettingRepository.deleteAll();
        projectRepository.deleteAll();
    }

    @Test
    void updatesSettingAndKeepsWindowSecondsFixed() {
        Project project = projectRepository.saveAndFlush(new Project("payment-service"));
        projectSettingRepository.saveAndFlush(new ProjectSetting(project));

        ProjectSettingUpdateResponse response = projectSettingService.update(
                project.getId(),
                new ProjectSettingUpdateRequest(20, 600, "https://hooks.example.com/ops/payment-v2", true));

        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(response.threshold()).isEqualTo(20);
        assertThat(response.windowSeconds()).isEqualTo(ProjectSetting.DEFAULT_WINDOW_SECONDS);
        assertThat(response.cooldownSeconds()).isEqualTo(600);
        assertThat(response.webhookUrl()).isEqualTo("https://hooks.example.com/ops/payment-v2");
        assertThat(response.webhookEnabled()).isTrue();

        ProjectSetting saved = projectSettingRepository.findByProjectId(project.getId()).orElseThrow();
        assertThat(saved.getThreshold()).isEqualTo(20);
        assertThat(saved.getCooldownSeconds()).isEqualTo(600);
    }

    @Test
    void responseUpdatedAtReflectsThisUpdateNotThePreviousValue() throws InterruptedException {
        Project project = projectRepository.saveAndFlush(new Project("payment-service"));
        ProjectSetting setting = projectSettingRepository.saveAndFlush(new ProjectSetting(project));
        LocalDateTime initialUpdatedAt = setting.getUpdatedAt();

        Thread.sleep(10);

        ProjectSettingUpdateResponse response = projectSettingService.update(
                project.getId(), new ProjectSettingUpdateRequest(20, 600, null, false));

        assertThat(response.updatedAt()).isAfter(initialUpdatedAt);
    }

    @Test
    void throwsNotFoundForMissingProject() {
        assertThatThrownBy(() -> projectSettingService.update(
                999L, new ProjectSettingUpdateRequest(20, 600, null, false)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.PROJECT_NOT_FOUND);
    }
}
