package com.company.module.security_log.dto;

import com.company.module.security_log.entity.ReviewStatus;
import com.company.module.security_log.entity.SecLogDetection;
import com.company.module.security_log.entity.Severity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DetectionResponse {

    private Long detectionId;
    private Long uploadId;
    private Long ruleId;
    private String ruleCode;
    private String ruleName;
    private Severity severity;
    private String severityLabel;
    private Integer lineNumber;
    private String logContent;
    private ReviewStatus reviewStatus;
    private String reviewStatusLabel;
    private String actionContent;
    private LocalDateTime reviewedAt;
    private Long reviewedBy;
    private LocalDateTime createdAt;

    public static DetectionResponse from(SecLogDetection d) {
        return DetectionResponse.builder()
                .detectionId(d.getDetectionId())
                .uploadId(d.getUploadId())
                .ruleId(d.getRuleId())
                .ruleCode(d.getRuleCode())
                .ruleName(d.getRuleName())
                .severity(d.getSeverity())
                .severityLabel(d.getSeverity().getLabel())
                .lineNumber(d.getLineNumber())
                .logContent(d.getLogContent())
                .reviewStatus(d.getReviewStatus())
                .reviewStatusLabel(d.getReviewStatus().getLabel())
                .actionContent(d.getActionContent())
                .reviewedAt(d.getReviewedAt())
                .reviewedBy(d.getReviewedBy())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
