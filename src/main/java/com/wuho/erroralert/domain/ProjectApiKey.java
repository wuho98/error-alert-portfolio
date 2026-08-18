package com.wuho.erroralert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * API key 원문은 저장하지 않고 {@code apiKeyHash}만 보관한다.
 */
@Entity
@Table(name = "project_api_key")
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ProjectApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "api_key_hash", nullable = false, unique = true)
    private String apiKeyHash;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public ProjectApiKey(Project project, String apiKeyHash) {
        this.project = Objects.requireNonNull(project, "project must not be null");
        this.apiKeyHash = Objects.requireNonNull(apiKeyHash, "apiKeyHash must not be null");
    }
}
