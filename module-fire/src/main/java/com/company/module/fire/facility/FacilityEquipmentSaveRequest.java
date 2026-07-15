package com.company.module.fire.facility;

import jakarta.validation.constraints.Min;
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

    private String equipmentType;

    private String manufacturer;
    private String locationDescription;
    private int outdoorUnitCount = 1;
    private Boolean inspectionRequested;

    @NotNull(message = "설치/제조일을 입력하세요.")
    private LocalDate manufactureDate;

    private int replacementCycleYears = 10;

    private BigDecimal x;
    private BigDecimal y;
    private String note;
}
