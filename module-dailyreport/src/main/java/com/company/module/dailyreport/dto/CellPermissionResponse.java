package com.company.module.dailyreport.dto;

import com.company.module.dailyreport.entity.CellPermission;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 셀 편집 권한 응답 DTO
 */
@Getter
@Builder
public class CellPermissionResponse {

    private Long permissionId;
    private Long userId;
    private String tableCode;
    private Integer rowStart;
    private Integer rowEnd;
    private Integer colStart;
    private Integer colEnd;
    private String inputCycle;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CellPermissionResponse from(CellPermission entity) {
        return CellPermissionResponse.builder()
                .permissionId(entity.getPermissionId())
                .userId(entity.getUserId())
                .tableCode(entity.getTableCode())
                .rowStart(entity.getRowStart())
                .rowEnd(entity.getRowEnd())
                .colStart(entity.getColStart())
                .colEnd(entity.getColEnd())
                .inputCycle(entity.getInputCycle())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
