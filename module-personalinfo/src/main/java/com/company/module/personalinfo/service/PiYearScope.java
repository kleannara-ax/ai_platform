package com.company.module.personalinfo.service;

import com.company.module.personalinfo.entity.PiContract;

import java.time.LocalDate;

/**
 * 기준 연도별 계약 구분 (목록 상단 요약 카드와 목록 필터가 같은 규칙을 쓴다).
 * <p>{@link com.company.module.personalinfo.repository.PiContractRepository#search} 의 JPQL 조건과 반드시 같게 유지한다.</p>
 * <ul>
 *   <li><b>대상(전체)</b>: 원계약 기간이 기준 연도에 걸친 계약
 *       — 시작일이 없거나 연말 이전, 종료일이 없거나 연초 이후, 종료 처리됐다면 종료일이 연초 이후</li>
 *   <li><b>종료</b>: 대상 중 기준 연도 안에 끝난 계약
 *       — 종료 처리(종료일이 연말 이전 또는 미입력) 또는 원계약 종료일이 연말 이전이면서 오늘 이전(만료)</li>
 *   <li><b>계약중</b>: 대상 중 종료가 아닌 계약</li>
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

    /** 원계약 기간이 기준 연도에 걸치는지 */
    public static boolean inYear(PiContract c, int year) {
        LocalDate ys = yearStart(year);
        LocalDate ye = yearEnd(year);
        boolean startOk = c.getContractStartDate() == null || !c.getContractStartDate().isAfter(ye);
        boolean endOk = c.getContractEndDate() == null || !c.getContractEndDate().isBefore(ys);
        boolean termOk = !"Y".equals(c.getTerminatedYn()) || c.getTerminatedDate() == null
                || !c.getTerminatedDate().isBefore(ys);
        return startOk && endOk && termOk;
    }

    /** 기준 연도 안에 끝났는지 (종료 처리 또는 만료). inYear 인 계약에 대해서만 의미가 있다. */
    public static boolean endedInYear(PiContract c, int year, LocalDate today) {
        LocalDate ye = yearEnd(year);
        if ("Y".equals(c.getTerminatedYn())) {
            return c.getTerminatedDate() == null || !c.getTerminatedDate().isAfter(ye);
        }
        LocalDate end = c.getContractEndDate();
        return end != null && !end.isAfter(ye) && end.isBefore(today);
    }
}
