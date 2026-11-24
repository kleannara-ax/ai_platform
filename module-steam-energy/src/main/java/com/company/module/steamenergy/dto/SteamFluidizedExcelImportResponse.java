package com.company.module.steamenergy.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/** 유동상 운영내역 엑셀 가져오기 결과 — 구역별로 읽은 칸 수 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedExcelImportResponse {

    private int year;
    private Integer month;
    /** 전체 반영 칸 수 */
    private int imported;
    /** 구역별 칸 수 (plan_rows, note_rows, contract_rows, cost_rows, detail_rows, srf_rows, improvement_rows, accident_rows 등) */
    private Map<String, Integer> rows;

    public static SteamFluidizedExcelImportResponse of(int year, Integer month, Map<String, Object> result) {
        Map<String, Integer> rows = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (!entry.getKey().endsWith("_rows") || !(entry.getValue() instanceof Number n)) continue;
            rows.put(entry.getKey(), n.intValue());
            total += n.intValue();
        }
        return new SteamFluidizedExcelImportResponse(year, month, total, rows);
    }
}
