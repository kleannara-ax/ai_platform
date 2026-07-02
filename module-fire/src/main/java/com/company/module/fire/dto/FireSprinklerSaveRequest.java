package com.company.module.fire.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 스프링클러 등록/수정 요청 DTO
 */
@Getter
@Setter
public class FireSprinklerSaveRequest {

    private Long sprinklerId;

    @NotNull(message = "건물을 선택하세요.")
    @Min(value = 1, message = "건물을 선택하세요.")
    private Long buildingId;

    @NotNull(message = "층을 선택하세요.")
    @Min(value = 1, message = "층을 선택하세요.")
    private Long floorId;

    private BigDecimal x;
    private BigDecimal y;
    private String note;
}
