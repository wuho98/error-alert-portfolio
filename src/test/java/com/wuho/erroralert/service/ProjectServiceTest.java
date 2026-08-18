package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.wuho.erroralert.api.dto.ProjectCreateResponse;
import com.wuho.erroralert.domain.ProjectSetting;
import com.wuho.erroralert.repository.ProjectRepository;
import com.wuho.erroralert.repository.ProjectSettingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProjectServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @MockBean
    private ProjectSettingRepository projectSettingRepository;

    @AfterEach
    void cleanUp() {
        projectRepository.deleteAll();
    }

    @Test
    void createsProjectAndDefaultSettingInOneTransaction() {
        org.mockito.Mockito.when(projectSettingRepository.save(any(ProjectSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectCreateResponse response = projectService.create("payment-service");

        // DB에 실제로 1건만 저장됐는지 확인 (Project+ProjectSetting 단일 트랜잭션 커밋 불변식)
        assertThat(projectRepository.findAll()).hasSize(1);
        assertThat(response.projectId()).isNotNull();
        assertThat(response.name()).isEqualTo("payment-service");
        assertThat(response.threshold()).isEqualTo(ProjectSetting.DEFAULT_THRESHOLD);
        assertThat(response.windowSeconds()).isEqualTo(ProjectSetting.DEFAULT_WINDOW_SECONDS);
        assertThat(response.cooldownSeconds()).isEqualTo(ProjectSetting.DEFAULT_COOLDOWN_SECONDS);
        assertThat(response.webhookUrl()).isNull();
        assertThat(response.webhookEnabled()).isFalse();
    }

    @Test
    void rollsBackProjectWhenProjectSettingSaveFails() {
        doThrow(new RuntimeException("forced failure"))
                .when(projectSettingRepository).save(any(ProjectSetting.class));

        assertThatThrownBy(() -> projectService.create("payment-service"))
                .isInstanceOf(RuntimeException.class);

        assertThat(projectRepository.findAll()).isEmpty();
    }
}
