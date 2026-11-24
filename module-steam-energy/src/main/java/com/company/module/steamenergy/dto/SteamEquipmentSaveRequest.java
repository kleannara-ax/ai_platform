package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 스팀 설비 저장 요청 DTO
 */
@Getter
@Setter
public class SteamEquipmentSaveRequest {

    @NotBlank(message = "설비코드(EQUIPMENT_CODE)는 필수입니다.")
    @Size(max = 50)
    private String equipmentCode;

    @NotBlank(message = "설비명(NAME)은 필수입니다.")
    @Size(max = 100)
    private String name;

    @Size(max = 50)
    private String category;

    @Size(max = 20)
    private String unit;

    @Min(0)
    private Integer sortOrder;

    private Boolean isActive;
}
