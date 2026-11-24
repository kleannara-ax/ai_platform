package com.company.module.epr.service;

import com.company.module.epr.dto.SalesExcelRow;
import com.company.module.epr.entity.EprPartCategory;
import com.company.module.epr.entity.EprProductMaster;
import com.company.module.epr.repository.EprPartCategoryRepository;
import com.company.module.epr.repository.EprProductMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/** 원래 프로젝트 EprCalculationServiceTest 를 그대로 옮긴 것 (계산 규칙이 바뀌지 않았는지 확인) */
@ExtendWith(MockitoExtension.class)
class EprCalculationServiceTest {

    @Mock
    private EprProductMasterRepository productRepository;
    @Mock
    private EprPartCategoryRepository partRepository;
    @InjectMocks
    private EprCalculationService calculationService;

    private static EprProductMaster product(String code, String name, int eaPerBag, int bagPerBox, int eaPerBox,
                                            String bagWeight, String boxWeight, String companyType) {
        return EprProductMaster.builder().productCode(code).productName(name)
                .eaPerBag(eaPerBag).bagPerBox(bagPerBox).eaPerBox(eaPerBox).bagPerPlt(1).boxPerPlt(1)
                .bagWeight(new BigDecimal(bagWeight)).boxWeight(new BigDecimal(boxWeight)).companyType(companyType)
                .build();
    }

    @Test
    void aggregatesDuplicateCodesAndUsesBigDecimalForReportWeights() {
        when(productRepository.findAllByProductCodeIn(anyCollection())).thenReturn(List.of(
                product("FAD-DRP0001A", "마스터 제품명", 12, 10, 120, "125.00", "350.00", "타사")));
        when(partRepository.findAllByPartPrefixIn(anyCollection())).thenReturn(List.of(
                EprPartCategory.builder().partPrefix("FAD").partName("기저귀 제품").build()));

        var result = calculationService.calculate(2026, List.of(
                new SalesExcelRow(3, " fad-drp0001a ", "업로드명 A", new BigDecimal("60")),
                new SalesExcelRow(4, "FAD-DRP0001A", "업로드명 B", new BigDecimal("60"))), List.of());

        assertThat(result.reportYear()).isEqualTo(2026);
        assertThat(result.uniqueSkuCount()).isEqualTo(1);
        assertThat(result.skuDetails()).hasSize(1);
        assertThat(result.skuDetails().get(0).salesEa()).isEqualByComparingTo("120");
        assertThat(result.skuDetails().get(0).sourceRows()).isEqualTo(2);
        assertThat(result.skuDetails().get(0).masterProductName()).isEqualTo("마스터 제품명");
        assertThat(result.skuDetails().get(0).companyType()).isEqualTo("타사");
        assertThat(result.skuDetails().get(0).bagReportWeightKg()).isEqualByComparingTo("1.250");
        assertThat(result.skuDetails().get(0).boxReportWeightKg()).isEqualByComparingTo("0.350");
        assertThat(result.grandTotalWeightKg()).isEqualByComparingTo("1.600");
        assertThat(result.partSummaries()).singleElement().satisfies(part -> {
            assertThat(part.partPrefix()).isEqualTo("FAD");
            assertThat(part.partName()).isEqualTo("기저귀 제품");
            assertThat(part.totalBagReportWeightKg()).isEqualByComparingTo("1.25");
            assertThat(part.totalBoxReportWeightKg()).isEqualByComparingTo("0.35");
            assertThat(part.totalReportWeightKg()).isEqualByComparingTo("1.60");
        });
    }

    @Test
    void gramsAreConvertedToKilogramsAndRoundedUpToTwoDecimals() {
        when(productRepository.findAllByProductCodeIn(anyCollection())).thenReturn(List.of(
                product("FRT-ONE0001", "1g 포장 제품", 1, 1, 1, "1.00", "1.00", null)));
        when(partRepository.findAllByPartPrefixIn(anyCollection())).thenReturn(List.of());

        var result = calculationService.calculate(2026, List.of(
                new SalesExcelRow(2, "FRT-ONE0001", "1g 포장 제품", BigDecimal.ONE)), List.of());

        assertThat(result.skuDetails().get(0).companyType()).isEqualTo("자사");
        assertThat(result.skuDetails().get(0).bagReportWeightKg()).isEqualByComparingTo("0.01");
        assertThat(result.skuDetails().get(0).boxReportWeightKg()).isEqualByComparingTo("0.01");
        assertThat(result.skuDetails().get(0).totalReportWeightKg()).isEqualByComparingTo("0.01");
        assertThat(result.partSummaries()).singleElement().satisfies(part -> {
            assertThat(part.partName()).isEqualTo("미분류(FRT)");
            assertThat(part.totalBagReportWeightKg()).isEqualByComparingTo("0.01");
            assertThat(part.totalBoxReportWeightKg()).isEqualByComparingTo("0.01");
            assertThat(part.totalReportWeightKg()).isEqualByComparingTo("0.01");
        });
        assertThat(result.grandTotalWeightKg()).isEqualByComparingTo("0.01");
    }

    @Test
    void zeroDivisorsDoNotThrowAndUnregisteredCodesAreSeparated() {
        when(productRepository.findAllByProductCodeIn(anyCollection())).thenReturn(List.of(
                product("FRT-ZERO01", "입수량 미등록", 0, 0, 0, "0", "0", "자사")));
        when(partRepository.findAllByPartPrefixIn(anyCollection())).thenReturn(List.of());

        var result = calculationService.calculate(2026, List.of(
                new SalesExcelRow(2, "FRT-ZERO01", "입수량 미등록", new BigDecimal("50")),
                new SalesExcelRow(3, "FAD-MISSING01", "미등록 제품", new BigDecimal("7"))), List.of());

        assertThat(result.skuDetails()).hasSize(2);
        assertThat(result.skuDetails().get(0).convertedBag()).isEqualByComparingTo("0.000");
        assertThat(result.skuDetails().get(0).status()).isEqualTo(EprCalculationService.STATUS_MISSING_WEIGHT);
        assertThat(result.missingWeightSkuCount()).isEqualTo(1);
        assertThat(result.unregisteredSkuCount()).isEqualTo(1);
        assertThat(result.unregisteredSales().get(0).productCode()).isEqualTo("FAD-MISSING01");
        assertThat(result.partSummaries()).hasSize(1);
        assertThat(result.grandTotalWeightKg()).isEqualByComparingTo("0.000");
    }

    @Test
    void invalidRowsBecomeIssuesAndAreNotCounted() {
        var result = calculationService.calculate(2026, List.of(
                new SalesExcelRow(2, "AB", "짧은 코드", BigDecimal.TEN),
                new SalesExcelRow(3, "FRT-NEG01", "음수", new BigDecimal("-1"))), List.of());

        assertThat(result.acceptedRows()).isZero();
        assertThat(result.issues()).extracting("rowNumber").containsExactly(2, 3);
    }
}
