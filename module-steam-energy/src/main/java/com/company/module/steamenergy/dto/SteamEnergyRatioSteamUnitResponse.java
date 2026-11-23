package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamEnergyRatioSteamUnit;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 스팀사용량 원단위(기타 포함) 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyRatioSteamUnitResponse {

    private Long ratioSteamUnitId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal unitPm2;
    private BigDecimal unitPm3;
    private BigDecimal unitPaperSubtotal;
    private BigDecimal unitTm3;
    private BigDecimal unitTm4;
    private BigDecimal unitTm5;
    private BigDecimal unitTissueSubtotal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamEnergyRatioSteamUnitResponse from(SteamEnergyRatioSteamUnit e) {
        return from(e, null);
    }

    public static SteamEnergyRatioSteamUnitResponse from(SteamEnergyRatioSteamUnit e, Boolean isUpdate) {
        return SteamEnergyRatioSteamUnitResponse.builder()
                .ratioSteamUnitId(e.getRatioSteamUnitId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .unitPm2(e.getUnitPm2())
                .unitPm3(e.getUnitPm3())
                .unitPaperSubtotal(e.getUnitPaperSubtotal())
                .unitTm3(e.getUnitTm3())
                .unitTm4(e.getUnitTm4())
                .unitTm5(e.getUnitTm5())
                .unitTissueSubtotal(e.getUnitTissueSubtotal())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
