package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyManual;
import com.company.module.safety.entity.SafetyManualCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 매뉴얼 목록(카테고리 선택 시 목록 화면)에서 쓰는 요약 응답 */
@Getter
@Builder
public class ManualSummaryResponse {

    private final Long manualId;
    private final Long categoryId;
    private final String categoryName;
    /** 대분류 &gt; 중분류 &gt; 소분류 전체 경로. 상위 분류를 선택해 하위 매뉴얼을 모아 볼 때 위치를 표시한다. */
    private final String categoryPath;
    private final String title;
    private final String sourceFileName;
    private final String sourceSheetName;
    private final int sortOrder;
    private final LocalDateTime updatedAt;

    public static ManualSummaryResponse from(SafetyManual entity) {
        SafetyManualCategory category = entity.getCategory();
        return ManualSummaryResponse.builder()
                .manualId(entity.getManualId())
                .categoryId(category != null ? category.getCategoryId() : null)
                .categoryName(category != null ? category.getName() : null)
                .categoryPath(buildPath(category))
                .title(entity.getTitle())
                .sourceFileName(entity.getSourceFileName())
                .sourceSheetName(entity.getSourceSheetName())
                .sortOrder(entity.getSortOrder())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** 소분류에서 부모를 거슬러 올라가 "대분류 > 중분류 > 소분류" 문자열을 만든다. */
    private static String buildPath(SafetyManualCategory category) {
        if (category == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (SafetyManualCategory node = category; node != null; node = node.getParent()) {
            names.add(node.getName());
        }
        Collections.reverse(names);
        return String.join(" > ", names);
    }
}
