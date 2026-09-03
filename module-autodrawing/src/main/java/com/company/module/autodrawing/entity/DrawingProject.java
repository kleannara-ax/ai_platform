package com.company.module.autodrawing.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 자동도면 프로젝트 엔티티
 * 기존 JSON 파일 기반 프로젝트 데이터를 JPA로 이전
 */
@Entity
@Table(name = "MOD_AUTODRAWING_PROJECT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DrawingProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PROJECT_ID")
    private Long projectId;

    /** 프로젝트 고유 식별자 (프론트엔드 UUID) */
    @Column(name = "PROJECT_UUID", nullable = false, length = 100, unique = true)
    private String projectUuid;

    /** 프로젝트명 */
    @Column(name = "PROJECT_NAME", nullable = false, length = 200)
    private String projectName;

    /** 팀 ID (프론트엔드에서 사용하는 팀 식별자) */
    @Column(name = "TEAM_ID", nullable = false, length = 50)
    private String teamId;

    /** 프로젝트 데이터 (JSON) — 도면 스펙, 치수, 부가정보 등 전체 저장 */
    @Column(name = "PROJECT_DATA", columnDefinition = "LONGTEXT")
    private String projectData;

    /** 생성자 (core_user.USER_ID 참조) */
    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateData(String projectName, String projectData) {
        this.projectName = projectName;
        this.projectData = projectData;
    }
}
