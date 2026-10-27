package com.company.module.personalinfo.dto;

import com.company.module.personalinfo.entity.PiAlertLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class AlertLogResponse {

    private Long alertId;
    private Long contractId;
    private String triggerType;
    private String alertStage;
    private LocalDate targetEndDate;
    private String recipients;
    private String subject;
    private String sendStatus;
    private LocalDateTime sentAt;
    private Integer retryCount;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createdAt;

    public static AlertLogResponse from(PiAlertLog a) {
        return AlertLogResponse.builder()
                .alertId(a.getAlertId())
                .contractId(a.getContractId())
                .triggerType(a.getTriggerType().name())
                .alertStage(a.getAlertStage())
                .targetEndDate(a.getTargetEndDate())
                .recipients(a.getRecipients())
                .subject(a.getSubject())
                .sendStatus(a.getSendStatus().name())
                .sentAt(a.getSentAt())
                .retryCount(a.getRetryCount())
                .errorMessage(a.getErrorMessage())
                .createdBy(a.getCreatedBy())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
