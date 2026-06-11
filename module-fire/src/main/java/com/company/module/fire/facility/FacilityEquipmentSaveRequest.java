package com.company.module.fire.facility;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class FacilityEquipmentSaveRequest {
    private Long equipmentId;
    private String serialNumber;

    @NotNull(message = "건물을 선택하세요.")
    @Min(value = 1, message = "건물을 선택하세요.")
    private Long buildingId;

    @NotNull(message = "층을 선택하세요.")
    @Min(value = 1, message = "층을 선택하세요.")
    private Long floorId;

    @NotBlank(message = "설비 종류를 입력하세요.")
    private String equipmentType;

    private String manufacturer;
    private String locationDescription;
    private int outdoorUnitCount = 1;

    @NotNull(message = "제조일을 입력하세요.")
    private LocalDate manufactureDate;

    @Min(value = 1, message = "교체 주기는 1년 이상이어야 합니다.")
    private int replacementCycleYears = 10;

    private BigDecimal x;
    private BigDecimal y;
    private String note;
}
