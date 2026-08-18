package com.wuho.erroralert.repository;

import com.wuho.erroralert.domain.AlertLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlertLogRepository extends JpaRepository<AlertLog, Long>, JpaSpecificationExecutor<AlertLog> {
}
