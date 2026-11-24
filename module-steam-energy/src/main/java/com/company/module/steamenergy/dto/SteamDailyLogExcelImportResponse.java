package com.company.module.steamenergy.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/** 운전일지(폐합성소각로 · 유동상) 엑셀 가져오기 결과 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamDailyLogExcelImportResponse {

    private int year;
    private int month;
    /** 값이 들어온 시트 수 (폐합성소각로) */
    private int sheets;
    /** 값이 들어온 일자 수 (유동상) */
    private int days;
    /** 반영한 칸 수 */
    private int cells;
    /** 반영한 함수율 칸 수 (유동상) */
    private int moistureCells;
    /** 확인이 필요한 점 (양식 위치가 다름, 시트 없음 등) */
    private List<String> warnings;

    @SuppressWarnings("unchecked")
    public static SteamDailyLogExcelImportResponse of(int year, int month, Map<String, Object> result) {
        Object warnings = result.get("warnings");
        return SteamDailyLogExcelImportResponse.builder()
                .year(year).month(month)
                .sheets(number(result, "sheets")).days(number(result, "days"))
                .cells(number(result, "cells")).moistureCells(number(result, "moisture_cells"))
                .warnings(warnings instanceof List<?> list ? (List<String>) list : List.of())
                .build();
    }

    private static int number(Map<String, Object> result, String key) {
        return result.get(key) instanceof Number n ? n.intValue() : 0;
    }
}
