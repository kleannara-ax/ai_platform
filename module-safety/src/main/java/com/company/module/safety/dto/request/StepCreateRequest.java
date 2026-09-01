package com.company.module.safety.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 매뉴얼 상세화면에서 단계를 하나씩 추가할 때 쓰는 요청 */
@Getter
@NoArgsConstructor
public class StepCreateRequest {

    private int stepNo;
    private String description;
    private String hazard;
    private String safetyEquipment;
    private String remark;
    private int sortOrder;
}
