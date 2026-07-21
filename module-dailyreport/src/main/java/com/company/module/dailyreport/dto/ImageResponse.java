package com.company.module.dailyreport.dto;

import com.company.module.dailyreport.entity.DailyReportImage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 이미지 응답 DTO
 */
@Getter
@Builder
public class ImageResponse {

    private Long imageId;
    private String originalName;
    private String storedPath;
    private Long fileSize;
    private String contentType;
    private String description;
    private String tableCode;
    private Integer sortOrder;
    private Long uploadedBy;
    private LocalDateTime createdAt;

    public static ImageResponse from(DailyReportImage entity) {
        return ImageResponse.builder()
                .imageId(entity.getImageId())
                .originalName(entity.getOriginalName())
                .storedPath(entity.getStoredPath())
                .fileSize(entity.getFileSize())
                .contentType(entity.getContentType())
                .description(entity.getDescription())
                .tableCode(entity.getTableCode())
                .sortOrder(entity.getSortOrder())
                .uploadedBy(entity.getUploadedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
