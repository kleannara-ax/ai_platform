package com.company.module.safety.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 엑셀 일괄업로드 미리보기 — 시트 하나를 표 그대로 보여주기 위한 응답.
 *
 * <p>목록 응답({@link ExcelSheetPreviewResponse})은 시트마다 몇 줄만 요약해 주지만,
 * 확정 전에 "무엇이 등록되는지" 확인하려면 실제 표가 필요하다. 이 응답은 등록될 열 구성과
 * 행 내용을 그대로 담는다.
 *
 * <p>사진은 원본을 싣지 않는다. 시트 하나에 60장이 들어 있는 파일도 있어 전부 실으면 응답이
 * 너무 커진다. 대신 사진마다 번호만 주고, 화면에 실제로 보이는 것만
 * {@code /excel-upload/preview-photo} 로 낱장씩 가져간다.
 */
@Getter
@Builder
public class ExcelSheetDetailResponse {

    private final String sheetName;

    /** 등록될 매뉴얼 제목 (= 시트명) */
    private final String title;

    private final String formType;
    private final String formTypeName;

    /** 위험성 평가서의 머리말 항목 (안전작업 매뉴얼은 비어 있다) */
    private final List<MetaLine> meta;

    private final List<Column> columns;
    private final List<Row> rows;

    /** 머리말 한 줄 */
    @Getter
    @Builder
    public static class MetaLine {
        private final String label;
        private final String value;
    }

    /** 표의 열 하나 */
    @Getter
    @Builder
    public static class Column {
        private final String label;
        /** TEXT / CHECK / PHOTO */
        private final String type;
    }

    /** 표의 행 하나 — cells 는 columns 와 같은 순서로 1:1 대응한다 */
    @Getter
    @Builder
    public static class Row {
        private final int stepNo;
        private final List<Cell> cells;
    }

    /** 칸 하나 */
    @Getter
    @Builder
    public static class Cell {
        private final String text;
        private final boolean checked;
        /** 이 칸에 들어갈 사진들의 번호 (preview-photo 로 낱장씩 불러온다) */
        private final List<Integer> photoIndexes;
    }
}
