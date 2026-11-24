package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamIncineratorContractDaily;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 소각로 도급내역 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamIncineratorContractDailyResponse {

    private Long contractDailyId;
    private LocalDate usageDate;
    private BigDecimal u1Steam;
    private BigDecimal u1Unit;
    private BigDecimal u1Runtime;
    private BigDecimal u1Fixed;
    private BigDecimal u2Steam;
    private BigDecimal u2Unit;
    private BigDecimal u2Runtime;
    private BigDecimal u2Fixed;
    private BigDecimal realSteam1;
    private BigDecimal realUnit1;
    private BigDecimal realSteam2;
    private BigDecimal realUnit2;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamIncineratorContractDailyResponse from(SteamIncineratorContractDaily e) {
        return from(e, null);
    }

    public static SteamIncineratorContractDailyResponse from(SteamIncineratorContractDaily e, Boolean isUpdate) {
        return SteamIncineratorContractDailyResponse.builder()
                .contractDailyId(e.getContractDailyId())
                .usageDate(e.getUsageDate())
                .u1Steam(e.getU1Steam())
                .u1Unit(e.getU1Unit())
                .u1Runtime(e.getU1Runtime())
                .u1Fixed(e.getU1Fixed())
                .u2Steam(e.getU2Steam())
                .u2Unit(e.getU2Unit())
                .u2Runtime(e.getU2Runtime())
                .u2Fixed(e.getU2Fixed())
                .realSteam1(e.getRealSteam1())
                .realUnit1(e.getRealUnit1())
                .realSteam2(e.getRealSteam2())
                .realUnit2(e.getRealUnit2())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
