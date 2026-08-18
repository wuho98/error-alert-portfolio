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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Project당 하나만 존재한다 ({@code uk_project_setting_project_id} 제약으로 보장).
 */
@Entity
@Table(
        name = "project_setting",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_setting_project_id", columnNames = "project_id"))
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ProjectSetting {

    public static final int DEFAULT_THRESHOLD = 10;
    public static final int DEFAULT_WINDOW_SECONDS = 60;
    public static final int DEFAULT_COOLDOWN_SECONDS = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private int threshold = DEFAULT_THRESHOLD;

    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds = DEFAULT_WINDOW_SECONDS;

    @Column(name = "cooldown_seconds", nullable = false)
    private int cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;

    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(name = "webhook_enabled", nullable = false)
    private boolean webhookEnabled = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public ProjectSetting(Project project) {
        this.project = Objects.requireNonNull(project, "project must not be null");
    }

    public void update(int threshold, int cooldownSeconds, String webhookUrl, boolean webhookEnabled) {
        this.threshold = threshold;
        this.cooldownSeconds = cooldownSeconds;
        this.webhookUrl = webhookUrl;
        this.webhookEnabled = webhookEnabled;
    }
}
