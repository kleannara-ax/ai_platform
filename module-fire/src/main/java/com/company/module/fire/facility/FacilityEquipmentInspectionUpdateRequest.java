package com.company.module.fire.facility;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class FacilityEquipmentInspectionUpdateRequest {
    @NotNull(message = "점검일은 필수입니다.")
    private LocalDate inspectionDate;
    private Boolean isFaulty;
    private String faultReason;
}
