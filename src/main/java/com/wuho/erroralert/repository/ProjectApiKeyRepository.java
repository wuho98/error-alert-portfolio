package com.wuho.erroralert.repository;

import com.wuho.erroralert.domain.ProjectApiKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectApiKeyRepository extends JpaRepository<ProjectApiKey, Long> {

    /**
     * Phase 2 서비스 레이어에서 활성 key 중복 발급(409) 체크에 사용한다.
     */
    Optional<ProjectApiKey> findByProjectIdAndRevokedAtIsNull(Long projectId);

    /**
     * 오류 수신 API 인증에서 {@code X-Api-Key} 해시로 프로젝트를 조회할 때 사용한다.
     */
    Optional<ProjectApiKey> findByApiKeyHash(String apiKeyHash);
}
