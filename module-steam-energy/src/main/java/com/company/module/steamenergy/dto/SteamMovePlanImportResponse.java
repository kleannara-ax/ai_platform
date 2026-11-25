package com.company.module.steamenergy.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 생산계획 엑셀 가져오기 결과 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamMovePlanImportResponse {

    /** 읽은 시트 이름 */
    private String sheetName;
    /** 값이 있어 저장한 달 (YYYY-MM, 오름차순) */
    private List<String> months;
    /** 새로 만든 행 수 (연·월·호기) */
    private int inserted;
    /** 값을 고친 행 수 */
    private int updated;

    public static SteamMovePlanImportResponse of(String sheetName, List<String> months, int inserted, int updated) {
        return SteamMovePlanImportResponse.builder().sheetName(sheetName).months(months).inserted(inserted).updated(updated).build();
    }
}
