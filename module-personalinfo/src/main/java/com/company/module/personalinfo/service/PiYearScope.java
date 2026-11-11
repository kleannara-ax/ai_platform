package com.company.module.personalinfo.service;

import com.company.module.personalinfo.entity.PiContract;

import java.time.LocalDate;

/**
 * 기준 연도별 계약 구분 (목록 상단 요약 카드와 목록 필터가 같은 규칙을 쓴다).
 * <p>{@link com.company.module.personalinfo.repository.PiContractRepository#search} 의 JPQL 조건과 반드시 같게 유지한다.</p>
 *
 * <ul>
 *   <li><b>기준일</b> = 조회 시점(오늘)을 기준 연도 안으로 맞춘 날.
 *       지난 연도는 12월 31일, 올해는 오늘, 앞으로 올 연도는 1월 1일.</li>
 *   <li><b>종료 기준일</b> = 종료 처리했으면 종료일(없으면 원계약 종료일), 아니면 원계약 종료일.</li>
 *   <li><b>대상(전체)</b>: 시작일이 없거나 연말 이전이고, 종료 기준일이 없거나 <b>전년도 12월 31일 이후</b>인 계약.
 *       → 12월 31일에 끝난 계약은 그해 계약중으로, 다음 해에는 종료로 잡힌다.</li>
 *   <li><b>종료</b>: 대상 중 종료 기준일이 기준일보다 앞선 계약 (= 기준일에 계약기간 밖).</li>
 *   <li><b>계약중</b>: 대상 중 종료가 아닌 계약 (= 기준일에 계약기간 안).</li>
 * </ul>
 */
public final class PiYearScope {

    private PiYearScope() {
    }

    public static LocalDate yearStart(int year) {
        return LocalDate.of(year, 1, 1);
    }

    public static LocalDate yearEnd(int year) {
        return LocalDate.of(year, 12, 31);
    }

    /** 전년도 12월 31일 (이날 이후에 끝난 계약이 기준 연도 대상) */
    public static LocalDate prevYearEnd(int year) {
        return LocalDate.of(year - 1, 12, 31);
    }

    /** 기준일: 오늘을 기준 연도 안으로 맞춘 날 */
    public static LocalDate refDate(int year, LocalDate today) {
        LocalDate ys = yearStart(year);
        LocalDate ye = yearEnd(year);
        if (today.isBefore(ys)) return ys;
        if (today.isAfter(ye)) return ye;
        return today;
    }

    /** 종료 기준일 (null 이면 끝나는 날이 정해지지 않음) */
    public static LocalDate effectiveEnd(PiContract c) {
        if ("Y".equals(c.getTerminatedYn()) && c.getTerminatedDate() != null) {
            return c.getTerminatedDate();
        }
        return c.getContractEndDate();
    }

    /** 기준 연도 대상인지 */
    public static boolean inYear(PiContract c, int year) {
        boolean startOk = c.getContractStartDate() == null || !c.getContractStartDate().isAfter(yearEnd(year));
        LocalDate end = effectiveEnd(c);
        boolean endOk = end == null || !end.isBefore(prevYearEnd(year));
        return startOk && endOk;
    }

    /** 기준 연도에 종료로 잡히는지 (inYear 인 계약에 대해서만 의미가 있다) */
    public static boolean endedInYear(PiContract c, int year, LocalDate today) {
        LocalDate end = effectiveEnd(c);
        if (end == null) {
            return "Y".equals(c.getTerminatedYn()); // 종료일·원계약 종료일이 모두 없는 예전 종료 데이터
        }
        return end.isBefore(refDate(year, today));
    }
}
