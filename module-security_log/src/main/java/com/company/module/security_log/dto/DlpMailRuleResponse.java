package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 메일 알림 관리 화면 (메일 서버 상태 + 규칙 목록 + 자리표시 안내) */
@Getter
@Builder
public class DlpMailRuleResponse {

    /** 메일 서버(SMTP) 서버 환경 변수 설정 여부 */
    private boolean smtpReady;
    /** 보내는 주소 (SECLOG_MAIL_FROM, 없으면 SMTP 계정) */
    private String fromAddr;
    /** 받을 수 있는 회사 메일 도메인 (SECLOG_MAIL_ALLOWED_DOMAINS, 비어 있으면 아무 데도 보내지 않음) */
    private List<String> allowedDomains;
    /** 제목·본문에 쓸 수 있는 자리표시 (예: {부서} = 부서) */
    private List<Placeholder> placeholders;
    private List<Rule> rules;

    @Getter
    @Builder
    public static class Placeholder {
        private String key;
        private String desc;
    }

    @Getter
    @Builder
    public static class Rule {
        private Long mailRuleId;
        /** SQL 로 넣은 기본 규칙 여부 */
        private boolean basic;
        private String ruleName;
        private Integer offsetDays;
        /** 예: 신청 종료일 + 1일 */
        private String offsetText;
        private String sendTime;
        private String toAddrs;
        private String ccAddrs;
        private String subjectTpl;
        private String bodyTpl;
        private String useYn;
        private LocalDate startDate;
        private LocalDateTime lastRunAt;
        private String lastRunResult;
        /** 보낼 차례인데 아직 못 보낸 사람 수 (실패·메일 서버 미설정 포함) */
        private long pendingCount;
        private long sentCount;
        private long failCount;
    }
}
