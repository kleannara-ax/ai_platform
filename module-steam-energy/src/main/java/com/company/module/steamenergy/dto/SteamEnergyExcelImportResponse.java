package com.company.module.steamenergy.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 에너지 회계비용 엑셀 가져오기 결과 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyExcelImportResponse {

    /** accounting-plan · fuel-plan · fuel-ratio · month-compare · all */
    private String page;
    private int year;
    /** 규격 표에 저장된 칸 수 (매핑 없는 칸은 제외) */
    private int imported;

    public static SteamEnergyExcelImportResponse of(String page, int year, int imported) {
        return SteamEnergyExcelImportResponse.builder().page(page).year(year).imported(imported).build();
    }
}
