package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 시설별 단가 저장 요청 DTO (연/월/항목 기준 업서트)
 */
@Getter
@Setter
public class SteamPriceSaveRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    @NotBlank(message = "시설(facility)은 필수입니다.")
    @Size(max = 50)
    private String facility;

    @NotBlank(message = "항목코드(itemCode)는 필수입니다.")
    @Size(max = 50)
    private String itemCode;

    @NotBlank(message = "항목명(itemName)은 필수입니다.")
    @Size(max = 200)
    private String itemName;

    /** DIRECT(직접입력) / AUTO(자동계산) */
    @Pattern(regexp = "DIRECT|AUTO", message = "입력방식(inputType)은 DIRECT 또는 AUTO 여야 합니다.")
    private String inputType;

    @Size(max = 20)
    private String unit;

    private BigDecimal priceValue;

    @Size(max = 500)
    private String remark;
}
