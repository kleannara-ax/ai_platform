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
 * DLP 모니터링 - 유출 로그 업로드 (월 1회, 같은 달은 가장 최근 파일만 집계)
 */
@Entity
@Table(name = "sec_log_dlp_leak_upload")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogDlpLeakUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LEAK_UPLOAD_ID")
    private Long leakUploadId;

    @Column(name = "TARGET_YM", nullable = false, length = 6)
    private String targetYm;

    @Column(name = "ORIGINAL_FILE_NAME", nullable = false, length = 255, updatable = false)
    private String originalFileName;

    @Column(name = "FILE_SIZE", nullable = false, updatable = false)
    private Long fileSize;

    @Column(name = "FILE_HASH", nullable = false, length = 64, updatable = false)
    private String fileHash;

    @Column(name = "SHEET_NAME", length = 100)
    private String sheetName;

    @Column(name = "HEADER_ROW")
    private Integer headerRow;

    @Column(name = "EVENT_COUNT", nullable = false)
    private Integer eventCount;

    @Column(name = "MATCHED_EVENT_COUNT", nullable = false)
    private Integer matchedEventCount;

    @Column(name = "USER_COUNT", nullable = false)
    private Integer userCount;

    @Column(name = "PERIOD_FROM")
    private LocalDateTime periodFrom;

    @Column(name = "PERIOD_TO")
    private LocalDateTime periodTo;

    @Column(name = "DATE_UNKNOWN_COUNT", nullable = false)
    private Integer dateUnknownCount;

    @Column(name = "OUT_OF_MONTH_COUNT", nullable = false)
    private Integer outOfMonthCount;

    @Column(name = "APPLIED_RULE_COUNT", nullable = false)
    private Integer appliedRuleCount;

    @Column(name = "ANALYSIS_NOTE", length = 1000)
    private String analysisNote;

    @Column(name = "ANALYZED_AT")
    private LocalDateTime analyzedAt;

    @Column(name = "REMARK", length = 1000)
    private String remark;

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
    private SecLogDlpLeakUpload(String targetYm, String originalFileName, Long fileSize,
                                String fileHash, String remark, Long createdBy) {
        this.targetYm = targetYm;
        this.originalFileName = originalFileName;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.remark = remark;
        this.createdBy = createdBy;
        this.eventCount = 0;
        this.matchedEventCount = 0;
        this.userCount = 0;
        this.dateUnknownCount = 0;
        this.outOfMonthCount = 0;
        this.appliedRuleCount = 0;
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

    /** 분석 결과 반영 (업로드·재분석) */
    public void applyAnalysis(String sheetName, Integer headerRow, int eventCount, int matchedEventCount, int userCount,
                              LocalDateTime periodFrom, LocalDateTime periodTo, int dateUnknownCount, int outOfMonthCount,
                              int appliedRuleCount, String analysisNote, Long updatedBy) {
        this.sheetName = sheetName;
        this.headerRow = headerRow;
        this.eventCount = eventCount;
        this.matchedEventCount = matchedEventCount;
        this.userCount = userCount;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.dateUnknownCount = dateUnknownCount;
        this.outOfMonthCount = outOfMonthCount;
        this.appliedRuleCount = appliedRuleCount;
        this.analysisNote = analysisNote;
        this.analyzedAt = SecLogTime.now();
        if (updatedBy != null) {
            this.updatedBy = updatedBy;
        }
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = SecLogTime.now();
    }
}
