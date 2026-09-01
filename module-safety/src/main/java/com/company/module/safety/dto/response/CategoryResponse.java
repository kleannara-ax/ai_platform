package com.company.module.safety.dto.response;

import com.company.module.safety.entity.SafetyManualCategory;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CategoryResponse {

    private final Long categoryId;
    private final String name;
    private final Long parentId;
    private final int sortOrder;
    private final List<CategoryResponse> children;

    public static CategoryResponse from(SafetyManualCategory entity) {
        return CategoryResponse.builder()
                .categoryId(entity.getCategoryId())
                .name(entity.getName())
                .parentId(entity.getParent() != null ? entity.getParent().getCategoryId() : null)
                .sortOrder(entity.getSortOrder())
                .build();
    }

    public static CategoryResponse withChildren(SafetyManualCategory entity, List<CategoryResponse> children) {
        return CategoryResponse.builder()
                .categoryId(entity.getCategoryId())
                .name(entity.getName())
                .parentId(entity.getParent() != null ? entity.getParent().getCategoryId() : null)
                .sortOrder(entity.getSortOrder())
                .children(children)
                .build();
    }
}
