package com.company.module.fire.facility;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class FacilityEquipmentInspectRequest {
    @NotNull(message = "설비 ID는 필수입니다.")
    private Long equipmentId;
    private LocalDate inspectionDate;
    private boolean faulty;
    private String faultReason;
}
