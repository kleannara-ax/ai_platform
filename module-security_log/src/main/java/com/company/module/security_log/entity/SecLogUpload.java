package com.company.module.security_log.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 시스템 로그 업로드 이력 (분석 결과 요약 포함)
 */
@Entity
@Table(name = "sec_log_upload")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UPLOAD_ID")
    private Long uploadId;

    /** 대상 연월 (YYYYMM) - 월별 조회 기준 */
    @Column(name = "TARGET_YM", nullable = false, length = 6)
    private String targetYm;

    @Column(name = "SYSTEM_NAME", nullable = false, length = 100)
    private String systemName;

    @Enumerated(EnumType.STRING)
    @Column(name = "LOG_TYPE", nullable = false, length = 20)
    private LogType logType;

    @Column(name = "ORIGINAL_FILE_NAME", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;

    @Column(name = "FILE_HASH", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "FILE_CHARSET", nullable = false, length = 20)
    private String fileCharset;

    @Column(name = "TOTAL_LINE_COUNT", nullable = false)
    private Integer totalLineCount;

    @Column(name = "DETECTION_COUNT", nullable = false)
    private Integer detectionCount;

    @Column(name = "CRITICAL_COUNT", nullable = false)
    private Integer criticalCount;

    @Column(name = "HIGH_COUNT", nullable = false)
    private Integer highCount;

    @Column(name = "MEDIUM_COUNT", nullable = false)
    private Integer mediumCount;

    @Column(name = "LOW_COUNT", nullable = false)
    private Integer lowCount;

    /** 탐지 결과 중 최고 위험도 (탐지 0건이면 NULL) */
    @Enumerated(EnumType.STRING)
    @Column(name = "MAX_SEVERITY", length = 20)
    private Severity maxSeverity;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private UploadStatus status;

    @Column(name = "ANALYZED_AT")
    private LocalDateTime analyzedAt;

    @Column(name = "REMARK", length = 1000)
    private String remark;

    @Column(name = "REVIEW_COMMENT", length = 2000)
    private String reviewComment;

    @Column(name = "REVIEWED_AT")
    private LocalDateTime reviewedAt;

    @Column(name = "REVIEWED_BY")
    private Long reviewedBy;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", updatable = false)
    private Long createdBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "DELETED_YN", nullable = false, length = 1)
    private String deletedYn;

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    @Column(name = "DELETED_BY")
    private Long deletedBy;

    @Builder
    private SecLogUpload(String targetYm, String systemName, LogType logType, String originalFileName,
                         Long fileSize, String fileHash, String fileCharset, String remark, Long createdBy) {
        this.targetYm = targetYm;
        this.systemName = systemName;
        this.logType = logType;
        this.originalFileName = originalFileName;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.fileCharset = fileCharset;
        this.remark = remark;
        this.createdBy = createdBy;
        this.totalLineCount = 0;
        this.detectionCount = 0;
        this.criticalCount = 0;
        this.highCount = 0;
        this.mediumCount = 0;
        this.lowCount = 0;
        this.status = UploadStatus.ANALYZED;
        this.deletedYn = "N";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.deletedYn == null) {
            this.deletedYn = "N";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 분석 결과 반영 (최초 분석 / 재분석 공통) */
    public void applyAnalysisResult(int totalLineCount, int criticalCount, int highCount,
                                    int mediumCount, int lowCount, Long updatedBy) {
        this.totalLineCount = totalLineCount;
        this.criticalCount = criticalCount;
        this.highCount = highCount;
        this.mediumCount = mediumCount;
        this.lowCount = lowCount;
        this.detectionCount = criticalCount + highCount + mediumCount + lowCount;
        if (criticalCount > 0) {
            this.maxSeverity = Severity.CRITICAL;
        } else if (highCount > 0) {
            this.maxSeverity = Severity.HIGH;
        } else if (mediumCount > 0) {
            this.maxSeverity = Severity.MEDIUM;
        } else if (lowCount > 0) {
            this.maxSeverity = Severity.LOW;
        } else {
            this.maxSeverity = null;
        }
        this.status = UploadStatus.ANALYZED;
        this.analyzedAt = LocalDateTime.now();
        this.reviewComment = null;
        this.reviewedAt = null;
        this.reviewedBy = null;
        if (updatedBy != null) {
            this.updatedBy = updatedBy;
        }
    }

    /** 업로드 기본정보 수정 (파일 자체는 변경 불가) */
    public void update(String targetYm, String systemName, LogType logType, String remark, Long updatedBy) {
        this.targetYm = targetYm;
        this.systemName = systemName;
        this.logType = logType;
        this.remark = remark;
        this.updatedBy = updatedBy;
    }

    /** 정보보안팀 최종 검토 완료 */
    public void completeReview(String reviewComment, Long reviewerId) {
        this.status = UploadStatus.REVIEWED;
        this.reviewComment = reviewComment;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = reviewerId;
        this.updatedBy = reviewerId;
    }

    /** 검토 완료 취소 (재검토) */
    public void cancelReview(Long updatedBy) {
        this.status = UploadStatus.ANALYZED;
        this.reviewedAt = null;
        this.reviewedBy = null;
        this.updatedBy = updatedBy;
    }

    public boolean isReviewed() {
        return this.status == UploadStatus.REVIEWED;
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore(Long updatedBy) {
        this.deletedYn = "N";
        this.deletedBy = null;
        this.deletedAt = null;
        this.updatedBy = updatedBy;
    }
}
