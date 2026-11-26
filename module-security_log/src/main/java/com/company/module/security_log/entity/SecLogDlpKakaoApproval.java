package com.company.module.security_log.entity;

import com.company.module.security_log.support.SecLogTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DLP 모니터링 - PC 카카오톡 사용 승인 대장 (전자결재 승인 건, 기존 엑셀 대장 대체)
 * - 상태(사용 중·만료·시작 전)·최신여부는 저장하지 않고 날짜로 계산
 */
@Entity
@Table(name = "sec_log_dlp_kakao_approval")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogDlpKakaoApproval {

    public static final String STATUS_APPLY = "신청중";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "KAKAO_APPROVAL_ID")
    private Long kakaoApprovalId;

    @Column(name = "DIVISION_NAME", length = 200)
    private String divisionName;

    @Column(name = "DEPT_NAME", nullable = false, length = 200)
    private String deptName;

    @Column(name = "USER_NAME", nullable = false, length = 200)
    private String userName;

    @Column(name = "START_DATE", nullable = false)
    private LocalDate startDate;

    @Column(name = "END_DATE", nullable = false)
    private LocalDate endDate;

    @Column(name = "APPLY_STATUS", nullable = false, length = 20)
    private String applyStatus;

    @Column(name = "APPROVAL_DOC_NO", length = 100)
    private String approvalDocNo;

    @Column(name = "REASON", length = 500)
    private String reason;

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
    private SecLogDlpKakaoApproval(String divisionName, String deptName, String userName, LocalDate startDate, LocalDate endDate,
                                   String approvalDocNo, String reason, Long createdBy) {
        this.divisionName = divisionName;
        this.deptName = deptName;
        this.userName = userName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.applyStatus = STATUS_APPLY;
        this.approvalDocNo = approvalDocNo;
        this.reason = reason;
        this.createdBy = createdBy;
        this.deletedYn = "N";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = SecLogTime.now();
        if (this.deletedYn == null) {
            this.deletedYn = "N";
        }
        if (this.applyStatus == null) {
            this.applyStatus = STATUS_APPLY;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = SecLogTime.now();
    }

    public void update(String divisionName, String deptName, String userName, LocalDate startDate, LocalDate endDate,
                       String approvalDocNo, String reason, Long updatedBy) {
        this.divisionName = divisionName;
        this.deptName = deptName;
        this.userName = userName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.approvalDocNo = approvalDocNo;
        this.reason = reason;
        this.updatedBy = updatedBy;
        this.updatedAt = SecLogTime.now();
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedAt = SecLogTime.now();
        this.deletedBy = deletedBy;
    }
}
