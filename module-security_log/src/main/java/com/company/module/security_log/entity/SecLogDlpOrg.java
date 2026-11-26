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

import java.time.LocalDateTime;

/**
 * DLP 관리 &gt; 사업부·부서 관리 - 카카오톡 승인 대장 드롭다운 목록
 * - PARENT_ORG_ID 0 = 사업부, 그 외 = 그 사업부 아래 부서
 * - 안 쓰는 항목은 USE_YN = 'N' (드롭다운에서만 빠짐)
 */
@Entity
@Table(name = "sec_log_dlp_org")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogDlpOrg {

    public static final long DIVISION_PARENT = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORG_ID")
    private Long orgId;

    @Column(name = "PARENT_ORG_ID", nullable = false, updatable = false)
    private Long parentOrgId;

    @Column(name = "ORG_NAME", nullable = false, length = 200)
    private String orgName;

    @Column(name = "USE_YN", nullable = false, length = 1)
    private String useYn;

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
    private SecLogDlpOrg(Long parentOrgId, String orgName, Long createdBy) {
        this.parentOrgId = parentOrgId == null ? DIVISION_PARENT : parentOrgId;
        this.orgName = orgName;
        this.useYn = "Y";
        this.createdBy = createdBy;
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

    public boolean isDivision() {
        return parentOrgId == null || parentOrgId == DIVISION_PARENT;
    }

    /** 이름·사용 여부 수정 */
    public void update(String orgName, String useYn, Long userId) {
        this.orgName = orgName;
        this.useYn = "N".equals(useYn) ? "N" : "Y";
        this.updatedBy = userId;
    }
}
