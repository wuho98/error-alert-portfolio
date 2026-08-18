package com.wuho.erroralert.service;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.api.dto.ProjectSettingUpdateRequest;
import com.wuho.erroralert.api.dto.ProjectSettingUpdateResponse;
import com.wuho.erroralert.domain.ProjectSetting;
import com.wuho.erroralert.repository.ProjectSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectSettingService {

    private final ProjectSettingRepository projectSettingRepository;

    public ProjectSettingService(ProjectSettingRepository projectSettingRepository) {
        this.projectSettingRepository = projectSettingRepository;
    }

    @Transactional
    public ProjectSettingUpdateResponse update(Long projectId, ProjectSettingUpdateRequest request) {
        ProjectSetting setting = projectSettingRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PROJECT_NOT_FOUND));

        setting.update(request.threshold(), request.cooldownSeconds(), request.webhookUrl(), request.webhookEnabled());
        ProjectSetting saved = projectSettingRepository.saveAndFlush(setting);

        return ProjectSettingUpdateResponse.of(projectId, saved);
    }
}
