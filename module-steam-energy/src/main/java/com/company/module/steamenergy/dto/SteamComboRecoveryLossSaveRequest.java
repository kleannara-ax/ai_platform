package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 복합보일러 스팀 회수 감소 저장 요청 — (YEAR_NO, MONTH_NO, LINE_NO) 업서트. 보내지 않은 항목은 빈 값으로 저장된다.
 */
@Getter
@Setter
public class SteamComboRecoveryLossSaveRequest {

    @NotNull(message = "연도(yearNo)은(는) 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월 (1~12)(monthNo)은(는) 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    @NotNull(message = "줄 번호 (1부터, 그 달의 감소 건 순번)(lineNo)은(는) 필수입니다.")
    private Integer lineNo;

    private String lossDate;
    private String lossTime;
    private String lossNote;
    private BigDecimal lossSteamRate;
    private BigDecimal lossBaseRate;
    private BigDecimal lossMinutes;
}
