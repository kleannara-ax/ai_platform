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
 * DLP 모니터링 - 유출 이벤트 (원본 1행 = 1건. MATCHED_RULES·MAX_SEVERITY 칸은 룰셋 삭제 후 쓰지 않음 — 기존 데이터 보존용)
 * - 원본의 '... 샘플 데이터' 열(개인정보 원문 일부)은 저장하지 않음 (개수 열만 PII_TOTAL·PII_DETAIL)
 * - 재분석·업로드 삭제 시 소프트 삭제
 */
@Entity
@Table(name = "sec_log_dlp_leak_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogDlpLeakEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LEAK_EVENT_ID")
    private Long leakEventId;

    @Column(name = "LEAK_UPLOAD_ID", nullable = false, updatable = false)
    private Long leakUploadId;

    @Column(name = "ROW_NO", nullable = false, updatable = false)
    private Integer rowNo;

    @Column(name = "OCCURRED_AT", updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "OCCURRED_AT_TEXT", length = 50, updatable = false)
    private String occurredAtText;

    @Column(name = "ACTION_NAME", length = 100, updatable = false)
    private String actionName;

    @Column(name = "DEPT_NAME", length = 200, updatable = false)
    private String deptName;

    @Column(name = "USER_NAME", length = 200, updatable = false)
    private String userName;

    @Column(name = "USER_ID", length = 200, updatable = false)
    private String userId;

    @Column(name = "AGENT_IP", length = 100, updatable = false)
    private String agentIp;

    @Column(name = "CHANNEL", length = 100, updatable = false)
    private String channel;

    @Column(name = "SUB_CHANNEL", length = 200, updatable = false)
    private String subChannel;

    @Column(name = "FILE_SIZE_TEXT", length = 50, updatable = false)
    private String fileSizeText;

    @Column(name = "FILE_LIST", length = 1000, updatable = false)
    private String fileList;

    @Column(name = "CONTENT", length = 2000, updatable = false)
    private String content;

    @Column(name = "DETAIL", length = 1000, updatable = false)
    private String detail;

    @Column(name = "POLICY_NAME", length = 200, updatable = false)
    private String policyName;

    @Column(name = "EVENT_STATUS", length = 50, updatable = false)
    private String eventStatus;

    @Column(name = "MEDIA_REGISTERED", length = 50, updatable = false)
    private String mediaRegistered;

    @Column(name = "COMPUTER_NAME", length = 200, updatable = false)
    private String computerName;

    @Column(name = "PII_TOTAL", nullable = false, updatable = false)
    private Integer piiTotal;

    @Column(name = "PII_DETAIL", length = 500, updatable = false)
    private String piiDetail;

    @Column(name = "MATCHED_RULES", length = 1000, updatable = false)
    private String matchedRules;

    @Enumerated(EnumType.STRING)
    @Column(name = "MAX_SEVERITY", length = 20, updatable = false)
    private Severity maxSeverity;

    @Column(name = "AI_SERVICE", length = 100, updatable = false)
    private String aiService;

    @Column(name = "ANALYSIS_LINE", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String analysisLine;

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
    private SecLogDlpLeakEvent(Long leakUploadId, Integer rowNo, LocalDateTime occurredAt, String occurredAtText,
                               String actionName, String deptName, String userName, String userId, String agentIp,
                               String channel, String subChannel, String fileSizeText, String fileList, String content,
                               String detail, String policyName, String eventStatus, String mediaRegistered,
                               String computerName, Integer piiTotal, String piiDetail, String matchedRules,
                               Severity maxSeverity, String aiService, String analysisLine, Long createdBy) {
        this.leakUploadId = leakUploadId;
        this.rowNo = rowNo;
        this.occurredAt = occurredAt;
        this.occurredAtText = occurredAtText;
        this.actionName = actionName;
        this.deptName = deptName;
        this.userName = userName;
        this.userId = userId;
        this.agentIp = agentIp;
        this.channel = channel;
        this.subChannel = subChannel;
        this.fileSizeText = fileSizeText;
        this.fileList = fileList;
        this.content = content;
        this.detail = detail;
        this.policyName = policyName;
        this.eventStatus = eventStatus;
        this.mediaRegistered = mediaRegistered;
        this.computerName = computerName;
        this.piiTotal = piiTotal == null ? 0 : piiTotal;
        this.piiDetail = piiDetail;
        this.matchedRules = matchedRules;
        this.maxSeverity = maxSeverity;
        this.aiService = aiService;
        this.analysisLine = analysisLine;
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
