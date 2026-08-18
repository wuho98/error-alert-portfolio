package com.wuho.erroralert.repository;

import com.wuho.erroralert.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
