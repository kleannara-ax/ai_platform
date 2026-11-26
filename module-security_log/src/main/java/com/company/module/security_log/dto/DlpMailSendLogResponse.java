package com.company.module.security_log.dto;

import com.company.module.security_log.entity.SecLogDlpMailSendLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 메일 알림 보낸 기록 1행 */
@Getter
@Builder
public class DlpMailSendLogResponse {

    private Long mailSendLogId;
    private Long mailRuleId;
    private String ruleName;
    private String deptName;
    private String userName;
    private LocalDate endDate;
    private String toAddrs;
    private String ccAddrs;
    private String subject;
    private String body;
    private String sendStatus;
    private Integer tryCount;
    private LocalDateTime sentAt;
    private LocalDateTime lastTryAt;
    private String errorMessage;

    public static DlpMailSendLogResponse from(SecLogDlpMailSendLog l, String ruleName) {
        return DlpMailSendLogResponse.builder()
                .mailSendLogId(l.getMailSendLogId()).mailRuleId(l.getMailRuleId()).ruleName(ruleName)
                .deptName(l.getDeptName()).userName(l.getUserName()).endDate(l.getEndDate())
                .toAddrs(l.getToAddrs()).ccAddrs(l.getCcAddrs()).subject(l.getSubject()).body(l.getBody())
                .sendStatus(l.getSendStatus()).tryCount(l.getTryCount()).sentAt(l.getSentAt()).lastTryAt(l.getLastTryAt())
                .errorMessage(l.getErrorMessage()).build();
    }
}
