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
