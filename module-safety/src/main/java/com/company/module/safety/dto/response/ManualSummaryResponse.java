package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyManual;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 매뉴얼 목록(카테고리 선택 시 목록 화면)에서 쓰는 요약 응답 */
@Getter
@Builder
public class ManualSummaryResponse {

    private final Long manualId;
    private final Long categoryId;
    private final String categoryName;
    private final String title;
    private final String sourceFileName;
    private final String sourceSheetName;
    private final int sortOrder;
    private final LocalDateTime updatedAt;

    public static ManualSummaryResponse from(SafetyManual entity) {
        return ManualSummaryResponse.builder()
                .manualId(entity.getManualId())
                .categoryId(entity.getCategory() != null ? entity.getCategory().getCategoryId() : null)
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : null)
                .title(entity.getTitle())
                .sourceFileName(entity.getSourceFileName())
                .sourceSheetName(entity.getSourceSheetName())
                .sortOrder(entity.getSortOrder())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
