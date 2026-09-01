package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyManualStep;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StepResponse {

    private final Long stepId;
    private final int stepNo;
    private final String description;
    private final String hazard;
    private final String safetyEquipment;
    private final String remark;
    private final int sortOrder;
    private final List<StepPhotoResponse> photos;

    public static StepResponse from(SafetyManualStep entity, List<StepPhotoResponse> photos) {
        return StepResponse.builder()
                .stepId(entity.getStepId())
                .stepNo(entity.getStepNo())
                .description(entity.getDescription())
                .hazard(entity.getHazard())
                .safetyEquipment(entity.getSafetyEquipment())
                .remark(entity.getRemark())
                .sortOrder(entity.getSortOrder())
                .photos(photos)
                .build();
    }
}
