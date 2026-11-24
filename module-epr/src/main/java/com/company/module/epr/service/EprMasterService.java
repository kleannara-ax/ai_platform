package com.company.module.epr.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.epr.dto.MasterExcelRow;
import com.company.module.epr.dto.PartCategoryResponse;
import com.company.module.epr.dto.PartCategorySaveRequest;
import com.company.module.epr.dto.ProductMasterResponse;
import com.company.module.epr.dto.ProductMasterSaveRequest;
import com.company.module.epr.dto.UploadIssue;
import com.company.module.epr.dto.UploadSummary;
import com.company.module.epr.dto.WeightExcelRow;
import com.company.module.epr.entity.EprPartCategory;
import com.company.module.epr.entity.EprProductMaster;
import com.company.module.epr.repository.EprPartCategoryRepository;
import com.company.module.epr.repository.EprProductMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 제품 마스터 · 파트 코드 관리.
 * 제품을 저장할 때 제품코드 앞 3자리 파트가 없으면 "미분류(코드)" 파트를 자동으로 만든다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EprMasterService {

    private static final int BATCH = 500;

    private final EprProductMasterRepository productRepository;
    private final EprPartCategoryRepository partRepository;

    // ───────────────────────── 조회 ─────────────────────────

    public List<ProductMasterResponse> searchProducts(String keyword, String partPrefix) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String part = partPrefix == null ? "" : EprPartCategory.normalizePrefix(partPrefix);
        Map<String, String> partNames = partNameMap();
        return productRepository.search(kw, part).stream()
                .map(product -> ProductMasterResponse.from(product, partNames.get(product.getPartPrefix())))
                .toList();
    }

    public List<EprProductMaster> allProducts() {
        return productRepository.findAllByOrderByProductCodeAsc();
    }

    public long countProducts() {
        return productRepository.count();
    }

    /** 등록된 파트 코드와 파트별 SKU 수 */
    public List<PartCategoryResponse> getParts() {
        Map<String, Long> counts = new HashMap<>();
        productRepository.countSkuByPartPrefix().forEach(row -> counts.put(row.getPartPrefix(), row.getSkuCount()));
        return partRepository.findAllByOrderByPartPrefixAsc().stream()
                .map(part -> new PartCategoryResponse(part.getPartPrefix(), part.getPartName(),
                        counts.getOrDefault(part.getPartPrefix(), 0L)))
                .toList();
    }

    private Map<String, String> partNameMap() {
        return partRepository.findAllByOrderByPartPrefixAsc().stream()
                .collect(Collectors.toMap(EprPartCategory::getPartPrefix, EprPartCategory::getPartName,
                        (left, right) -> left));
    }

    // ───────────────────────── 제품 단건 ─────────────────────────

    /** 같은 제품코드가 있으면 수정, 없으면 등록한다. */
    @Transactional
    public ProductMasterResponse saveProduct(ProductMasterSaveRequest request, Long userId) {
        String code = EprProductMaster.normalizeCode(request.getProductCode());
        if (code.length() < 3 || code.length() > 30) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "제품코드는 3~30자여야 합니다. (앞 3자리 파트코드 필요)");
        }
        if (request.getProductName() == null || request.getProductName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "제품명을 입력하세요.");
        }
        String companyType = EprProductMaster.normalizeCompanyType(request.getCompanyType());
        EprProductMaster product = productRepository.findAllAnyByProductCodeIn(List.of(code)).stream()
                .findFirst().orElse(null);
        if (product == null) {
            product = EprProductMaster.builder()
                    .productCode(code).productName(request.getProductName())
                    .eaPerBag(request.getEaPerBag()).bagPerBox(request.getBagPerBox()).eaPerBox(request.getEaPerBox())
                    .bagPerPlt(request.getBagPerPlt()).boxPerPlt(request.getBoxPerPlt())
                    .bagWeight(request.getBagWeight()).boxWeight(request.getBoxWeight())
                    .companyType(companyType).createdBy(userId)
                    .build();
        } else {
            if (product.isDeleted()) product.restore(userId);
            product.update(request.getProductName(), request.getEaPerBag(), request.getBagPerBox(),
                    request.getEaPerBox(), request.getBagPerPlt(), request.getBoxPerPlt(),
                    request.getBagWeight(), request.getBoxWeight(), companyType, userId);
        }
        String partName = ensurePart(product.getPartPrefix(), userId).getPartName();
        EprProductMaster saved = productRepository.save(product);
        return ProductMasterResponse.from(saved, partName);
    }

    // ───────────────────────── 엑셀 ─────────────────────────

    /** 제품 마스터 엑셀: 제품코드 기준 신규 등록 또는 전체 항목 갱신 */
    @Transactional
    public UploadSummary upsertMaster(List<MasterExcelRow> rows, List<UploadIssue> parseIssues, Long userId) {
        Map<String, MasterExcelRow> uniqueRows = new LinkedHashMap<>();
        List<UploadIssue> issues = new ArrayList<>(parseIssues);
        for (MasterExcelRow row : rows) {
            String code = EprProductMaster.normalizeCode(row.productCode());
            if (code.isBlank() || code.length() > 30) {
                issues.add(new UploadIssue(row.rowNumber(), code, "제품코드는 1~30자여야 합니다."));
                continue;
            }
            if (row.productName() == null || row.productName().isBlank() || row.productName().length() > 255) {
                issues.add(new UploadIssue(row.rowNumber(), code, "제품명은 1~255자여야 합니다."));
                continue;
            }
            if (List.of(row.eaPerBag(), row.bagPerBox(), row.eaPerBox(), row.bagPerPlt(), row.boxPerPlt()).stream()
                    .anyMatch(value -> value == null || value < 0)) {
                issues.add(new UploadIssue(row.rowNumber(), code, "입수량은 0 이상의 정수여야 합니다."));
                continue;
            }
            uniqueRows.put(code, row);   // 같은 코드가 여러 번 나오면 마지막 행을 쓴다
        }

        Map<String, EprProductMaster> existing = new HashMap<>();
        List<String> codes = List.copyOf(uniqueRows.keySet());
        for (int start = 0; start < codes.size(); start += BATCH) {
            productRepository.findAllAnyByProductCodeIn(codes.subList(start, Math.min(start + BATCH, codes.size())))
                    .forEach(product -> existing.put(EprProductMaster.normalizeCode(product.getProductCode()), product));
        }

        int inserted = 0;
        int updated = 0;
        Set<String> ensuredParts = new HashSet<>();
        List<EprProductMaster> pending = new ArrayList<>();
        for (var entry : uniqueRows.entrySet()) {
            String code = entry.getKey();
            MasterExcelRow row = entry.getValue();
            EprProductMaster product = existing.get(code);
            if (product == null) {
                product = EprProductMaster.builder()
                        .productCode(code).productName(row.productName())
                        .eaPerBag(row.eaPerBag()).bagPerBox(row.bagPerBox()).eaPerBox(row.eaPerBox())
                        .bagPerPlt(row.bagPerPlt()).boxPerPlt(row.boxPerPlt())
                        .bagWeight(row.bagWeight()).boxWeight(row.boxWeight())
                        .companyType(row.companyType()).createdBy(userId)
                        .build();
                inserted++;
            } else {
                if (product.isDeleted()) product.restore(userId);
                product.update(row.productName(), row.eaPerBag(), row.bagPerBox(), row.eaPerBox(),
                        row.bagPerPlt(), row.boxPerPlt(), row.bagWeight(), row.boxWeight(), row.companyType(), userId);
                updated++;
            }
            if (ensuredParts.add(product.getPartPrefix())) ensurePart(product.getPartPrefix(), userId);
            pending.add(product);
            if (pending.size() == BATCH) {
                productRepository.saveAll(pending);
                pending.clear();
            }
        }
        if (!pending.isEmpty()) productRepository.saveAll(pending);
        productRepository.flush();
        return new UploadSummary("master", rows.size() + parseIssues.size(), inserted, updated,
                issues.size(), List.copyOf(issues));
    }

    /** 포장중량 엑셀: 등록된 제품의 BAG/BOX 중량만 바꾼다 (입수량·구분은 그대로) */
    @Transactional
    public UploadSummary updateWeights(List<WeightExcelRow> rows, List<UploadIssue> parseIssues, Long userId) {
        Map<String, WeightExcelRow> uniqueRows = new LinkedHashMap<>();
        for (WeightExcelRow row : rows) uniqueRows.put(EprProductMaster.normalizeCode(row.productCode()), row);

        Map<String, EprProductMaster> byCode = new HashMap<>();
        List<String> codes = List.copyOf(uniqueRows.keySet());
        for (int start = 0; start < codes.size(); start += BATCH) {
            productRepository.findAllByProductCodeIn(codes.subList(start, Math.min(start + BATCH, codes.size())))
                    .forEach(product -> byCode.put(EprProductMaster.normalizeCode(product.getProductCode()), product));
        }
        List<UploadIssue> issues = new ArrayList<>(parseIssues);
        int updated = 0;
        for (var entry : uniqueRows.entrySet()) {
            WeightExcelRow row = entry.getValue();
            EprProductMaster product = byCode.get(entry.getKey());
            if (product == null) {
                issues.add(new UploadIssue(row.rowNumber(), entry.getKey(), "미등록 제품코드입니다. 마스터를 먼저 등록하세요."));
                continue;
            }
            if (row.bagWeight() == null || row.boxWeight() == null
                    || row.bagWeight().signum() < 0 || row.boxWeight().signum() < 0) {
                issues.add(new UploadIssue(row.rowNumber(), entry.getKey(), "BAG/BOX 중량은 0 이상의 숫자여야 합니다."));
                continue;
            }
            product.updateWeights(row.bagWeight(), row.boxWeight(), userId);
            updated++;
        }
        productRepository.flush();
        return new UploadSummary("weight", rows.size() + parseIssues.size(), 0, updated,
                issues.size(), List.copyOf(issues));
    }

    // ───────────────────────── 파트 ─────────────────────────

    /** 같은 파트코드가 있으면 파트명을 바꾸고, 삭제된 코드면 되살린다. */
    @Transactional
    public PartCategoryResponse savePart(PartCategorySaveRequest request, Long userId) {
        String prefix = EprPartCategory.normalizePrefix(request.getPartPrefix());
        if (!prefix.matches("[A-Z0-9]{2,10}")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파트코드는 영문/숫자 2~10자여야 합니다.");
        }
        String partName = request.getPartName();
        if (partName == null || partName.isBlank() || partName.trim().length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파트명을 1~255자로 입력하세요.");
        }
        EprPartCategory part = partRepository.findAnyByPartPrefix(prefix).orElse(null);
        if (part == null) {
            part = EprPartCategory.builder().partPrefix(prefix).partName(partName).createdBy(userId).build();
        } else if (part.isDeleted()) {
            part.restore(partName, userId);
        } else {
            part.update(partName, userId);
        }
        EprPartCategory saved = partRepository.save(part);
        return new PartCategoryResponse(saved.getPartPrefix(), saved.getPartName(),
                productRepository.countByPartPrefix(prefix));
    }

    /** 파트를 쓰는 제품이 있으면 지우지 않는다. 지울 때는 소프트 삭제한다. */
    @Transactional
    public void deletePart(String prefixInput, Long userId) {
        String prefix = EprPartCategory.normalizePrefix(prefixInput);
        EprPartCategory part = partRepository.findByPartPrefix(prefix)
                .orElseThrow(() -> new EntityNotFoundException("파트코드 " + prefix + " 를 찾을 수 없습니다."));
        if (productRepository.countByPartPrefix(prefix) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해당 파트를 사용하는 제품이 있어 삭제할 수 없습니다.");
        }
        part.delete(userId);
    }

    /** 파트가 없으면 "미분류(코드)" 로 만들고, 삭제된 파트면 원래 이름으로 되살린다. */
    private EprPartCategory ensurePart(String prefix, Long userId) {
        String normalized = EprPartCategory.normalizePrefix(prefix);
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "제품코드에서 파트코드를 추출할 수 없습니다.");
        }
        EprPartCategory part = partRepository.findAnyByPartPrefix(normalized).orElse(null);
        if (part == null) {
            return partRepository.save(EprPartCategory.builder()
                    .partPrefix(normalized).partName("미분류(" + normalized + ")").createdBy(userId).build());
        }
        if (part.isDeleted()) {
            part.restore(part.getPartName(), userId);
            return partRepository.save(part);
        }
        return part;
    }
}
