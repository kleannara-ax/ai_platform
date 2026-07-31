package com.company.module.dailyreport.dto;

import com.company.module.dailyreport.entity.DailyReportRemark;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 특이사항 응답 DTO
 *
 * ★★ 2026-07 개편: 사업부별 담당자 배정(CellAuth와 동일 방식) + "누가 언제
 * 저장했는지" 화면 표시를 위해 editable/savedByName/savedAt 필드를 추가한다.
 */
@Getter
@Builder
public class RemarkResponse {

    private Long remarkId;
    private String tableCode;
    /** 사업부 코드: PAPER/TISSUE/PAD/SAFETY/ETC */
    private String category;
    private String content;
    private Integer sortOrder;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 현재 사용자 기준 편집 가능 여부 (서비스에서 CellAuth 기반으로 계산) */
    private Boolean editable;
    /** 이 사업부 행의 담당자 이름 목록 (쉼표 구분, 관리자 화면과 동일한 표시용) */
    private String ownerNames;
    /** 마지막으로 저장(작성 또는 수정)한 사람의 이름 — 화면에 "OOO · 시각 저장"으로 표시 */
    private String savedByName;
    /** 마지막으로 저장한 시각 (updatedAt이 있으면 그 값, 없으면 createdAt) */
    private LocalDateTime savedAt;

    public static RemarkResponse from(DailyReportRemark entity) {
        return RemarkResponse.builder()
                .remarkId(entity.getRemarkId())
                .tableCode(entity.getTableCode())
                .category(entity.getCategory())
                .content(entity.getContent())
                .sortOrder(entity.getSortOrder())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .savedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt() : entity.getCreatedAt())
                .build();
    }
}
