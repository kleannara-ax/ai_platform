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
 * DLP 모니터링 보고서 항목별 입력값 (사람이 적는 칸)
 * - SECTION: USB_UNREG / KAKAO_USER / DEPT_HEADCOUNT / PI_REINSTALL / PI_OFFLINE / AI_USER
 * - 값을 지우면 행은 그대로 두고 값만 비움 (UK: 연월 + 항목 + 대상)
 */
@Entity
@Table(name = "sec_log_dlp_report_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogDlpReportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DLP_REPORT_ITEM_ID")
    private Long dlpReportItemId;

    @Column(name = "TARGET_YM", nullable = false, length = 6, updatable = false)
    private String targetYm;

    @Column(name = "SECTION", nullable = false, length = 30, updatable = false)
    private String section;

    @Column(name = "ITEM_KEY", nullable = false, length = 300, updatable = false)
    private String itemKey;

    @Column(name = "VALUE1", length = 100)
    private String value1;

    @Column(name = "VALUE2", length = 1000)
    private String value2;

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
    private SecLogDlpReportItem(String targetYm, String section, String itemKey, String value1, String value2, Long createdBy) {
        this.targetYm = targetYm;
        this.section = section;
        this.itemKey = itemKey;
        this.value1 = value1;
        this.value2 = value2;
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

    public void changeValues(String value1, String value2, Long updatedBy) {
        this.value1 = value1;
        this.value2 = value2;
        this.updatedBy = updatedBy;
        this.updatedAt = SecLogTime.now();
    }
}
