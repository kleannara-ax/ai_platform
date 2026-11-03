package com.company.module.security_log.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 로그 기간 확인 기준 (대시보드 '로그 업로드 현황'과 '접속·보안 로그' 목록 공통)
 * <pre>
 * - 첫 로그일이 1일 + PERIOD_EDGE_DAYS 보다 늦으면  "M/D부터 시작"
 * - 마지막 로그일이 말일 - PERIOD_EDGE_DAYS 보다 이르면 "M/D까지만 있음" (대상 월이 끝난 뒤에만 판단)
 * - 일시를 읽지 못한 줄이 DATE_UNKNOWN_PERCENT % 이상이면 "일시 인식 안 된 줄 N"
 * - 대상 월 밖 줄이 있으면 "대상 월 밖 줄 N"
 * </pre>
 */
final class LogPeriodCheck {

    /** 첫/마지막 로그일 허용 여유 일수 */
    static final int PERIOD_EDGE_DAYS = 3;
    /** 일시를 읽지 못한 줄이 전체의 이 비율(%) 이상이면 확인 필요 */
    static final int DATE_UNKNOWN_PERCENT = 10;

    private LogPeriodCheck() {
    }

    /** 대상 월 안의 첫/마지막 로그일로 본 사유 (first == null 이면 대상 월 로그 없음) */
    static List<String> coverageReasons(YearMonth month, boolean monthOver, LocalDate first, LocalDate last) {
        List<String> reasons = new ArrayList<>();
        if (first == null) {
            reasons.add("대상 월 로그 없음");
            return reasons;
        }
        if (first.getDayOfMonth() > 1 + PERIOD_EDGE_DAYS) {
            reasons.add(first.getMonthValue() + "/" + first.getDayOfMonth() + "부터 시작");
        }
        // 끝 확인은 대상 월이 끝난 뒤에만 (진행 중인 달은 아직 다 올 수 없음)
        if (monthOver && last.isBefore(month.atEndOfMonth().minusDays(PERIOD_EDGE_DAYS))) {
            reasons.add(last.getMonthValue() + "/" + last.getDayOfMonth() + "까지만 있음");
        }
        return reasons;
    }

    /** 줄 수 기준 사유 (일시 인식 안 된 줄, 대상 월 밖 줄) */
    static List<String> lineReasons(long lines, long unknown, long outside) {
        List<String> reasons = new ArrayList<>();
        if (lines > 0 && unknown * 100 >= lines * DATE_UNKNOWN_PERCENT) {
            reasons.add("일시 인식 안 된 줄 " + unknown);
        }
        if (outside > 0) {
            reasons.add("대상 월 밖 줄 " + outside);
        }
        return reasons;
    }
}
