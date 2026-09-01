package com.company.module.safety.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StepUpdateRequest {

    private int stepNo;
    private String description;
    private String hazard;
    private String safetyEquipment;
    private String remark;
    private int sortOrder;
}
