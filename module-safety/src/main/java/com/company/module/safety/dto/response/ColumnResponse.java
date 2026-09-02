package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyManualColumn;
import lombok.Builder;
import lombok.Getter;

/** 상세 표의 열 정의 — 화면은 이 목록대로 표를 그린다. */
@Getter
@Builder
public class ColumnResponse {

    private final Long columnId;
    private final String label;
    /** TEXT / CHECK / PHOTO */
    private final String columnType;
    private final int sortOrder;
    private final int widthWeight;

    public static ColumnResponse from(SafetyManualColumn entity) {
        return ColumnResponse.builder()
                .columnId(entity.getColumnId())
                .label(entity.getLabel())
                .columnType(entity.getColumnType())
                .sortOrder(entity.getSortOrder())
                .widthWeight(entity.getWidthWeight())
                .build();
    }
}
