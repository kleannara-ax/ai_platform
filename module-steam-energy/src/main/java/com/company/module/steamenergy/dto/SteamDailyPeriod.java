package com.company.module.steamenergy.dto;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 일별 표 공통 — 연/월 조회 기간 계산과 기간 검사
 *
 * <p>month 가 없으면 연도 전체(1월 1일 ~ 12월 31일).
 */
public record SteamDailyPeriod(LocalDate from, LocalDate to) {

    public static SteamDailyPeriod of(int year, Integer month) {
        if (year < 2000 || year > 2999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도(year)가 올바르지 않습니다: " + year);
        }
        if (month == null) {
            return new SteamDailyPeriod(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        }
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "월(month)이 올바르지 않습니다: " + month);
        }
        YearMonth ym = YearMonth.of(year, month);
        return new SteamDailyPeriod(ym.atDay(1), ym.atEndOfMonth());
    }

    /** 일괄 저장 행의 일자가 요청 기간 안인지 */
    public boolean contains(LocalDate date) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    /** 기간 밖 일자는 거부한다 — 다른 달 데이터가 섞여 들어오는 것을 막는다. */
    public void requireContains(LocalDate date) {
        if (!contains(date)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "일자 " + date + " 는 요청 기간(" + from + " ~ " + to + ") 밖입니다.");
        }
    }
}
