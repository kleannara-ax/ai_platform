package com.company.module.fire.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 소화전 등록/수정 요청 DTO
 */
@Getter
@Setter
public class FireHydrantSaveRequest {

    private Long hydrantId;  // 0 또는 null이면 신규 등록

    /** Indoor / Outdoor */
    @NotBlank(message = "소화전 타입을 선택하세요.")
    private String hydrantType;

    /** Auto / Manual */
    @NotBlank(message = "작동 방식을 선택하세요.")
    private String operationType;

    // 옥내 소화전
    private Long buildingId;
    private Long floorId;
    private BigDecimal x;
    private BigDecimal y;

    // 옥외 소화전
    private String locationDescription;
}
