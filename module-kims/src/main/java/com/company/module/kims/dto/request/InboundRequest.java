package com.company.module.kims.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * 소모품 입고 요청 DTO.
 */
@Getter
@Setter
public class InboundRequest {

    @NotNull(message = "입고 수량은 필수입니다.")
    @Positive(message = "입고 수량은 1 이상이어야 합니다.")
    private Integer quantity;

    @NotBlank(message = "입고 담당자는 필수입니다.")
    private String createdBy;

    /** 입고 비고 (선택) */
    private String note;
}
