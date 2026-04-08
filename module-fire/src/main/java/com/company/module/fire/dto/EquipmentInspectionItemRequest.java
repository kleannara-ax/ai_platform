package com.company.module.fire.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EquipmentInspectionItemRequest {

    @NotBlank(message = "점검 항목 키는 필수입니다.")
    private String itemKey;

    @NotBlank(message = "점검 항목명은 필수입니다.")
    private String itemLabel;

    @NotBlank(message = "점검 결과는 필수입니다.")
    private String result;
}
