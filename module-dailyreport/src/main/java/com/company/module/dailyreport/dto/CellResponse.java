package com.company.module.dailyreport.dto;

import com.company.module.dailyreport.entity.DailyReportCell;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 셀 데이터 응답 DTO
 */
@Getter
@Builder
public class CellResponse {

    private Long cellId;
    /** 이 셀이 속한 표의 코드 (예: TBL_PRODUCTION_INDEX). 프론트 저장 요청 시 사용. */
    private String tableCode;
    private Integer rowIndex;
    private Integer colIndex;
    private String excelCoord;
    private String cellValue;
    private String cellType;
    private String cellLabel;
    private String dataFormat;
    private String formula;
    private String inputCycle;
    private String freqCode;
    private String freqLabel;
    private String ownerIds;
    private String ownerNames;
    private Boolean isLocked;
    private Boolean isEditable;  // 현재 사용자 기준 편집 가능 여부
    private Integer rowSpan;
    private Integer colSpan;
    private Long lastEditorId;
    private LocalDateTime lastEditedAt;
    /**
     * ★ hover 표시용 "실질" 최종 저장자 이름/시각 (2026-08).
     * - lastEditorId/lastEditedAt은 전파 제어 플래그와 겸용이라 이월(carry-over)된
     *   셀은 항상 null이다. 화면에는 fallback(같은 좌표의 과거 실입력 값)까지
     *   반영한 이 필드를 사용한다 — 값이 없으면(저장 기록 자체가 없으면) null.
     * - 서비스 계층(CellService)에서 원본 lastEditorId가 있으면 그 값을,
     *   없으면 배치 fallback 조회 결과를 채워 넣는다.
     */
    private String displayEditorName;
    private LocalDateTime displayEditedAt;

    /**
     * ★ CellService가 배치 fallback 조회 결과를 반영하기 위한 전용 setter (2026-08).
     * - 다른 필드는 여전히 생성 시점에 확정되는 불변 값이므로 @Setter를 클래스
     *   전체에 붙이지 않고, 이 두 필드만 명시적으로 열어둔다.
     */
    public void setDisplayEditorName(String displayEditorName) {
        this.displayEditorName = displayEditorName;
    }

    public void setDisplayEditedAt(LocalDateTime displayEditedAt) {
        this.displayEditedAt = displayEditedAt;
    }

    public static CellResponse from(DailyReportCell entity) {
        return CellResponse.builder()
                .cellId(entity.getCellId())
                .tableCode(entity.getReportTable() != null ? entity.getReportTable().getTableCode() : null)
                .rowIndex(entity.getRowIndex())
                .colIndex(entity.getColIndex())
                .excelCoord(entity.getExcelCoord())
                .cellValue(entity.getCellValue())
                .cellType(entity.getCellType())
                .cellLabel(entity.getCellLabel())
                .dataFormat(entity.getDataFormat())
                .formula(entity.getFormula())
                .inputCycle(entity.getInputCycle())
                .freqCode(entity.getFreqCode())
                .freqLabel(entity.getFreqLabel())
                .ownerIds(entity.getOwnerIds())
                .ownerNames(entity.getOwnerNames())
                .isLocked(entity.getIsLocked())
                .isEditable(false) // 기본값, 서비스에서 사용자별로 재설정
                .rowSpan(entity.getRowSpan())
                .colSpan(entity.getColSpan())
                .lastEditorId(entity.getLastEditorId())
                .lastEditedAt(entity.getLastEditedAt())
                .build();
    }

    /** 편집 가능 여부를 포함한 변환 */
    public static CellResponse fromWithEditability(DailyReportCell entity, boolean editable) {
        return CellResponse.builder()
                .cellId(entity.getCellId())
                .tableCode(entity.getReportTable() != null ? entity.getReportTable().getTableCode() : null)
                .rowIndex(entity.getRowIndex())
                .colIndex(entity.getColIndex())
                .excelCoord(entity.getExcelCoord())
                .cellValue(entity.getCellValue())
                .cellType(entity.getCellType())
                .cellLabel(entity.getCellLabel())
                .dataFormat(entity.getDataFormat())
                .formula(entity.getFormula())
                .inputCycle(entity.getInputCycle())
                .freqCode(entity.getFreqCode())
                .freqLabel(entity.getFreqLabel())
                .ownerIds(entity.getOwnerIds())
                .ownerNames(entity.getOwnerNames())
                .isLocked(entity.getIsLocked())
                .isEditable(editable)
                .rowSpan(entity.getRowSpan())
                .colSpan(entity.getColSpan())
                .lastEditorId(entity.getLastEditorId())
                .lastEditedAt(entity.getLastEditedAt())
                .build();
    }
}
