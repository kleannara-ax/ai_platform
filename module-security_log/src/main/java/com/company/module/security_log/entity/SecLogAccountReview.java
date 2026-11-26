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
 * 접속 계정 검토 (접속 행위 분석의 계정별 소명·조치 기록)
 * - 업로드 1건 x 계정 1개당 사용 중인 행은 1개 (미검토로 되돌리면 소프트 삭제)
 */
@Entity
@Table(name = "sec_log_account_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogAccountReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ACCOUNT_REVIEW_ID")
    private Long accountReviewId;

    @Column(name = "UPLOAD_ID", nullable = false, updatable = false)
    private Long uploadId;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 200, updatable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "REVIEW_STATUS", nullable = false, length = 20)
    private AccountReviewStatus reviewStatus;

    @Column(name = "REVIEW_COMMENT", length = 2000)
    private String reviewComment;

    @Column(name = "RISK_FLAGS", length = 200)
    private String riskFlags;

    @Column(name = "REVIEWED_AT", nullable = false)
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
    private SecLogAccountReview(Long uploadId, String accountId, AccountReviewStatus reviewStatus,
                                String reviewComment, String riskFlags, Long reviewedBy) {
        this.uploadId = uploadId;
        this.accountId = accountId;
        this.reviewStatus = reviewStatus;
        this.reviewComment = reviewComment;
        this.riskFlags = riskFlags;
        this.reviewedAt = SecLogTime.now();
        this.reviewedBy = reviewedBy;
        this.createdBy = reviewedBy;
        this.deletedYn = "N";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = SecLogTime.now();
        if (this.deletedYn == null) {
            this.deletedYn = "N";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = SecLogTime.now();
    }

    /** 검토 내용 변경 (다시 검토) */
    public void review(AccountReviewStatus reviewStatus, String reviewComment, String riskFlags, Long reviewerId) {
        this.reviewStatus = reviewStatus;
        this.reviewComment = reviewComment;
        this.riskFlags = riskFlags;
        this.reviewedAt = SecLogTime.now();
        this.reviewedBy = reviewerId;
        this.updatedBy = reviewerId;
    }

    /** 미검토로 되돌리기 (소프트 삭제) */
    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = SecLogTime.now();
    }
}
