package com.company.module.kims.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 소모품 품목 등록 요청 DTO.
 */
@Getter
@Setter
public class InventoryItemCreateRequest {

    @NotBlank(message = "품목명은 필수입니다.")
    @Size(max = 100)
    private String itemName;

    @NotBlank(message = "카테고리는 필수입니다.")
    @Size(max = 50)
    private String category;

    /** 초기(현재) 재고 수량 - 0 이상 */
    @NotNull(message = "현재 재고는 필수입니다.")
    @PositiveOrZero(message = "현재 재고는 0 이상이어야 합니다.")
    private Integer currentStock;

    /** 최소 재고 기준 - 0 이상 */
    @NotNull(message = "최소 재고 기준은 필수입니다.")
    @PositiveOrZero(message = "최소 재고 기준은 0 이상이어야 합니다.")
    private Integer minStock;

    @NotBlank(message = "단위는 필수입니다.")
    @Size(max = 20)
    private String unit;

    @Size(max = 255)
    private String remark;
}
