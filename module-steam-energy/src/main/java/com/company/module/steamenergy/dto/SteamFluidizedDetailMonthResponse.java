package com.company.module.steamenergy.dto;

import com.company.module.steamenergy.entity.SteamFluidizedDetailMonth;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 유동상 세부 운영내역 (월별 단가·비용) 응답 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedDetailMonthResponse {

    private Long detailMonthId;
    private Integer yearNo;
    private Integer monthNo;
    private BigDecimal improvementCost;
    private BigDecimal extraCost;
    private BigDecimal steamProducedUnitPrice;
    private BigDecimal ammoniumCarbonateUnitPrice;
    private BigDecimal boilerChemInUnitPrice;
    private BigDecimal boilerChemUseUnitPrice;
    private BigDecimal activatedCarbonInUnitPrice;
    private BigDecimal activatedCarbonUseUnitPrice;
    private BigDecimal causticSodaInUnitPrice;
    private BigDecimal causticSodaUseUnitPrice;
    private BigDecimal slakedLimeUnitPrice;
    private BigDecimal saltUnitPrice;
    private BigDecimal dieselUnitPrice;
    private BigDecimal silicaSandUseUnitPrice;
    private BigDecimal srfUnitPrice;
    private BigDecimal flyAshGeneratedUnitPrice;
    private BigDecimal flyAshOut01UnitPrice;
    private BigDecimal flyAshOut02UnitPrice;
    private BigDecimal flyAshOut03UnitPrice;
    private BigDecimal flyAshOut04UnitPrice;
    private BigDecimal flyAshOut05UnitPrice;
    private BigDecimal flyAshOut06UnitPrice;
    private BigDecimal flyAshOut07UnitPrice;
    private BigDecimal flyAshOut08UnitPrice;
    private BigDecimal bottomAshGeneratedUnitPrice;
    private BigDecimal bottomAshOut01UnitPrice;
    private BigDecimal bottomAshOut02UnitPrice;
    private BigDecimal bottomAshOut03UnitPrice;
    private BigDecimal bottomAshOut04UnitPrice;
    private BigDecimal powerUnitPrice;
    private BigDecimal powerUreaUnitPrice;
    private BigDecimal powerFdFanUnitPrice;
    private BigDecimal reuseWaterUnitPrice;
    private BigDecimal condensateUnitPrice;
    private BigDecimal boilerFeedWaterUnitPrice;
    private BigDecimal wasteWaterUnitPrice;
    private BigDecimal softWaterUnitPrice;
    private BigDecimal sludgeOutsource01UnitPrice;
    private BigDecimal sludgeOutsource02UnitPrice;
    private BigDecimal sludgeOutsource03UnitPrice;
    private BigDecimal sludgeOutsource04UnitPrice;
    private BigDecimal sludgeOutsource05UnitPrice;
    private BigDecimal sludgeOutsource06UnitPrice;
    private BigDecimal sludgeOutsource07UnitPrice;
    private BigDecimal sludgeOutsource08UnitPrice;
    private BigDecimal sludgeOutsource09UnitPrice;
    private BigDecimal sludgeOutsource10UnitPrice;
    private BigDecimal sludgeOutsource11UnitPrice;
    private BigDecimal inspectionFeeUnitPrice;
    private String silicaSandUseNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isUpdate;

    public static SteamFluidizedDetailMonthResponse from(SteamFluidizedDetailMonth e) {
        return from(e, null);
    }

    public static SteamFluidizedDetailMonthResponse from(SteamFluidizedDetailMonth e, Boolean isUpdate) {
        return SteamFluidizedDetailMonthResponse.builder()
                .detailMonthId(e.getDetailMonthId())
                .yearNo(e.getYearNo())
                .monthNo(e.getMonthNo())
                .improvementCost(e.getImprovementCost())
                .extraCost(e.getExtraCost())
                .steamProducedUnitPrice(e.getSteamProducedUnitPrice())
                .ammoniumCarbonateUnitPrice(e.getAmmoniumCarbonateUnitPrice())
                .boilerChemInUnitPrice(e.getBoilerChemInUnitPrice())
                .boilerChemUseUnitPrice(e.getBoilerChemUseUnitPrice())
                .activatedCarbonInUnitPrice(e.getActivatedCarbonInUnitPrice())
                .activatedCarbonUseUnitPrice(e.getActivatedCarbonUseUnitPrice())
                .causticSodaInUnitPrice(e.getCausticSodaInUnitPrice())
                .causticSodaUseUnitPrice(e.getCausticSodaUseUnitPrice())
                .slakedLimeUnitPrice(e.getSlakedLimeUnitPrice())
                .saltUnitPrice(e.getSaltUnitPrice())
                .dieselUnitPrice(e.getDieselUnitPrice())
                .silicaSandUseUnitPrice(e.getSilicaSandUseUnitPrice())
                .srfUnitPrice(e.getSrfUnitPrice())
                .flyAshGeneratedUnitPrice(e.getFlyAshGeneratedUnitPrice())
                .flyAshOut01UnitPrice(e.getFlyAshOut01UnitPrice())
                .flyAshOut02UnitPrice(e.getFlyAshOut02UnitPrice())
                .flyAshOut03UnitPrice(e.getFlyAshOut03UnitPrice())
                .flyAshOut04UnitPrice(e.getFlyAshOut04UnitPrice())
                .flyAshOut05UnitPrice(e.getFlyAshOut05UnitPrice())
                .flyAshOut06UnitPrice(e.getFlyAshOut06UnitPrice())
                .flyAshOut07UnitPrice(e.getFlyAshOut07UnitPrice())
                .flyAshOut08UnitPrice(e.getFlyAshOut08UnitPrice())
                .bottomAshGeneratedUnitPrice(e.getBottomAshGeneratedUnitPrice())
                .bottomAshOut01UnitPrice(e.getBottomAshOut01UnitPrice())
                .bottomAshOut02UnitPrice(e.getBottomAshOut02UnitPrice())
                .bottomAshOut03UnitPrice(e.getBottomAshOut03UnitPrice())
                .bottomAshOut04UnitPrice(e.getBottomAshOut04UnitPrice())
                .powerUnitPrice(e.getPowerUnitPrice())
                .powerUreaUnitPrice(e.getPowerUreaUnitPrice())
                .powerFdFanUnitPrice(e.getPowerFdFanUnitPrice())
                .reuseWaterUnitPrice(e.getReuseWaterUnitPrice())
                .condensateUnitPrice(e.getCondensateUnitPrice())
                .boilerFeedWaterUnitPrice(e.getBoilerFeedWaterUnitPrice())
                .wasteWaterUnitPrice(e.getWasteWaterUnitPrice())
                .softWaterUnitPrice(e.getSoftWaterUnitPrice())
                .sludgeOutsource01UnitPrice(e.getSludgeOutsource01UnitPrice())
                .sludgeOutsource02UnitPrice(e.getSludgeOutsource02UnitPrice())
                .sludgeOutsource03UnitPrice(e.getSludgeOutsource03UnitPrice())
                .sludgeOutsource04UnitPrice(e.getSludgeOutsource04UnitPrice())
                .sludgeOutsource05UnitPrice(e.getSludgeOutsource05UnitPrice())
                .sludgeOutsource06UnitPrice(e.getSludgeOutsource06UnitPrice())
                .sludgeOutsource07UnitPrice(e.getSludgeOutsource07UnitPrice())
                .sludgeOutsource08UnitPrice(e.getSludgeOutsource08UnitPrice())
                .sludgeOutsource09UnitPrice(e.getSludgeOutsource09UnitPrice())
                .sludgeOutsource10UnitPrice(e.getSludgeOutsource10UnitPrice())
                .sludgeOutsource11UnitPrice(e.getSludgeOutsource11UnitPrice())
                .inspectionFeeUnitPrice(e.getInspectionFeeUnitPrice())
                .silicaSandUseNote(e.getSilicaSandUseNote())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }
}
