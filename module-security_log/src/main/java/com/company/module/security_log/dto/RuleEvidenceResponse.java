package com.company.module.security_log.dto;

import com.company.module.security_log.entity.MatchType;
import com.company.module.security_log.entity.Severity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 룰셋 분석 근거 (업로드 1건 x 룰 1개) — 왜 검토 대상으로 잡혔는지
 */
@Getter
@Builder
public class RuleEvidenceResponse {

    private Long uploadId;
    private String systemName;
    private String targetYm;
    private String fileName;
    private LocalDateTime analyzedAt;
    /** 최종 검토완료(마감) 여부 */
    private boolean reviewLocked;

    /** DLP 관리자 감사 로그 여부 (true 면 검토 대상 = 계정별 묶음, 원본 엑셀 행·열 표시) */
    private boolean dlp;
    /** 행 번호 이름 (원본 엑셀 행 / 원본 파일 줄 / 로그 줄) */
    private String rowLabel;

    private RuleInfo rule;

    /** DONE / FAILED / NOT_RUN */
    private String status;
    private String statusLabel;
    private String failReason;
    /** 패턴 일치 줄 수 (분석 기록이 없으면 null) */
    private Integer matchCount;
    /** 탐지(저장) 건수 */
    private int detectionCount;
    private int detectionPending;

    /** 내부 IP 기준 (EXTERNAL_IP 룰만) — '이름 대역' */
    private List<String> internalIps;
    /** 왜 검토 대상인지 (개조식) */
    private List<String> reasons;
    /** 참고 (분석 이후 룰·내부 IP 변경 등) */
    private List<String> notes;

    private List<Evidence> items;
    /** 개인정보 다운로드 이력 (이 룰이 자사몰·임직원몰의 연동 룰일 때만, 아니면 null) */
    private PiiDownloadResponse pii;

    @Getter
    @Builder
    public static class RuleInfo {
        private String ruleCode;
        private String ruleName;
        private String description;
        private Severity severity;
        private String severityLabel;
        private MatchType matchType;
        private String matchTypeLabel;
        /** 찾는 방식 (쉬운 말) */
        private String howText;
        private String pattern;
        private Integer thresholdCount;
        /** 공통 / 시스템 전용 */
        private String scopeLabel;
        /** 마지막 분석 이후 룰 수정·삭제 여부 */
        private boolean changedAfterAnalysis;
        private boolean deleted;
    }

    /** 탐지 1건 = 로그 1줄 */
    @Getter
    @Builder
    public static class Evidence {
        private Long detectionId;
        private Integer lineNumber;
        /** 원본 행 번호 (DLP 엑셀 행, 그 외 = 로그 줄 번호) */
        private Integer row;
        /** 계정 (DLP, 읽지 못하면 '-') */
        private String account;
        private String name;
        private String time;
        private String ip;
        private String type;
        private String content;
        private String detail;
        /** 탐지 시점 로그 원문 (분석 엔진이 본 줄) */
        private String logContent;

        /** 현재 룰로 다시 확인했을 때 일치 여부 */
        private boolean matched;
        private String matchText;
        private int matchStart;
        private int matchEnd;
        /** EXTERNAL_IP 룰이 판정한 IP */
        private String matchIp;

        private String reviewStatus;
        private String reviewStatusLabel;
        private String actionContent;
        private LocalDateTime reviewedAt;
        private Long reviewedBy;
    }
}
