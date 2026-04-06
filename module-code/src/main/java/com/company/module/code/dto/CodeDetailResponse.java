package com.company.module.code.dto;

import com.company.module.code.entity.CodeDetail;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Builder @AllArgsConstructor
public class CodeDetailResponse {

    private Long codeId;
    private Long groupId;
    private String groupCode;
    private String code;
    private String codeName;
    private String description;
    private String extraValue1;
    private String extraValue2;
    private Boolean isActive;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CodeDetailResponse from(CodeDetail entity) {
        return CodeDetailResponse.builder()
                .codeId(entity.getCodeId())
                .groupId(entity.getGroup().getGroupId())
                .groupCode(entity.getGroup().getGroupCode())
                .code(entity.getCode())
                .codeName(entity.getCodeName())
                .description(entity.getDescription())
                .extraValue1(entity.getExtraValue1())
                .extraValue2(entity.getExtraValue2())
                .isActive(entity.getIsActive())
                .sortOrder(entity.getSortOrder())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
