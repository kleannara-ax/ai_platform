package com.company.module.dailyreport.dto;

import com.company.module.dailyreport.entity.DailyReport;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 일보 응답 DTO
 */
@Getter
@Builder
public class DailyReportResponse {

    private Long reportId;
    private LocalDate reportDate;
    private String title;
    private String status;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ReportTableResponse> tables;
    private List<RemarkResponse> remarks;
    private List<ImageResponse> images;

    /** Entity → Response 변환 (테이블/특이사항/이미지 미포함 — 목록 조회용) */
    public static DailyReportResponse from(DailyReport entity) {
        return DailyReportResponse.builder()
                .reportId(entity.getReportId())
                .reportDate(entity.getReportDate())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** Entity → Response 변환 (하위 객체 모두 포함 — 상세 조회용) */
    public static DailyReportResponse fromWithDetails(DailyReport entity) {
        return DailyReportResponse.builder()
                .reportId(entity.getReportId())
                .reportDate(entity.getReportDate())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .tables(entity.getTables().stream()
                        .map(ReportTableResponse::from)
                        .toList())
                .remarks(entity.getRemarks().stream()
                        .map(RemarkResponse::from)
                        .toList())
                .images(entity.getImages().stream()
                        .map(ImageResponse::from)
                        .toList())
                .build();
    }
}
