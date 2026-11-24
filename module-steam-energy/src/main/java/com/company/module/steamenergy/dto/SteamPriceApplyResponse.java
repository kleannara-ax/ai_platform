package com.company.module.steamenergy.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 등록 단가 일괄 적용 결과
 *
 * <p>hasDailyTarget 이 false 면 이 항목은 새 일별 표에 들어갈 자리가 없다는 뜻이다
 * (아직 전환되지 않은 화면의 단가 — 화면 JS 가 옛 경로로 처리한다).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamPriceApplyResponse {

    private String itemCode;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal priceValue;
    private boolean hasDailyTarget;
    private int appliedDays;

    public static SteamPriceApplyResponse of(String itemCode, Integer yearNo, Integer monthNo,
                                             BigDecimal priceValue, boolean hasDailyTarget, int appliedDays) {
        return SteamPriceApplyResponse.builder()
                .itemCode(itemCode).yearNo(yearNo).monthNo(monthNo).priceValue(priceValue)
                .hasDailyTarget(hasDailyTarget).appliedDays(appliedDays)
                .build();
    }
}
