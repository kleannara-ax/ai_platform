package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyManual;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 매뉴얼 상세 화면(엑셀과 같은 레이아웃) 응답 — 단계 목록을 포함한다. */
@Getter
@Builder
public class ManualDetailResponse {

    private final Long manualId;
    private final Long categoryId;
    private final String categoryName;
    private final String title;
    private final String sourceFileName;
    private final String sourceSheetName;
    private final List<StepResponse> steps;

    public static ManualDetailResponse from(SafetyManual entity, List<StepResponse> steps) {
        return ManualDetailResponse.builder()
                .manualId(entity.getManualId())
                .categoryId(entity.getCategory() != null ? entity.getCategory().getCategoryId() : null)
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : null)
                .title(entity.getTitle())
                .sourceFileName(entity.getSourceFileName())
                .sourceSheetName(entity.getSourceSheetName())
                .steps(steps)
                .build();
    }
}
