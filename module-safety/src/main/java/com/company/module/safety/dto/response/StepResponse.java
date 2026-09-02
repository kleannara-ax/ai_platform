package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyManualStep;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 상세 표의 행 1개.
 * <p>열 구성이 매뉴얼마다 달라서 칸 값을 고정 필드가 아니라 {@code values}(열 ID 기준) 로 담는다.
 */
@Getter
@Builder
public class StepResponse {

    private final Long stepId;
    private final int stepNo;
    private final int sortOrder;
    private final List<StepValueResponse> values;
    private final List<StepPhotoResponse> photos;

    public static StepResponse from(SafetyManualStep entity,
                                    List<StepValueResponse> values,
                                    List<StepPhotoResponse> photos) {
        return StepResponse.builder()
                .stepId(entity.getStepId())
                .stepNo(entity.getStepNo())
                .sortOrder(entity.getSortOrder())
                .values(values)
                .photos(photos)
                .build();
    }
}
