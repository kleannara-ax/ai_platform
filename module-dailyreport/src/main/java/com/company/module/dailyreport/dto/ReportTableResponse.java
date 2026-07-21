package com.company.module.dailyreport.dto;

import com.company.module.dailyreport.entity.DailyReportTable;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 일보 표 응답 DTO
 */
@Getter
@Builder
public class ReportTableResponse {

    private Long tableId;
    private String tableCode;
    private String tableName;
    private Integer sortOrder;
    private Integer rowCount;
    private Integer colCount;
    private List<CellResponse> cells;

    public static ReportTableResponse from(DailyReportTable entity) {
        return ReportTableResponse.builder()
                .tableId(entity.getTableId())
                .tableCode(entity.getTableCode())
                .tableName(entity.getTableName())
                .sortOrder(entity.getSortOrder())
                .rowCount(entity.getRowCount())
                .colCount(entity.getColCount())
                .cells(entity.getCells().stream()
                        .map(CellResponse::from)
                        .toList())
                .build();
    }
}
