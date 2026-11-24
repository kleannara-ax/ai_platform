package com.company.module.epr.service;

import com.company.module.epr.dto.PartSummary;
import com.company.module.epr.dto.SalesCalculationResult;
import com.company.module.epr.dto.SalesExcelRow;
import com.company.module.epr.dto.SkuCalculation;
import com.company.module.epr.dto.UnregisteredSale;
import com.company.module.epr.dto.UploadIssue;
import com.company.module.epr.entity.EprProductMaster;
import com.company.module.epr.repository.EprPartCategoryRepository;
import com.company.module.epr.repository.EprProductMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 판매수량 → 신고중량 계산 (DB 저장 없음).
 *
 * <ul>
 *   <li>같은 제품코드(trim · 대소문자 무시)는 판매수량을 합산한다.</li>
 *   <li>환산 BAG = EA ÷ EA/BAG, 환산 BOX = EA ÷ EA/BOX (소수 6자리 HALF_UP). 입수량이 0 이면 0.</li>
 *   <li>신고중량(kg) = 환산수량 × 포장중량(g) ÷ 1,000, 소수 둘째 자리 올림(CEILING).</li>
 *   <li>마스터에 없는 코드는 "미등록코드" 로 따로 모으고 파트 집계에서 뺀다.</li>
 *   <li>판매가 있는데 신고중량이 0 이면 "중량미입력".</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EprCalculationService {

    public static final String STATUS_OK = "정상";
    public static final String STATUS_UNREGISTERED = "미등록코드";
    public static final String STATUS_MISSING_WEIGHT = "중량미입력";

    private static final int DIVISION_SCALE = 6;
    private static final int RESULT_SCALE = 2;
    private static final int BATCH = 500;
    private static final BigDecimal GRAMS_PER_KILOGRAM = new BigDecimal("1000");

    private final EprProductMasterRepository productRepository;
    private final EprPartCategoryRepository partRepository;

    public SalesCalculationResult calculate(int reportYear, List<SalesExcelRow> rows, List<UploadIssue> parseIssues) {
        List<UploadIssue> issues = new ArrayList<>(parseIssues);
        Map<String, AggregatedSale> salesByCode = new LinkedHashMap<>();
        int acceptedRows = 0;
        for (SalesExcelRow row : rows) {
            String code = EprProductMaster.normalizeCode(row.productCode());
            if (code.length() < 3 || code.length() > 30) {
                issues.add(new UploadIssue(row.rowNumber(), code, "제품코드는 3~30자여야 합니다."));
                continue;
            }
            if (row.salesEa() == null || row.salesEa().signum() < 0) {
                issues.add(new UploadIssue(row.rowNumber(), code, "판매수량 EA는 0 이상의 숫자여야 합니다."));
                continue;
            }
            salesByCode.compute(code, (key, old) -> old == null
                    ? new AggregatedSale(row.productName(), row.salesEa(), 1)
                    : old.plus(row.salesEa()));
            acceptedRows++;
        }

        Map<String, EprProductMaster> productByCode = findProducts(salesByCode.keySet()).stream()
                .collect(Collectors.toMap(product -> EprProductMaster.normalizeCode(product.getProductCode()),
                        product -> product, (left, right) -> left));
        Map<String, String> partNames = findPartNames(salesByCode.keySet().stream()
                .map(code -> code.substring(0, 3)).collect(Collectors.toSet()));

        List<SkuCalculation> details = new ArrayList<>();
        List<UnregisteredSale> unregistered = new ArrayList<>();
        for (var entry : salesByCode.entrySet()) {
            String code = entry.getKey();
            AggregatedSale sale = entry.getValue();
            String prefix = code.substring(0, 3);
            String partName = partNames.getOrDefault(prefix, "미분류(" + prefix + ")");
            EprProductMaster product = productByCode.get(code);
            if (product == null) {
                unregistered.add(new UnregisteredSale(code, sale.uploadedName(), sale.quantity(), sale.sourceRows()));
                details.add(new SkuCalculation(prefix, partName, code, "", "", sale.uploadedName(), sale.quantity(),
                        0, BigDecimal.ZERO.setScale(4), zero6(), zero6(), zero2(), 0,
                        BigDecimal.ZERO.setScale(4), zero6(), zero6(), zero2(), zero2(), STATUS_UNREGISTERED,
                        sale.sourceRows()));
                continue;
            }
            BigDecimal convertedBag = divideSafe(sale.quantity(), product.getEaPerBag());
            BigDecimal convertedBox = divideSafe(sale.quantity(), product.getEaPerBox());
            BigDecimal bagWeight = nonNull(product.getBagWeight());
            BigDecimal boxWeight = nonNull(product.getBoxWeight());
            BigDecimal rawBagReportG = convertedBag.multiply(bagWeight);
            BigDecimal rawBoxReportG = convertedBox.multiply(boxWeight);
            BigDecimal totalKg = gramsToKgCeiling(rawBagReportG.add(rawBoxReportG));
            boolean missingWeight = sale.quantity().signum() > 0 && totalKg.signum() == 0;
            details.add(new SkuCalculation(prefix, partName, code, product.getProductName(), product.getCompanyType(),
                    sale.uploadedName(), sale.quantity(), product.getEaPerBag(), bagWeight, convertedBag, rawBagReportG,
                    gramsToKgCeiling(rawBagReportG), product.getEaPerBox(), boxWeight, convertedBox, rawBoxReportG,
                    gramsToKgCeiling(rawBoxReportG), totalKg, missingWeight ? STATUS_MISSING_WEIGHT : STATUS_OK,
                    sale.sourceRows()));
        }

        List<PartSummary> summaries = summarizeByPart(details);
        BigDecimal grandTotalKg = summaries.stream().map(PartSummary::totalReportWeightKg)
                .reduce(zero2(), BigDecimal::add).setScale(RESULT_SCALE, RoundingMode.CEILING);
        BigDecimal totalSalesEa = salesByCode.values().stream().map(AggregatedSale::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int missingCount = (int) details.stream().filter(item -> STATUS_MISSING_WEIGHT.equals(item.status())).count();
        return new SalesCalculationResult(reportYear, LocalDateTime.now(), acceptedRows, totalSalesEa, details.size(),
                unregistered.size(), missingCount, grandTotalKg, summaries, List.copyOf(details),
                List.copyOf(unregistered), List.copyOf(issues));
    }

    /**
     * 파트별 합계. 파트 신고중량은 SKU 의 올림 전 g 값을 더한 뒤 한 번만 kg 올림한다.
     * 저장된 연도를 다시 불러올 때(EprAnnualSalesService)도 같은 방식으로 다시 계산한다.
     */
    public static List<PartSummary> summarizeByPart(List<SkuCalculation> details) {
        Map<String, PartAccumulator> partTotals = new TreeMap<>();
        for (SkuCalculation detail : details) {
            if (STATUS_UNREGISTERED.equals(detail.status())) continue;
            partTotals.computeIfAbsent(detail.partPrefix(), key -> new PartAccumulator(key, detail.partName()))
                    .add(detail);
        }
        return partTotals.values().stream().map(PartAccumulator::toSummary).toList();
    }

    private List<EprProductMaster> findProducts(Set<String> codes) {
        List<String> allCodes = codes.stream().sorted().toList();
        List<EprProductMaster> products = new ArrayList<>();
        for (int start = 0; start < allCodes.size(); start += BATCH) {
            products.addAll(productRepository.findAllByProductCodeIn(
                    allCodes.subList(start, Math.min(start + BATCH, allCodes.size()))));
        }
        return products;
    }

    private Map<String, String> findPartNames(Set<String> prefixes) {
        List<String> ordered = prefixes.stream().sorted().toList();
        Map<String, String> names = new LinkedHashMap<>();
        for (int start = 0; start < ordered.size(); start += BATCH) {
            partRepository.findAllByPartPrefixIn(ordered.subList(start, Math.min(start + BATCH, ordered.size())))
                    .forEach(part -> names.put(part.getPartPrefix(), part.getPartName()));
        }
        return names;
    }

    private BigDecimal divideSafe(BigDecimal sales, Integer divisor) {
        if (divisor == null || divisor <= 0) return zero6();
        return sales.divide(BigDecimal.valueOf(divisor), DIVISION_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal gramsToKgCeiling(BigDecimal grams) {
        return grams.divide(GRAMS_PER_KILOGRAM, RESULT_SCALE, RoundingMode.CEILING);
    }

    private static BigDecimal nonNull(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static BigDecimal zero6() { return BigDecimal.ZERO.setScale(DIVISION_SCALE, RoundingMode.HALF_UP); }
    private static BigDecimal zero2() { return BigDecimal.ZERO.setScale(RESULT_SCALE, RoundingMode.CEILING); }

    private record AggregatedSale(String uploadedName, BigDecimal quantity, int sourceRows) {
        AggregatedSale plus(BigDecimal amount) {
            return new AggregatedSale(uploadedName, quantity.add(amount), sourceRows + 1);
        }
    }

    private static final class PartAccumulator {
        private final String prefix;
        private final String name;
        private long skuCount;
        private BigDecimal salesEa = BigDecimal.ZERO;
        private BigDecimal convertedBag = BigDecimal.ZERO;
        private BigDecimal bagWeightG = BigDecimal.ZERO;
        private BigDecimal convertedBox = BigDecimal.ZERO;
        private BigDecimal boxWeightG = BigDecimal.ZERO;

        private PartAccumulator(String prefix, String name) {
            this.prefix = prefix;
            this.name = name;
        }

        private void add(SkuCalculation detail) {
            skuCount++;
            salesEa = salesEa.add(detail.salesEa());
            convertedBag = convertedBag.add(detail.convertedBag());
            bagWeightG = bagWeightG.add(detail.rawBagReportWeightG());
            convertedBox = convertedBox.add(detail.convertedBox());
            boxWeightG = boxWeightG.add(detail.rawBoxReportWeightG());
        }

        private PartSummary toSummary() {
            return new PartSummary(prefix, name, skuCount, salesEa,
                    convertedBag.setScale(3, RoundingMode.HALF_UP), gramsToKgCeiling(bagWeightG),
                    convertedBox.setScale(3, RoundingMode.HALF_UP), gramsToKgCeiling(boxWeightG),
                    gramsToKgCeiling(bagWeightG.add(boxWeightG)));
        }
    }
}
