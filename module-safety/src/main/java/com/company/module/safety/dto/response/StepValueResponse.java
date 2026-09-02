package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyManualStepValue;
import lombok.Builder;
import lombok.Getter;

/** 행 x 열 교차 값 — 화면은 columnId 로 열을 찾아 값을 채운다. */
@Getter
@Builder
public class StepValueResponse {

    private final Long valueId;
    private final Long columnId;
    private final String text;
    private final boolean checked;

    public static StepValueResponse from(SafetyManualStepValue entity) {
        return StepValueResponse.builder()
                .valueId(entity.getValueId())
                .columnId(entity.getColumn().getColumnId())
                .text(entity.getTextValue())
                .checked(entity.isChecked())
                .build();
    }
}
