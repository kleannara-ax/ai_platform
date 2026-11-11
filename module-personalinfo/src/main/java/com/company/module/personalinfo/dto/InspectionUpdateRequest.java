package com.company.module.personalinfo.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 점검 이력 후속조치 수정 요청 */
@Getter
@Setter
public class InspectionUpdateRequest {

    private LocalDate inspectionDate;

    @Size(max = 100)
    private String inspectorName;

    /** 조치 예정일 */
    private LocalDate actionDueDate;

    /** 조치 완료 여부 */
    @Pattern(regexp = "^[YN]$")
    private String actionCompletedYn = "N";

    private LocalDate actionCompletedDate;

    @Size(max = 2000)
    private String remark;
}
