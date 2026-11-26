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
 * Privacy-I 재설치 / 장기 미접속 목록 1행 (DLP 관리시스템에서 받은 파일, 다시 올리면 이전 행 소프트 삭제)
 */
@Entity
@Table(name = "sec_log_dlp_privacyi_row")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogDlpPrivacyiRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRIVACYI_ROW_ID")
    private Long privacyiRowId;

    @Column(name = "TARGET_YM", nullable = false, length = 6, updatable = false)
    private String targetYm;

    /** REINSTALL 재설치 / OFFLINE 장기 미접속 */
    @Column(name = "KIND", nullable = false, length = 20, updatable = false)
    private String kind;

    @Column(name = "FILE_NAME", nullable = false, length = 255, updatable = false)
    private String fileName;

    @Column(name = "ROW_NO", nullable = false, updatable = false)
    private Integer rowNo;

    @Column(name = "DEPT_NAME", length = 200, updatable = false)
    private String deptName;

    @Column(name = "USER_NAME", length = 200, updatable = false)
    private String userName;

    @Column(name = "USER_ID", length = 200, updatable = false)
    private String userId;

    @Column(name = "EVENT_AT", updatable = false)
    private LocalDateTime eventAt;

    @Column(name = "EVENT_AT_TEXT", length = 50, updatable = false)
    private String eventAtText;

    @Column(name = "OFFLINE_DAYS", updatable = false)
    private Integer offlineDays;

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
    private SecLogDlpPrivacyiRow(String targetYm, String kind, String fileName, Integer rowNo, String deptName,
                                 String userName, String userId, LocalDateTime eventAt, String eventAtText,
                                 Integer offlineDays, Long createdBy) {
        this.targetYm = targetYm;
        this.kind = kind;
        this.fileName = fileName;
        this.rowNo = rowNo;
        this.deptName = deptName;
        this.userName = userName;
        this.userId = userId;
        this.eventAt = eventAt;
        this.eventAtText = eventAtText;
        this.offlineDays = offlineDays;
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
}
