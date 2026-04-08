package com.company.module.fire.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EquipmentInspectionRequest {

    @Valid
    @NotEmpty(message = "점검 항목을 입력해 주세요.")
    private List<EquipmentInspectionItemRequest> items = new ArrayList<>();

    private LocalTime inspectionTime;

    private String note;
}
