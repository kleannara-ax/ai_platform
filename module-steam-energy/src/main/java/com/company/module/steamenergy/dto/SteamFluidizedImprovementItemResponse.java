package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamFluidizedImprovementItem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 유동상 설비개선 항목 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedImprovementItemResponse {

    private Long improvementItemId;
    private Integer yearNo;
    private String recordKey;
    private String category;
    private String itemNo;
    private String itemName;
    private BigDecimal planCost;
    private BigDecimal actualCost;
    private String status;
    private String remark;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamFluidizedImprovementItemResponse from(SteamFluidizedImprovementItem e) {
        return from(e, null);
    }

    public static SteamFluidizedImprovementItemResponse from(SteamFluidizedImprovementItem e, Boolean isUpdate) {
        return SteamFluidizedImprovementItemResponse.builder()
                .improvementItemId(e.getImprovementItemId())
                .yearNo(e.getYearNo())
                .recordKey(e.getRecordKey())
                .category(e.getCategory())
                .itemNo(e.getItemNo())
                .itemName(e.getItemName())
                .planCost(e.getPlanCost())
                .actualCost(e.getActualCost())
                .status(e.getStatus())
                .remark(e.getRemark())
                .sortOrder(e.getSortOrder())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
