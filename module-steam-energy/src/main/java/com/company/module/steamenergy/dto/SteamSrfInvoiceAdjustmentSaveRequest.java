package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRF 인보이스 Adjustment Factor 저장 요청 — (YEAR_NO, SECTION, ITEM_CODE) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamSrfInvoiceAdjustmentSaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "표 구분 (afbsc / afbsc-rebase / afasc)(section)은(는) 필수입니다.")
    private String section;

    @NotNull(message = "항목 (a, b, c, d, e, e-bottom, e-fly, f, g, h)(itemCode)은(는) 필수입니다.")
    private String itemCode;

    private BigDecimal coefficient;
    private BigDecimal baseValue;
    private BigDecimal presentValue;
}
