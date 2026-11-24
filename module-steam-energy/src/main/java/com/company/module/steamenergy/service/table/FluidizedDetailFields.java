package com.company.module.steamenergy.service.table;

import java.util.Map;

/**
 * 유동상 세부 운영내역: 서식(엑셀 "3. 세부 운영내역") 행 번호 → 규격 표 필드.
 * 화면 쪽 같은 표는 {@code static/steam/js/fluidized-detail-store.js}.
 *
 * <ul>
 *   <li>{@link #DAY_ROWS}: 일자 열(C~AG = 1~31일)의 입력 행 → steam_fluidized_detail_daily</li>
 *   <li>{@link #PRICE_ROWS}: 단가 열(AJ)의 입력 행, {@link #COST_ROWS}: 비용 열(AK)의 입력 행 → steam_fluidized_detail_month</li>
 * </ul>
 */
public final class FluidizedDetailFields {

    private FluidizedDetailFields() {
    }

    /** 규사 사용 줄(40행)에 적힌 글자 ("사용량 측정불가") — 월별 표에 둔다. 서식 자리는 C40. */
    public static final String NOTE_FIELD = "silicaSandUseNote";
    public static final int NOTE_ROW = 40;

    public static final Map<Integer, String> DAY_ROWS = Map.ofEntries(
            Map.entry(4, "runHours"),
            Map.entry(6, "steamProducedTon"),
            Map.entry(8, "sludgeBurnTon"),
            Map.entry(9, "srfBurnTon"),
            Map.entry(11, "steamUnitPrice"),
            Map.entry(13, "stopHourUnitPrice"),
            Map.entry(18, "ammoniumCarbonateInKg"),
            Map.entry(19, "ammoniumCarbonateUseKg"),
            Map.entry(21, "boilerChemInKg"),
            Map.entry(22, "boilerChemUseKg"),
            Map.entry(24, "activatedCarbonInKg"),
            Map.entry(25, "activatedCarbonUseKg"),
            Map.entry(27, "causticSodaInKg"),
            Map.entry(28, "causticSodaUseKg"),
            Map.entry(30, "slakedLimeInKg"),
            Map.entry(31, "slakedLimeUseKg"),
            Map.entry(33, "saltInKg"),
            Map.entry(34, "saltUseKg"),
            Map.entry(36, "dieselInL"),
            Map.entry(37, "dieselUseL"),
            Map.entry(39, "silicaSandInKg"),
            Map.entry(40, "silicaSandUseKg"),
            Map.entry(41, "activatedCarbonExtraInKg"),
            Map.entry(42, "emergencyDieselInL"),
            Map.entry(46, "srfInTon"),
            Map.entry(49, "flyAshGeneratedTon"),
            Map.entry(50, "flyAshOut01Ton"),
            Map.entry(51, "flyAshOut02Ton"),
            Map.entry(52, "flyAshOut03Ton"),
            Map.entry(53, "flyAshOut04Ton"),
            Map.entry(54, "flyAshOut05Ton"),
            Map.entry(55, "flyAshOut06Ton"),
            Map.entry(56, "flyAshOut07Ton"),
            Map.entry(57, "flyAshOut08Ton"),
            Map.entry(58, "bottomAshGeneratedTon"),
            Map.entry(59, "bottomAshOut01Ton"),
            Map.entry(60, "bottomAshOut02Ton"),
            Map.entry(61, "bottomAshOut03Ton"),
            Map.entry(62, "bottomAshOut04Ton"),
            Map.entry(65, "powerMainKwh"),
            Map.entry(66, "powerAshVentFilterKwh"),
            Map.entry(67, "powerBagFilterKwh"),
            Map.entry(68, "powerScrubberKwh"),
            Map.entry(69, "powerCarbonFeedKwh"),
            Map.entry(70, "powerLimeFeedKwh"),
            Map.entry(71, "powerUreaKwh"),
            Map.entry(72, "powerFdFanKwh"),
            Map.entry(74, "reuseWaterTon"),
            Map.entry(75, "riverbedWaterTon"),
            Map.entry(76, "condensateTon"),
            Map.entry(77, "boilerFeedWaterTon"),
            Map.entry(78, "wasteWaterTon"),
            Map.entry(79, "softWaterTon"),
            Map.entry(81, "sludgeOutsource01Ton"),
            Map.entry(82, "sludgeOutsource02Ton"),
            Map.entry(83, "sludgeOutsource03Ton"),
            Map.entry(84, "sludgeOutsource04Ton"),
            Map.entry(85, "sludgeOutsource05Ton"),
            Map.entry(86, "sludgeOutsource06Ton"),
            Map.entry(87, "sludgeOutsource07Ton"),
            Map.entry(88, "sludgeOutsource08Ton"),
            Map.entry(89, "sludgeOutsource09Ton"),
            Map.entry(90, "sludgeOutsource10Ton"),
            Map.entry(91, "sludgeOutsource11Ton"),
            Map.entry(92, "inspectionFeeQty")
    );

    public static final Map<Integer, String> PRICE_ROWS = Map.ofEntries(
            Map.entry(6, "steamProducedUnitPrice"),
            Map.entry(18, "ammoniumCarbonateUnitPrice"),
            Map.entry(21, "boilerChemInUnitPrice"),
            Map.entry(22, "boilerChemUseUnitPrice"),
            Map.entry(24, "activatedCarbonInUnitPrice"),
            Map.entry(25, "activatedCarbonUseUnitPrice"),
            Map.entry(27, "causticSodaInUnitPrice"),
            Map.entry(28, "causticSodaUseUnitPrice"),
            Map.entry(30, "slakedLimeUnitPrice"),
            Map.entry(33, "saltUnitPrice"),
            Map.entry(36, "dieselUnitPrice"),
            Map.entry(40, "silicaSandUseUnitPrice"),
            Map.entry(46, "srfUnitPrice"),
            Map.entry(49, "flyAshGeneratedUnitPrice"),
            Map.entry(50, "flyAshOut01UnitPrice"),
            Map.entry(51, "flyAshOut02UnitPrice"),
            Map.entry(52, "flyAshOut03UnitPrice"),
            Map.entry(53, "flyAshOut04UnitPrice"),
            Map.entry(54, "flyAshOut05UnitPrice"),
            Map.entry(55, "flyAshOut06UnitPrice"),
            Map.entry(56, "flyAshOut07UnitPrice"),
            Map.entry(57, "flyAshOut08UnitPrice"),
            Map.entry(58, "bottomAshGeneratedUnitPrice"),
            Map.entry(59, "bottomAshOut01UnitPrice"),
            Map.entry(60, "bottomAshOut02UnitPrice"),
            Map.entry(61, "bottomAshOut03UnitPrice"),
            Map.entry(62, "bottomAshOut04UnitPrice"),
            Map.entry(65, "powerUnitPrice"),
            Map.entry(71, "powerUreaUnitPrice"),
            Map.entry(72, "powerFdFanUnitPrice"),
            Map.entry(74, "reuseWaterUnitPrice"),
            Map.entry(76, "condensateUnitPrice"),
            Map.entry(77, "boilerFeedWaterUnitPrice"),
            Map.entry(78, "wasteWaterUnitPrice"),
            Map.entry(79, "softWaterUnitPrice"),
            Map.entry(81, "sludgeOutsource01UnitPrice"),
            Map.entry(82, "sludgeOutsource02UnitPrice"),
            Map.entry(83, "sludgeOutsource03UnitPrice"),
            Map.entry(84, "sludgeOutsource04UnitPrice"),
            Map.entry(85, "sludgeOutsource05UnitPrice"),
            Map.entry(86, "sludgeOutsource06UnitPrice"),
            Map.entry(87, "sludgeOutsource07UnitPrice"),
            Map.entry(88, "sludgeOutsource08UnitPrice"),
            Map.entry(89, "sludgeOutsource09UnitPrice"),
            Map.entry(90, "sludgeOutsource10UnitPrice"),
            Map.entry(91, "sludgeOutsource11UnitPrice"),
            Map.entry(92, "inspectionFeeUnitPrice")
    );

    public static final Map<Integer, String> COST_ROWS = Map.ofEntries(
            Map.entry(15, "improvementCost"),
            Map.entry(16, "extraCost")
    );
}
