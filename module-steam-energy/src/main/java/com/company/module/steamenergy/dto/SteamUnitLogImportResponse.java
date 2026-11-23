package com.company.module.steamenergy.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 원단위 일지 엑셀 가져오기 결과
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamUnitLogImportResponse {

    private int year;
    /** 읽은 시트 이름 ("1월일지" …) */
    private List<String> sheets;
    /** 표별 저장 건수 (신규+갱신) */
    private int unitRows;
    private int commonRows;
    private int lngRows;
    private int fuelRows;
    /** 제목을 못 찾아 건너뛴 칸 — "시트: 칸" */
    private List<String> notFound;
}
