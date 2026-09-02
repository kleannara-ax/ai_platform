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
    /** 내용 검색으로 걸린 단계 수. 내용 검색이 아니면 0. */
    private final int matchCount;
    /** 내용 검색으로 걸린 부분의 짧은 발췌 (왜 걸렸는지 화면에 보여주기 위함). 내용 검색이 아니면 빈 목록. */
    private final List<String> matchSnippets;

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
                .matchSnippets(List.of())
                .build();
    }

    /** 내용 검색 결과용 — 걸린 단계 수와 발췌를 함께 담는다. */
    public static ManualSummaryResponse withMatches(SafetyManual entity, int matchCount, List<String> snippets) {
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
                .matchCount(matchCount)
                .matchSnippets(snippets)
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
