package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SRF 인보이스 운영내역 저장 요청 — (USAGE_DATE) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamSrfInvoiceOperationDailySaveRequest {

    @NotNull(message = "일자(usageDate)은(는) 필수입니다.")
    private LocalDate usageDate;

    private BigDecimal powerKwh;
}
