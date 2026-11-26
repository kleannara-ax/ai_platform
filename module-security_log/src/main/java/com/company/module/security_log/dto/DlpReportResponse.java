package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * DLP 관리자 감사 로그 점검 보고서 (탐지 룰셋 기반)
 * - [관리 › 탐지 룰셋 편집]에서 이 시스템에 적용되는 룰로 탐지한 결과를 '룰 × 계정' 단위로 묶어 보여 준다.
 * - 위험도 = 룰에 지정한 위험도 (심각·높음 → 🔴, 보통 → 🟡, 낮음 → 🟢)
 * - 항목 검토 = 그 항목에 묶인 탐지 건 검토 (최종 검토완료 조건과 같은 기록)
 * - 근거 로그 = 원본 파일의 행 번호 (엑셀은 엑셀 행 번호, 텍스트는 줄 번호)
 */
@Getter
@Builder
public class DlpReportResponse {

    /** 보고서를 만들 수 있는 로그인지 (DLP 시스템 + 관리자 감사 로그 형식) */
    private boolean applicable;
    /** applicable=false 인 이유 */
    private String reason;

    private Long uploadId;
    private String systemName;
    private String targetYm;
    private String fileName;
    /** 근거 로그 번호 설명 (예: "원본 엑셀 행 번호") */
    private String rowLabel;

    // ---- 1. 한눈에 보기
    private int totalRows;
    private int accountCount;
    private String periodFrom;
    private String periodTo;
    /** 항목 수 (🔴 = 심각+높음 / 🟡 = 보통 / 🟢 = 낮음) */
    private int highCount;
    private int mediumCount;
    private int lowCount;
    /** 탐지 건수 (전체 / 미검토) */
    private int detectionTotal;
    private int detectionPending;
    /** 이번 달 핵심 (2~3줄) */
    private List<String> summaryLines;

    // ---- 2~4. 검토 항목 (높음 → 보통 → 낮음, 번호 순)
    private List<Item> items;
    // ---- 5. 룰별 결과 (적용 룰 전체, 0건 포함)
    private List<RuleResult> rules;
    // ---- 6. 계정별 요약
    private List<Account> accounts;
    // ---- 7. 분석 한계
    private List<String> limits;
    /** 로그 내 의심 문구(지시문처럼 보이는 문장) 점검 결과 한 줄 */
    private String suspiciousNote;

    // ---- 검토 현황 (항목 안의 탐지 건이 모두 검토되면 검토 완료)
    private int highReviewed;
    private int mediumReviewed;
    private int lowReviewed;
    /** 이 업로드가 최종 검토완료되어 검토를 바꿀 수 없는지 */
    private boolean reviewLocked;

    @Getter
    @Builder
    public static class Item {
        private int no;
        /** 항목 키 = 룰 코드|계정 */
        private String itemKey;
        /** 보고서 구분: HIGH(심각·높음) / MEDIUM / LOW */
        private String level;
        /** 룰 위험도: CRITICAL / HIGH / MEDIUM / LOW */
        private String severity;
        private String severityLabel;
        private String ruleCode;
        private String ruleName;
        /** 룰 설명 (점검 포인트) */
        private String ruleDescription;
        private String account;
        private String name;
        /** 한 줄 제목 (🔴 카드 제목) */
        private String title;
        /** 표에 쓰는 내용 (보통·낮음) */
        private String content;
        private int count;
        private String countText;
        /** '항목: 값' 여러 줄 (🔴 카드 '무슨 일') */
        private String what;
        /** 근거 로그 행 번호 (최대 200개) */
        private List<Integer> evidenceRows;
        /** 근거 로그 전체 개수 */
        private int evidenceTotal;
        /** 이 항목에 묶인 탐지 ID (검토 저장용) */
        private List<Long> detectionIds;
        private int pendingCount;

        // ---- 검토 (탐지 건 검토 결과 요약)
        /** null(미검토) / PARTIAL(일부 검토) / CONFIRMED / RESOLVED / FALSE_POSITIVE / MIXED(검토 결과가 섞임) */
        private String reviewStatus;
        private String reviewStatusLabel;
        private String reviewComment;
        private String reviewedAt;
        private Long reviewedBy;
    }

    @Getter
    @Builder
    public static class RuleResult {
        private String ruleCode;
        private String ruleName;
        private String severity;
        private String severityLabel;
        /** 범위: 공통 / 전용 */
        private String scope;
        private int thresholdCount;
        private int hitCount;
        private int accountCount;
        private int pendingCount;
    }

    @Getter
    @Builder
    public static class Account {
        private String account;
        private String name;
        private int activity;
        private int high;
        private int medium;
        private int low;
        private String summary;
    }
}
