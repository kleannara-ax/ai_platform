package com.company.module.dailyreport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 일보 이미지 첨부 엔티티
 * - 일보에 첨부된 이미지 파일 정보 관리
 * - 이미지 추가·미리보기·삭제 지원
 */
@Entity
@Table(name = "daily_report_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyReportImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IMAGE_ID")
    private Long imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REPORT_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_IMAGE_REPORT"))
    private DailyReport dailyReport;

    /** 원본 파일명 */
    @Column(name = "ORIGINAL_NAME", nullable = false, length = 300)
    private String originalName;

    /** 저장 경로 (서버 상대 경로 또는 URL) */
    @Column(name = "STORED_PATH", nullable = false, length = 500)
    private String storedPath;

    /** 파일 크기 (bytes) */
    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;

    /** MIME 타입 (예: image/jpeg, image/png) */
    @Column(name = "CONTENT_TYPE", nullable = false, length = 100)
    private String contentType;

    /** 이미지 설명 (alt text) */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** 관련 표 코드 (null이면 전체 일보 공통) */
    @Column(name = "TABLE_CODE", length = 50)
    private String tableCode;

    /** 정렬 순서 */
    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;

    /** 업로드자 ID (core_user 참조) */
    @Column(name = "UPLOADED_BY", nullable = false)
    private Long uploadedBy;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public DailyReportImage(String originalName, String storedPath, Long fileSize,
                            String contentType, String description, String tableCode,
                            Integer sortOrder, Long uploadedBy) {
        this.originalName = originalName;
        this.storedPath = storedPath;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.description = description;
        this.tableCode = tableCode;
        this.sortOrder = sortOrder;
        this.uploadedBy = uploadedBy;
    }

    /** 부모 일보 연결 (양방향 매핑용) */
    void assignReport(DailyReport dailyReport) {
        this.dailyReport = dailyReport;
    }

    /** 설명 수정 */
    public void updateDescription(String description) {
        this.description = description;
    }
}
