package com.company.module.fire.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 소화기 등록/수정 요청 DTO
 */
@Getter
@Setter
public class ExtinguisherSaveRequest {

    private Long extinguisherId;  // 0 또는 null이면 신규 등록

    @NotNull(message = "건물을 선택하세요.")
    @Min(value = 1, message = "건물을 선택하세요.")
    private Long buildingId;

    @NotNull(message = "층을 선택하세요.")
    @Min(value = 1, message = "층을 선택하세요.")
    private Long floorId;

    private Long groupId;  // 위치 그룹 (nullable)

    @NotBlank(message = "소화기 종류를 입력하세요.")
    private String extinguisherType;

    @NotNull(message = "제조일을 입력하세요.")
    private LocalDate manufactureDate;

    @Min(value = 1, message = "교체 주기는 1년 이상이어야 합니다.")
    private int replacementCycleYears = 10;

    @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    private int quantity = 1;

    private BigDecimal x;
    private BigDecimal y;
    private String note;
}
