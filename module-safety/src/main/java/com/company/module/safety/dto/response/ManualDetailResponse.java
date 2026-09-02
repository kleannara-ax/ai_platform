package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyManual;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 매뉴얼 상세 화면 응답 — 서식, 머리말 항목, 표 열 정의, 행 목록을 함께 담는다. */
@Getter
@Builder
public class ManualDetailResponse {

    private final Long manualId;
    private final Long categoryId;
    private final String categoryName;
    private final String title;
    /** WORK_METHOD / RISK_ASSESSMENT */
    private final String formType;
    private final String formTypeName;
    private final String sourceFileName;
    private final String sourceSheetName;
    private final List<MetaResponse> meta;
    private final List<ColumnResponse> columns;
    private final List<StepResponse> steps;

    public static ManualDetailResponse from(SafetyManual entity,
                                            List<MetaResponse> meta,
                                            List<ColumnResponse> columns,
                                            List<StepResponse> steps) {
        return ManualDetailResponse.builder()
                .manualId(entity.getManualId())
                .categoryId(entity.getCategory() != null ? entity.getCategory().getCategoryId() : null)
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : null)
                .title(entity.getTitle())
                .formType(entity.formType().name())
                .formTypeName(entity.formType().displayName())
                .sourceFileName(entity.getSourceFileName())
                .sourceSheetName(entity.getSourceSheetName())
                .meta(meta)
                .columns(columns)
                .steps(steps)
                .build();
    }
}
