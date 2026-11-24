package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 유동상 설비개선 월별 비용 저장 요청 — (YEAR_NO, RECORD_KEY) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamFluidizedImprovementPaymentSaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "기록 키 (구분 접두어:번호 — 예: accident-record:1763600000000, improvement-item:xlsx-r12)(recordKey)은(는) 필수입니다.")
    private String recordKey;

    private String payMonth;
    private BigDecimal paymentAmount;
    private BigDecimal spentAmount;
    private String vendorName;
    private String usageDesc;
    private Integer sortOrder;
}
