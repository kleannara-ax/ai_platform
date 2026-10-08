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
 * 이상징후 탐지 결과 (로그 라인 단위)
 * - 룰 코드/룰명/위험도는 탐지 시점 값을 스냅샷으로 보관 (이후 룰 수정과 무관하게 이력 보존)
 */
@Entity
@Table(name = "sec_log_detection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DETECTION_ID")
    private Long detectionId;

    @Column(name = "UPLOAD_ID", nullable = false, updatable = false)
    private Long uploadId;

    @Column(name = "RULE_ID", nullable = false, updatable = false)
    private Long ruleId;

    @Column(name = "RULE_CODE", nullable = false, length = 50, updatable = false)
    private String ruleCode;

    @Column(name = "RULE_NAME", nullable = false, length = 200, updatable = false)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "SEVERITY", nullable = false, length = 20, updatable = false)
    private Severity severity;

    @Column(name = "LINE_NUMBER", nullable = false, updatable = false)
    private Integer lineNumber;

    @Column(name = "LOG_CONTENT", nullable = false, length = 2000, updatable = false)
    private String logContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "REVIEW_STATUS", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    @Column(name = "ACTION_CONTENT", length = 2000)
    private String actionContent;

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
    private SecLogDetection(Long uploadId, Long ruleId, String ruleCode, String ruleName, Severity severity,
                            Integer lineNumber, String logContent, Long createdBy) {
        this.uploadId = uploadId;
        this.ruleId = ruleId;
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.severity = severity;
        this.lineNumber = lineNumber;
        this.logContent = logContent;
        this.createdBy = createdBy;
        this.reviewStatus = ReviewStatus.PENDING;
        this.deletedYn = "N";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.deletedYn == null) {
            this.deletedYn = "N";
        }
        if (this.reviewStatus == null) {
            this.reviewStatus = ReviewStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 탐지 건 검토/조치 내용 등록 */
    public void review(ReviewStatus reviewStatus, String actionContent, Long reviewerId) {
        this.reviewStatus = reviewStatus;
        this.actionContent = actionContent;
        if (reviewStatus == ReviewStatus.PENDING) {
            this.reviewedAt = null;
            this.reviewedBy = null;
        } else {
            this.reviewedAt = LocalDateTime.now();
            this.reviewedBy = reviewerId;
        }
        this.updatedBy = reviewerId;
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
