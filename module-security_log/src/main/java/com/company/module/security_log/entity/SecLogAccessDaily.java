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
 * 일자별 로그 집계 (업로드 1건 x 로그 일자 1일 = 1행)
 * - 대시보드 일별 수집 현황 달력, 일별 추이, 월 점검 결과 내보내기에 사용
 */
@Entity
@Table(name = "sec_log_access_daily")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogAccessDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DAILY_ID")
    private Long dailyId;

    @Column(name = "UPLOAD_ID", nullable = false, updatable = false)
    private Long uploadId;

    @Column(name = "SYSTEM_ID", updatable = false)
    private Long systemId;

    @Column(name = "LOG_DATE", nullable = false, updatable = false)
    private LocalDate logDate;

    @Column(name = "LINE_COUNT", nullable = false, updatable = false)
    private Integer lineCount;

    @Column(name = "LOGIN_SUCCESS_COUNT", nullable = false, updatable = false)
    private Integer loginSuccessCount;

    @Column(name = "LOGIN_FAIL_COUNT", nullable = false, updatable = false)
    private Integer loginFailCount;

    @Column(name = "OFF_HOUR_COUNT", nullable = false, updatable = false)
    private Integer offHourCount;

    @Column(name = "PRIV_LOGIN_COUNT", nullable = false, updatable = false)
    private Integer privLoginCount;

    @Column(name = "DETECTION_COUNT", nullable = false, updatable = false)
    private Integer detectionCount;

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
    private SecLogAccessDaily(Long uploadId, Long systemId, LocalDate logDate, Integer lineCount,
                              Integer loginSuccessCount, Integer loginFailCount, Integer offHourCount,
                              Integer privLoginCount, Integer detectionCount, Long createdBy) {
        this.uploadId = uploadId;
        this.systemId = systemId;
        this.logDate = logDate;
        this.lineCount = lineCount == null ? 0 : lineCount;
        this.loginSuccessCount = loginSuccessCount == null ? 0 : loginSuccessCount;
        this.loginFailCount = loginFailCount == null ? 0 : loginFailCount;
        this.offHourCount = offHourCount == null ? 0 : offHourCount;
        this.privLoginCount = privLoginCount == null ? 0 : privLoginCount;
        this.detectionCount = detectionCount == null ? 0 : detectionCount;
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
}
