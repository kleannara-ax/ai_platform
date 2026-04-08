package com.company.module.fire.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class FireHydrantInspectionUpdateRequest {

    @NotNull(message = "inspectionDate is required.")
    private LocalDate inspectionDate;

    @NotNull(message = "isFaulty is required.")
    private Boolean isFaulty;

    private String faultReason;

    @Size(max = 200, message = "inspectorName must be <= 200 chars.")
    private String inspectorName;
}

