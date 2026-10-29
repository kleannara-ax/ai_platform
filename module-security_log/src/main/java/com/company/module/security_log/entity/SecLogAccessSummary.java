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
 * 접속 행위 분석 결과 (업로드 1건 x 계정 1개 = 1행)
 * - 로그에서 로그인 성공/실패 이벤트를 추출해 계정별로 집계한 스냅샷
 */
@Entity
@Table(name = "sec_log_access_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogAccessSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SUMMARY_ID")
    private Long summaryId;

    @Column(name = "UPLOAD_ID", nullable = false, updatable = false)
    private Long uploadId;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 200, updatable = false)
    private String accountId;

    @Column(name = "SUCCESS_COUNT", nullable = false, updatable = false)
    private Integer successCount;

    @Column(name = "FAIL_COUNT", nullable = false, updatable = false)
    private Integer failCount;

    @Column(name = "OFF_HOUR_COUNT", nullable = false, updatable = false)
    private Integer offHourCount;

    @Column(name = "EXTERNAL_IP_COUNT", nullable = false, updatable = false)
    private Integer externalIpCount;

    @Column(name = "IP_COUNT", nullable = false, updatable = false)
    private Integer ipCount;

    @Column(name = "IP_LIST", length = 1000, updatable = false)
    private String ipList;

    @Column(name = "FIRST_SEEN", length = 50, updatable = false)
    private String firstSeen;

    @Column(name = "LAST_SEEN", length = 50, updatable = false)
    private String lastSeen;

    /** 위험 표시 (쉼표 구분): FAIL_MANY, OFF_HOUR, EXTERNAL_IP, MULTI_IP, PRIV_ACCOUNT */
    @Column(name = "RISK_FLAGS", length = 200, updatable = false)
    private String riskFlags;

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
    private SecLogAccessSummary(Long uploadId, String accountId, Integer successCount, Integer failCount,
                                Integer offHourCount, Integer externalIpCount, Integer ipCount, String ipList,
                                String firstSeen, String lastSeen, String riskFlags, Long createdBy) {
        this.uploadId = uploadId;
        this.accountId = accountId;
        this.successCount = successCount;
        this.failCount = failCount;
        this.offHourCount = offHourCount;
        this.externalIpCount = externalIpCount;
        this.ipCount = ipCount;
        this.ipList = ipList;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.riskFlags = riskFlags;
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

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = SecLogTime.now();
    }

    public void restore(Long updatedBy) {
        this.deletedYn = "N";
        this.deletedBy = null;
        this.deletedAt = null;
        this.updatedBy = updatedBy;
    }
}
