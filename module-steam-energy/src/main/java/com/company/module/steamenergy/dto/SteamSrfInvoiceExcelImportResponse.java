package com.company.module.steamenergy.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** SRF 보일러 인보이스 통합본 엑셀 가져오기 결과 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamSrfInvoiceExcelImportResponse {

    /** 반영한 인보이스 기간 (Summary 의 Invoice for the period 시작일) */
    private int year;
    private int month;
    /** 월별 운영내역 · Accumulated 를 넣은 회계연도 (6월이 속한 해) */
    private int fiscalYear;
    /** 값이 들어간 칸 수 합계 */
    private int imported;
    /** 시트별 값이 들어간 칸 수 (summary · clientInvoice · operation · monthly · accumulated · bsc · adjustment · incident) */
    private Map<String, Integer> sheets;
    /** 엑셀의 TOTAL SERVICE CHARGES FOR THE MONTH (저장하지 않는 계산값 — 비교용) */
    private BigDecimal workbookTotal;
    /** 반영한 값으로 화면 계산식을 돌린 TOTAL SERVICE CHARGES FOR THE MONTH */
    private BigDecimal screenTotal;
    /** 확인이 필요한 점 — 저장할 칸이 없어 건너뛴 항목, 못 찾은 시트·제목, 다른 화면 값과 다른 점 */
    private List<String> warnings;
}
