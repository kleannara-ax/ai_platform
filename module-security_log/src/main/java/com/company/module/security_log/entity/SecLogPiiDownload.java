package com.company.module.security_log.entity;

import com.company.module.security_log.support.SecLogTime;
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
 * 개인정보 다운로드 내역 + 다운로드 사유 검토 (자사몰·임직원몰)
 * - 업로드한 엑셀의 '개인정보 다운로드 내역' 탭 1행 = 1건
 * - 재분석·업로드 삭제 시 소프트 삭제
 */
@Entity
@Table(name = "sec_log_pii_download")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogPiiDownload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PII_DOWNLOAD_ID")
    private Long piiDownloadId;

    @Column(name = "UPLOAD_ID", nullable = false, updatable = false)
    private Long uploadId;

    @Column(name = "SHEET_NAME", length = 100, updatable = false)
    private String sheetName;

    @Column(name = "ROW_NO", nullable = false, updatable = false)
    private Integer rowNo;

    @Column(name = "DOWNLOAD_AT", updatable = false)
    private LocalDateTime downloadAt;

    @Column(name = "DOWNLOAD_AT_TEXT", length = 50, updatable = false)
    private String downloadAtText;

    @Column(name = "ADMIN_ID", length = 200, updatable = false)
    private String adminId;

    @Column(name = "ACCESS_IP", length = 100, updatable = false)
    private String accessIp;

    @Column(name = "ACCESS_PAGE", length = 500, updatable = false)
    private String accessPage;

    @Column(name = "TASK_NAME", length = 500, updatable = false)
    private String taskName;

    @Column(name = "FILE_TARGET", length = 500, updatable = false)
    private String fileTarget;

    @Column(name = "FORM_NAME", length = 500, updatable = false)
    private String formName;

    @Column(name = "DOWNLOAD_REASON", length = 1000, updatable = false)
    private String downloadReason;

    @Column(name = "RAW_CONTENT", nullable = false, length = 2000, updatable = false)
    private String rawContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "REVIEW_STATUS", nullable = false, length = 20)
    private PiiReviewStatus reviewStatus;

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
    private SecLogPiiDownload(Long uploadId, String sheetName, Integer rowNo, LocalDateTime downloadAt,
                              String downloadAtText, String adminId, String accessIp, String accessPage,
                              String taskName, String fileTarget, String formName, String downloadReason,
                              String rawContent, Long createdBy) {
        this.uploadId = uploadId;
        this.sheetName = sheetName;
        this.rowNo = rowNo;
        this.downloadAt = downloadAt;
        this.downloadAtText = downloadAtText;
        this.adminId = adminId;
        this.accessIp = accessIp;
        this.accessPage = accessPage;
        this.taskName = taskName;
        this.fileTarget = fileTarget;
        this.formName = formName;
        this.downloadReason = downloadReason;
        this.rawContent = rawContent;
        this.reviewStatus = PiiReviewStatus.PENDING;
        this.createdBy = createdBy;
        this.deletedYn = "N";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = SecLogTime.now();
        if (this.deletedYn == null) {
            this.deletedYn = "N";
        }
        if (this.reviewStatus == null) {
            this.reviewStatus = PiiReviewStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = SecLogTime.now();
    }

    /** 다운로드 사유 검토 (PENDING 이면 미검토로 되돌림) */
    public void review(PiiReviewStatus status, String comment, Long reviewerId) {
        this.reviewStatus = status;
        if (status == PiiReviewStatus.PENDING) {
            this.reviewComment = null;
            this.reviewedAt = null;
            this.reviewedBy = null;
        } else {
            this.reviewComment = comment;
            this.reviewedAt = SecLogTime.now();
            this.reviewedBy = reviewerId;
        }
        this.updatedBy = reviewerId;
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = SecLogTime.now();
    }
}
