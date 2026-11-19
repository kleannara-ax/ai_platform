package com.company.module.steamenergy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 등록 단가를 그 달 일별 표에 일괄 적용하는 요청 (단가 입력 페이지)
 *
 * <p>itemCode 별 대상: lng_unit → LNG 보일러·버너 단가, fluid_steam → 유동상 단가, waste2_steam → 폐합성소각로 단가.
 */
@Getter
@Setter
public class SteamPriceApplyRequest {

    @NotNull(message = "연도(yearNo)는 필수입니다.")
    @Min(2000) @Max(2999)
    private Integer yearNo;

    @NotNull(message = "월(monthNo)은 필수입니다.")
    @Min(1) @Max(12)
    private Integer monthNo;

    @NotBlank(message = "항목코드(itemCode)는 필수입니다.")
    private String itemCode;
}
