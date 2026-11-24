package com.company.module.epr.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.epr.dto.AnnualSalesSummary;
import com.company.module.epr.dto.PartSummary;
import com.company.module.epr.dto.SalesCalculationResult;
import com.company.module.epr.dto.SkuCalculation;
import com.company.module.epr.dto.UnregisteredSale;
import com.company.module.epr.entity.EprAnnualSales;
import com.company.module.epr.entity.EprAnnualSalesDetail;
import com.company.module.epr.entity.EprPartCategory;
import com.company.module.epr.repository.EprAnnualSalesDetailRepository;
import com.company.module.epr.repository.EprAnnualSalesRepository;
import com.company.module.epr.repository.EprPartCategoryRepository;
import com.company.module.epr.repository.EprProductMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 연도별 판매 실적 저장 · 조회 · 전년 대비 비교.
 * 신고 연도당 1건만 둔다. 같은 연도를 다시 올리면(사용자 확인 후) 집계를 고치고 SKU 상세를 새로 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EprAnnualSalesService {

    public static final int MIN_YEAR = 2000;
    public static final int MAX_YEAR = 2200;
    private static final int BATCH = 500;

    private final EprAnnualSalesRepository annualRepository;
    private final EprAnnualSalesDetailRepository detailRepository;
    private final EprPartCategoryRepository partRepository;
    private final EprProductMasterRepository productRepository;

    public static void requireYear(int year, String label) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, label + "는 2000~2200 범위로 입력하세요.");
        }
    }

    public boolean exists(int reportYear) {
        return annualRepository.existsByReportYear(reportYear);
    }

    /** 이미 저장된 연도인데 replaceExisting=false 면 409 로 막는다 (화면이 사용자에게 교체 여부를 묻고 다시 보낸다). */
    public void requireReplaceConfirmed(int reportYear, boolean replaceExisting) {
        requireYear(reportYear, "신고 연도");
        if (!replaceExisting && exists(reportYear)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    reportYear + "년 판매 자료가 이미 저장되어 있습니다. 덮어쓰기를 확인하세요.");
        }
    }

    @Transactional
    public void replaceYear(int reportYear, SalesCalculationResult result, boolean replaceExisting, Long userId) {
        requireReplaceConfirmed(reportYear, replaceExisting);
        EprAnnualSales annual = annualRepository.findAnyByReportYear(reportYear).orElse(null);
        if (annual == null) {
            annual = EprAnnualSales.create(reportYear, result, userId);
        } else if (annual.isDeleted()) {
            annual.restore(result, userId);
        } else {
            annual.update(result, userId);
        }
        annual = annualRepository.saveAndFlush(annual);
        detailRepository.softDeleteAllByAnnualSalesId(annual.getAnnualSalesId(), userId, LocalDateTime.now());
        List<SkuCalculation> skus = result.skuDetails();
        for (int start = 0; start < skus.size(); start += BATCH) {
            EprAnnualSales header = annual;
            detailRepository.saveAll(skus.subList(start, Math.min(start + BATCH, skus.size())).stream()
                    .map(sku -> EprAnnualSalesDetail.of(header, sku, userId)).toList());
            detailRepository.flush();
        }
    }

    /** 연도별 총 판매 EA · 신고중량과 전년 대비 증감. 시작 연도의 전년 자료가 있으면 그 증감도 낸다. */
    public List<AnnualSalesSummary> compare(int fromYear, int toYear) {
        if (fromYear < MIN_YEAR || toYear > MAX_YEAR || fromYear > toYear || toYear - fromYear > 30) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도 범위는 2000~2200, 최대 31개 연도까지 비교할 수 있습니다.");
        }
        int lookupFromYear = Math.max(MIN_YEAR, fromYear - 1);
        List<EprAnnualSales> summaries = annualRepository.findAllByReportYearBetweenOrderByReportYearAsc(lookupFromYear, toYear);
        Map<Integer, EprAnnualSales> byYear = new HashMap<>();
        summaries.forEach(item -> byYear.put(item.getReportYear(), item));
        return summaries.stream()
                .filter(item -> item.getReportYear() >= fromYear)
                .map(item -> {
                    EprAnnualSales previous = byYear.get(item.getReportYear() - 1);
                    var salesChange = previous == null ? null : item.getTotalSalesEa().subtract(previous.getTotalSalesEa());
                    var weightChange = previous == null ? null
                            : item.getTotalReportWeightKg().subtract(previous.getTotalReportWeightKg());
                    return new AnnualSalesSummary(item.getReportYear(), item.getAcceptedRows(),
                            item.getUniqueSkuCount(), item.getUnregisteredSkuCount(), item.getMissingWeightSkuCount(),
                            item.getTotalSalesEa(), item.getTotalReportWeightKg(), salesChange, weightChange,
                            item.getCalculatedAt());
                }).toList();
    }

    /**
     * 저장된 연도의 결과. 신고값(입수량·포장중량·신고중량)은 계산 당시 값을 그대로 쓰고,
     * 파트명과 자사/타사 구분만 지금의 파트·마스터 값으로 보여 준다.
     */
    public SalesCalculationResult result(int reportYear) {
        EprAnnualSales annual = annualRepository.findByReportYear(reportYear)
                .orElseThrow(() -> new EntityNotFoundException(reportYear + "년 저장된 판매 집계가 없습니다."));
        List<EprAnnualSalesDetail> savedDetails =
                detailRepository.findAllByAnnualSalesIdOrderByPartPrefixAscProductCodeAsc(annual.getAnnualSalesId());
        Map<String, String> currentPartNames = partRepository.findAllByOrderByPartPrefixAsc().stream()
                .collect(Collectors.toMap(EprPartCategory::getPartPrefix, EprPartCategory::getPartName, (l, r) -> l));
        List<String> registeredCodes = savedDetails.stream()
                .filter(detail -> !EprCalculationService.STATUS_UNREGISTERED.equals(detail.getStatus()))
                .map(EprAnnualSalesDetail::getProductCode).distinct().toList();
        Map<String, String> currentCompanyTypes = new HashMap<>();
        for (int start = 0; start < registeredCodes.size(); start += BATCH) {
            productRepository.findAllByProductCodeIn(registeredCodes.subList(start, Math.min(start + BATCH, registeredCodes.size())))
                    .forEach(product -> currentCompanyTypes.put(product.getProductCode(), product.getCompanyType()));
        }

        List<SkuCalculation> skuDetails = savedDetails.stream().map(detail -> new SkuCalculation(
                detail.getPartPrefix(),
                currentPartNames.getOrDefault(detail.getPartPrefix(), detail.getPartName()),
                detail.getProductCode(), detail.getMasterProductName(),
                currentCompanyTypes.getOrDefault(detail.getProductCode(), ""),
                detail.getUploadedProductName(), detail.getSalesEa(),
                detail.getEaPerBag(), detail.getBagWeightG(), detail.getConvertedBag(),
                detail.getConvertedBag().multiply(detail.getBagWeightG()), detail.getBagReportWeightKg(),
                detail.getEaPerBox(), detail.getBoxWeightG(), detail.getConvertedBox(),
                detail.getConvertedBox().multiply(detail.getBoxWeightG()), detail.getBoxReportWeightKg(),
                detail.getTotalReportWeightKg(), detail.getStatus(), detail.getSourceRows())).toList();

        List<PartSummary> partSummaries = EprCalculationService.summarizeByPart(skuDetails);
        List<UnregisteredSale> unregistered = skuDetails.stream()
                .filter(detail -> EprCalculationService.STATUS_UNREGISTERED.equals(detail.status()))
                .map(detail -> new UnregisteredSale(detail.productCode(), detail.uploadedProductName(),
                        detail.salesEa(), detail.sourceRows())).toList();
        return new SalesCalculationResult(reportYear, annual.getCalculatedAt(), annual.getAcceptedRows(),
                annual.getTotalSalesEa(), annual.getUniqueSkuCount(), annual.getUnregisteredSkuCount(),
                annual.getMissingWeightSkuCount(), annual.getTotalReportWeightKg(), partSummaries,
                skuDetails, unregistered, List.of());
    }

    public List<Integer> availableYears() {
        return annualRepository.findAllByOrderByReportYearAsc().stream().map(EprAnnualSales::getReportYear).toList();
    }
}
