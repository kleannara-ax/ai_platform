package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamEnergyRatioRef2019;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 연료비 배분비율(2019 참고) 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEnergyRatioRef2019Response {

    private Long ratioRef2019Id;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal t18Pm1;
    private BigDecimal t18Pm2;
    private BigDecimal t18Pm3;
    private BigDecimal t18PaperSubtotal;
    private BigDecimal t18Tm1;
    private BigDecimal t18Tm3;
    private BigDecimal t18Tm4;
    private BigDecimal t18Tm5;
    private BigDecimal t18TissueSubtotal;
    private BigDecimal t18Total;
    private BigDecimal t19Pm1;
    private BigDecimal t19Pm2;
    private BigDecimal t19Pm3;
    private BigDecimal t19PaperSubtotal;
    private BigDecimal t19Tm1;
    private BigDecimal t19Tm3;
    private BigDecimal t19Tm4;
    private BigDecimal t19Tm5;
    private BigDecimal t19TissueSubtotal;
    private BigDecimal t19Total;
    private BigDecimal t20Pm1;
    private BigDecimal t20Pm2;
    private BigDecimal t20Pm3;
    private BigDecimal t20PaperSubtotal;
    private BigDecimal t20Tm1;
    private BigDecimal t20Tm3;
    private BigDecimal t20Tm4;
    private BigDecimal t20Tm5;
    private BigDecimal t20TissueSubtotal;
    private BigDecimal t20Total;
    private BigDecimal t20TotalEnergyCost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamEnergyRatioRef2019Response from(SteamEnergyRatioRef2019 e) {
        return from(e, null);
    }

    public static SteamEnergyRatioRef2019Response from(SteamEnergyRatioRef2019 e, Boolean isUpdate) {
        return SteamEnergyRatioRef2019Response.builder()
                .ratioRef2019Id(e.getRatioRef2019Id())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .t18Pm1(e.getT18Pm1())
                .t18Pm2(e.getT18Pm2())
                .t18Pm3(e.getT18Pm3())
                .t18PaperSubtotal(e.getT18PaperSubtotal())
                .t18Tm1(e.getT18Tm1())
                .t18Tm3(e.getT18Tm3())
                .t18Tm4(e.getT18Tm4())
                .t18Tm5(e.getT18Tm5())
                .t18TissueSubtotal(e.getT18TissueSubtotal())
                .t18Total(e.getT18Total())
                .t19Pm1(e.getT19Pm1())
                .t19Pm2(e.getT19Pm2())
                .t19Pm3(e.getT19Pm3())
                .t19PaperSubtotal(e.getT19PaperSubtotal())
                .t19Tm1(e.getT19Tm1())
                .t19Tm3(e.getT19Tm3())
                .t19Tm4(e.getT19Tm4())
                .t19Tm5(e.getT19Tm5())
                .t19TissueSubtotal(e.getT19TissueSubtotal())
                .t19Total(e.getT19Total())
                .t20Pm1(e.getT20Pm1())
                .t20Pm2(e.getT20Pm2())
                .t20Pm3(e.getT20Pm3())
                .t20PaperSubtotal(e.getT20PaperSubtotal())
                .t20Tm1(e.getT20Tm1())
                .t20Tm3(e.getT20Tm3())
                .t20Tm4(e.getT20Tm4())
                .t20Tm5(e.getT20Tm5())
                .t20TissueSubtotal(e.getT20TissueSubtotal())
                .t20Total(e.getT20Total())
                .t20TotalEnergyCost(e.getT20TotalEnergyCost())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
