package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRF 인보이스 Client Invoice 저장 요청 — (YEAR_NO, MONTH_NO, SECTION, LINE_NO) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamSrfInvoiceClientItemSaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월 (1~12)(monthNo)은(는) 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    @NotNull(message = "구분 (utilities: 유틸리티 10줄 / other-out: 깨끗한나라→베올리아 3줄 / other-in: 깨끗한나라←베올리아 3줄)(section)은(는) 필수입니다.")
    private String section;

    @NotNull(message = "줄 번호 (0부터)(lineNo)은(는) 필수입니다.")
    private Integer lineNo;

    private String itemLabel;
    private String unitLabel;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private String note;
}
