package com.company.module.dailyreport.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 셀 값 일괄 저장 요청 DTO
 * - 사용자가 담당 셀들의 값을 한번에 저장할 때 사용
 */
@Getter
@Setter
public class CellSaveRequest {

    @NotNull(message = "표 코드는 필수입니다.")
    private String tableCode;

    @NotNull(message = "셀 값 목록은 필수입니다.")
    private List<CellValueItem> cells;

    /**
     * 개별 셀 값 항목
     */
    @Getter
    @Setter
    public static class CellValueItem {

        @NotNull(message = "행 인덱스는 필수입니다.")
        private Integer rowIndex;

        @NotNull(message = "열 인덱스는 필수입니다.")
        private Integer colIndex;

        private String cellValue;
    }
}
