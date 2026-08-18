package com.wuho.erroralert.repository;

import com.wuho.erroralert.domain.ProjectSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectSettingRepository extends JpaRepository<ProjectSetting, Long> {

    Optional<ProjectSetting> findByProjectId(Long projectId);
}
