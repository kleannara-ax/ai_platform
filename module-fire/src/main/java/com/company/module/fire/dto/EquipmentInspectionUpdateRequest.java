package com.company.module.fire.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EquipmentInspectionUpdateRequest {

    @NotNull(message = "inspectionDate is required.")
    private LocalDate inspectionDate;

    private LocalTime inspectionTime;

    @Size(max = 200, message = "inspectorName must be <= 200 chars.")
    private String inspectorName;

    @Valid
    @NotEmpty(message = "items is required.")
    private List<EquipmentInspectionItemRequest> items = new ArrayList<>();

    private String note;
}
