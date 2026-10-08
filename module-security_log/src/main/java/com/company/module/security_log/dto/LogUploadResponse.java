package com.company.module.security_log.dto;

import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.entity.Severity;
import com.company.module.security_log.entity.UploadStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 업로드 목록/등록 결과 응답 */
@Getter
@Builder
public class LogUploadResponse {

    private Long uploadId;
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

    public static LogUploadResponse from(SecLogUpload u) {
        return LogUploadResponse.builder()
                .uploadId(u.getUploadId())
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
