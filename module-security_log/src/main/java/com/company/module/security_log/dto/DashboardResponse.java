package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** ISMS 로그 점검 대시보드 */
@Getter
@Builder
public class DashboardResponse {

    private String targetYm;
    private LocalDateTime generatedAt;

    // ---- KPI ----
    private int systemCount;
    private int accessRegistered;
    private int accessReviewed;
    /** 월 마감률: 접속·행위 로그 검토완료 시스템 / 시스템 수 */
    private int closeRate;
    private long riskAccountCount;
    private long pendingDetectionCount;
    private long detectionCount;
    private long criticalCount;
    private long highCount;
    private long mediumCount;
    private long lowCount;

    // ---- 목록 ----
    private List<RiskAccount> topRiskAccounts;
    private List<Todo> todos;
    private List<Trend> trend;
    private List<RuleHitCount> topRules;
    private List<MonthlyStatusResponse.Row> systems;

    // ---- 2. 마감 D-day ----
    /** 마감 기준일 (대상 월의 다음 달 마지막 영업일 전 기준: 다음 달 10일) */
    private String closeDueDate;
    /** 오늘 기준 남은 일수 (음수면 마감일 지남) */
    private long closeDDay;
    /** 마감까지 남은 시스템 수 (접속·행위 로그 미검토완료) */
    private int closeRemainCount;
    private boolean closed;

    // ---- 1. 로그 업로드 현황 ----
    private UploadSummary uploadSummary;
    private List<UploadRow> uploadRows;

    // ---- 일별 수집 현황 달력 (시스템 상세 창·엑셀) ----
    private List<String> calendarDays;
    private List<CalendarRow> calendar;

    // ---- 3. 미검토 지연 ----
    private Aging aging;

    // ---- 6. 특권 계정 활동 ----
    private List<PrivAccount> privAccounts;

    // ---- 검토 현황 (시스템별) ----
    private List<ReviewRow> reviews;
    /** 탐지 검토 상태별 합계 (PENDING/CONFIRMED/RESOLVED/FALSE_POSITIVE) */
    private Map<String, Long> reviewCounts;

    // ---- 이번 달 일별 추이 ----
    private List<DailyTrend> dailyTrend;

    @Getter
    @Builder
    public static class RiskAccount {
        private Long systemId;
        private String systemName;
        private String accountId;
        private long successCount;
        private long failCount;
        private long offHourCount;
        private long externalIpCount;
        private List<String> riskFlags;
        private String lastSeen;
        private Long latestUploadId;
    }

    @Getter
    @Builder
    public static class Todo {
        /** DANGER / WARN / INFO */
        private String level;
        /** NO_DATA / COLLECT_ERROR / PENDING_DETECTION / PENDING_ACTION / READY_TO_CLOSE */
        private String kind;
        private Long systemId;
        private String systemName;
        private String message;
        private String link;
    }

    @Getter
    @Builder
    public static class Trend {
        private String targetYm;
        private long detectionCount;
        private long highPlus;
        private long riskAccounts;
        private int closeRate;
    }

    /** 일별 수집 현황 달력 1행 (시스템) */
    @Getter
    @Builder
    public static class CalendarRow {
        private Long systemId;
        private String systemName;
        private String collectMode;
        /** 날짜별 상태: OK(로그 있음) / EMPTY(수집됨·활동 없음) / FAIL(수집 실패) / MISS(누락) / FUTURE(아직) */
        private List<String> status;
        /** 날짜별 로그 줄 수 */
        private List<Integer> counts;
        private int okDays;
        /** 수집은 됐지만 활동(로그)이 없는 날 */
        private int emptyDays;
        private int missDays;
        private int failDays;
        /** 대상 일수 (어제까지 또는 월말까지) */
        private int dueDays;
        /** 수집률 = (로그 있음 + 활동 없음) / 대상 일수 */
        private int coverage;
    }

    /** 로그 업로드 현황 요약 */
    @Getter
    @Builder
    public static class UploadSummary {
        private int systemCount;
        /** 그 달 로그 파일을 1개 이상 올린 시스템 수 */
        private int uploadedCount;
        private int missingCount;
        /** 대상 월이 끝났는지 (끝나기 전 미업로드는 '대기') */
        private boolean monthOver;
        /** 로그 기간·일자 확인이 필요한 시스템 수 */
        private int checkCount;
        /** 로그 일자 점검 전(08 적용 전) 업로드 수 → [로그 일자 점검 다시 하기] 표시 */
        private int uncheckedUploadCount;
        private int fileCount;
        private long totalLines;
        private long accountCount;
    }

    /** 시스템별 업로드 현황 1행 (그 달 업로드 파일 합산) */
    @Getter
    @Builder
    public static class UploadRow {
        private Long systemId;
        private String category;
        private String systemName;
        /** NONE(미업로드) / WAIT(월이 끝나기 전 미업로드) / OK(업로드 완료) / CHECK(확인 필요) / UNCHECKED(일자 점검 전) */
        private String status;
        /** 확인 필요 사유 (기간 시작 늦음, 기간 끝 이름, 일시 인식 안 됨, 대상 월 밖) */
        private List<String> checkReasons;
        private int fileCount;
        private String fileName;
        private Long latestUploadId;
        private LocalDateTime lastUploadedAt;
        /** 대상 월 안의 첫/마지막 로그 일자 (yyyy-MM-dd) */
        private String firstLogDate;
        private String lastLogDate;
        private int activeDays;
        private int monthDays;
        private long totalLines;
        private long dateUnknownLines;
        private long outOfMonthLines;
        private long accountCount;
    }

    @Getter
    @Builder
    public static class Aging {
        private long total;
        private long within3;
        private long d4to7;
        private long d8to14;
        private long over14;
        private List<AgingItem> oldest;
    }

    @Getter
    @Builder
    public static class AgingItem {
        private Long uploadId;
        private Long systemId;
        private String systemName;
        private long pendingCount;
        private long days;
        private LocalDateTime since;
    }

    @Getter
    @Builder
    public static class PrivAccount {
        private Long systemId;
        private String systemName;
        private String accountId;
        private long successCount;
        private long failCount;
        private long offHourCount;
        private long externalIpCount;
        private String lastSeen;
        private Long latestUploadId;
        private List<String> riskFlags;
    }

    @Getter
    @Builder
    public static class DailyTrend {
        private String date;
        private long lines;
        private long success;
        private long fail;
        private long offHour;
        private long detections;
    }

    /** 검토 현황 1행 (시스템) */
    @Getter
    @Builder
    public static class ReviewRow {
        private Long systemId;
        private String systemName;
        /** 접속 로그: NONE / ANALYZED / REVIEWED */
        private String accessStatus;
        private int uploadCount;
        private Long latestUploadId;
        private long detectionTotal;
        private long detectionPending;
        private long detectionConfirmed;
        private long detectionResolved;
        private long detectionFalsePositive;
        /** 탐지 검토 진행률 (%) */
        private int detectionRate;
        /** 다음에 할 일 (화면 안내) */
        private String nextAction;
        private String nextLink;
    }
}
