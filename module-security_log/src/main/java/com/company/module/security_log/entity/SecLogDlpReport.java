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
 * DLP 모니터링 보고서 표지 정보 (월별 1행) - 보고 구분 · 대상 시스템 · 작성 부서 · 회사 식별 단어
 */
@Entity
@Table(name = "sec_log_dlp_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogDlpReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DLP_REPORT_ID")
    private Long dlpReportId;

    @Column(name = "TARGET_YM", nullable = false, length = 6, updatable = false)
    private String targetYm;

    @Column(name = "REPORT_TYPE", length = 100)
    private String reportType;

    @Column(name = "TARGET_SYSTEM", length = 200)
    private String targetSystem;

    @Column(name = "WRITER_DEPT", length = 200)
    private String writerDept;

    @Column(name = "COMPANY_KEYWORDS", length = 500)
    private String companyKeywords;

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
    private SecLogDlpReport(String targetYm, String reportType, String targetSystem, String writerDept,
                            String companyKeywords, Long createdBy) {
        this.targetYm = targetYm;
        this.reportType = reportType;
        this.targetSystem = targetSystem;
        this.writerDept = writerDept;
        this.companyKeywords = companyKeywords;
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

    public void update(String reportType, String targetSystem, String writerDept, String companyKeywords, Long updatedBy) {
        this.reportType = reportType;
        this.targetSystem = targetSystem;
        this.writerDept = writerDept;
        this.companyKeywords = companyKeywords;
        this.updatedBy = updatedBy;
        this.updatedAt = SecLogTime.now();
    }
}
