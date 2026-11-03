package com.company.module.security_log.dto;

import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.entity.Severity;
import com.company.module.security_log.entity.UploadStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** 업로드 목록/등록 결과 응답 */
@Getter
@Builder(toBuilder = true)
public class LogUploadResponse {

    private Long uploadId;
    private Long systemId;
    private String targetYm;
    private String systemName;
    private LogType logType;
    private String logTypeLabel;
    private String originalFileName;
    private Long fileSize;
    private String fileHash;
    private String fileCharset;
    private Integer totalLineCount;
    private Integer detectionCount;
    private Integer criticalCount;
    private Integer highCount;
    private Integer mediumCount;
    private Integer lowCount;
    private Severity maxSeverity;
    private String maxSeverityLabel;
    private UploadStatus status;
    private String statusLabel;
    private LocalDateTime analyzedAt;
    private String remark;
    private LocalDateTime createdAt;
    private Long createdBy;

    // ---- 목록 화면 전용 (업로드 상세·등록 응답에서는 null)

    /** 검토 상태별 탐지 건수 */
    private Long pendingCount;
    private Long confirmedCount;
    private Long resolvedCount;
    private Long falsePositiveCount;

    /** 로그 기간 확인: OK / CHECK(확인 필요) / UNCHECKED(일자 점검 전) */
    private String periodStatus;
    /** 기간 확인 사유 (예: "9/5부터 시작", "대상 월 밖 줄 79") */
    private List<String> periodReasons;
    /** 대상 월 안의 첫/마지막 로그일 (yyyy-MM-dd) */
    private String firstLogDate;
    private String lastLogDate;

    public static LogUploadResponse from(SecLogUpload u) {
        return LogUploadResponse.builder()
                .uploadId(u.getUploadId())
                .systemId(u.getSystemId())
                .targetYm(u.getTargetYm())
                .systemName(u.getSystemName())
                .logType(u.getLogType())
                .logTypeLabel(u.getLogType().getLabel())
                .originalFileName(u.getOriginalFileName())
                .fileSize(u.getFileSize())
                .fileHash(u.getFileHash())
                .fileCharset(u.getFileCharset())
                .totalLineCount(u.getTotalLineCount())
                .detectionCount(u.getDetectionCount())
                .criticalCount(u.getCriticalCount())
                .highCount(u.getHighCount())
                .mediumCount(u.getMediumCount())
                .lowCount(u.getLowCount())
                .maxSeverity(u.getMaxSeverity())
                .maxSeverityLabel(u.getMaxSeverity() == null ? "탐지없음" : u.getMaxSeverity().getLabel())
                .status(u.getStatus())
                .statusLabel(u.getStatus().getLabel())
                .analyzedAt(u.getAnalyzedAt())
                .remark(u.getRemark())
                .createdAt(u.getCreatedAt())
                .createdBy(u.getCreatedBy())
                .build();
    }
}
