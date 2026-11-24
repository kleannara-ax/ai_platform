package com.company.module.steamenergy.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/** 소각로 위탁운영비 엑셀 가져오기 결과 — 시트별로 읽은 칸 수 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamIncineratorExcelImportResponse {

    private int year;
    private int month;
    /** 전체 반영 칸 수 */
    private int imported;
    private int contractRows;
    private int planVsRows;
    private int operationRows;
    private int totalCostRows;
    private int accidentRows;

    public static SteamIncineratorExcelImportResponse of(int year, int month, Map<String, Object> result) {
        int contract = number(result, "contract_rows");
        int planVs = number(result, "plan_vs_rows");
        int operation = number(result, "operation_rows");
        int totalCost = number(result, "total_cost_rows");
        int accident = number(result, "accident_rows");
        return SteamIncineratorExcelImportResponse.builder()
                .year(year).month(month)
                .imported(contract + planVs + operation + totalCost + accident)
                .contractRows(contract).planVsRows(planVs).operationRows(operation)
                .totalCostRows(totalCost).accidentRows(accident)
                .build();
    }

    private static int number(Map<String, Object> result, String key) {
        return result.get(key) instanceof Number n ? n.intValue() : 0;
    }
}
