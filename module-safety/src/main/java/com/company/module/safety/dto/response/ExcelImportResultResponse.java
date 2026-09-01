package com.company.module.safety.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 엑셀 일괄업로드 2단계(확정) 결과 응답 */
@Getter
@Builder
public class ExcelImportResultResponse {

    /** 실제로 매뉴얼로 생성된 시트 개수 */
    private final int importedCount;

    /** 생성된 매뉴얼 요약 목록 */
    private final List<ManualSummaryResponse> manuals;

    /** 요청했지만 건너뛴 시트(이미 존재/인식 실패 등) 및 이유 */
    private final List<String> skipped;
}
