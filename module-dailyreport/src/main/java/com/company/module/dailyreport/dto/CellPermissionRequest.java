package com.company.module.dailyreport.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 셀 편집 권한 등록/수정 요청 DTO
 */
@Getter
@Setter
public class CellPermissionRequest {

    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    @NotBlank(message = "표 코드는 필수입니다.")
    private String tableCode;

    @NotNull(message = "행 시작 인덱스는 필수입니다.")
    @Min(value = 0, message = "행 시작 인덱스는 0 이상이어야 합니다.")
    private Integer rowStart;

    @NotNull(message = "행 끝 인덱스는 필수입니다.")
    @Min(value = 0, message = "행 끝 인덱스는 0 이상이어야 합니다.")
    private Integer rowEnd;

    @NotNull(message = "열 시작 인덱스는 필수입니다.")
    @Min(value = 0, message = "열 시작 인덱스는 0 이상이어야 합니다.")
    private Integer colStart;

    @NotNull(message = "열 끝 인덱스는 필수입니다.")
    @Min(value = 0, message = "열 끝 인덱스는 0 이상이어야 합니다.")
    private Integer colEnd;

    @NotBlank(message = "입력 주기는 필수입니다.")
    private String inputCycle;
}
