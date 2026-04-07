package com.company.module.code.dto;

import com.company.module.code.entity.CodeGroup;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Builder @AllArgsConstructor
public class CodeGroupResponse {

    private Long groupId;
    private String groupCode;
    private String groupName;
    private String description;
    private Boolean isActive;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int codeCount;
    private List<CodeDetailResponse> details;

    public static CodeGroupResponse from(CodeGroup entity) {
        return CodeGroupResponse.builder()
                .groupId(entity.getGroupId())
                .groupCode(entity.getGroupCode())
                .groupName(entity.getGroupName())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .sortOrder(entity.getSortOrder())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .codeCount(entity.getDetails() != null ? entity.getDetails().size() : 0)
                .details(entity.getDetails() != null
                        ? entity.getDetails().stream().map(CodeDetailResponse::from).toList()
                        : List.of())
                .build();
    }

    public static CodeGroupResponse summary(CodeGroup entity) {
        return CodeGroupResponse.builder()
                .groupId(entity.getGroupId())
                .groupCode(entity.getGroupCode())
                .groupName(entity.getGroupName())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .sortOrder(entity.getSortOrder())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .codeCount(entity.getDetails() != null ? entity.getDetails().size() : 0)
                .build();
    }
}
