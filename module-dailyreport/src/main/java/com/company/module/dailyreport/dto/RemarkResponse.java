package com.company.module.dailyreport.dto;

import com.company.module.dailyreport.entity.DailyReportRemark;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 특이사항 응답 DTO
 */
@Getter
@Builder
public class RemarkResponse {

    private Long remarkId;
    private String tableCode;
    private String category;
    private String content;
    private Integer sortOrder;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RemarkResponse from(DailyReportRemark entity) {
        return RemarkResponse.builder()
                .remarkId(entity.getRemarkId())
                .tableCode(entity.getTableCode())
                .category(entity.getCategory())
                .content(entity.getContent())
                .sortOrder(entity.getSortOrder())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
