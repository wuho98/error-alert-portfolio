package com.wuho.erroralert.service;

import com.wuho.erroralert.api.dto.ProjectCreateResponse;
import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.domain.ProjectSetting;
import com.wuho.erroralert.repository.ProjectRepository;
import com.wuho.erroralert.repository.ProjectSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectSettingRepository projectSettingRepository;

    public ProjectService(ProjectRepository projectRepository, ProjectSettingRepository projectSettingRepository) {
        this.projectRepository = projectRepository;
        this.projectSettingRepository = projectSettingRepository;
    }

    @Transactional
    public ProjectCreateResponse create(String name) {
        Project project = projectRepository.save(new Project(name));
        ProjectSetting setting = projectSettingRepository.save(new ProjectSetting(project));
        return ProjectCreateResponse.of(project, setting);
    }
}
