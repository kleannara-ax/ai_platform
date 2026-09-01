package com.company.module.safety.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 엑셀 일괄업로드 1단계(형식 확인/미리보기) 응답.
 * <p>업로드된 워크북의 시트별 인식 결과를 보여주고, 사용자가 가져올 시트를 선택하게 한다.
 */
@Getter
@Builder
public class ExcelSheetPreviewResponse {

    /** 시트명 (원본 그대로) */
    private final String sheetName;

    /** 매뉴얼 형식으로 인식되었는지 여부 (헤더가 No./공정명/... 패턴과 일치) */
    private final boolean recognized;

    /** 인식되지 않은 경우 이유 (예: "개요/범례 시트로 추정되어 제외됨") */
    private final String reason;

    /** 인식된 단계(행) 개수 */
    private final int stepCount;

    /** 인식된 사진 개수 */
    private final int photoCount;

    /** 매뉴얼 제목으로 쓸 값 (병합된 공정명 셀에서 추출, 공백 정리됨) */
    private final String detectedTitle;

    /** 업로드 확정 시 이 시트를 가져올지 여부 (기본값 = recognized) — 사용자가 화면에서 토글 가능 */
    private final boolean selected;

    /** 단계별 미리보기 (앞부분 몇 개만) */
    private final List<String> stepPreviewLines;
}
