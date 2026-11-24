package com.company.module.epr.entity;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * 제품 마스터 — 제품코드별 입수량과 BAG/BOX 포장중량(g), 자사/타사 구분.
 * PART_PREFIX 는 제품코드 앞 3자리에서 자동으로 정한다.
 */
@Entity
@Table(name = "epr_product_master")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EprProductMaster extends EprBaseEntity {

    public static final String OWN_COMPANY = "자사";
    public static final String OTHER_COMPANY = "타사";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRODUCT_ID")
    private Long productId;

    @Column(name = "PRODUCT_CODE", nullable = false, length = 30)
    private String productCode;

    @Column(name = "PART_PREFIX", nullable = false, length = 10)
    private String partPrefix;

    @Column(name = "PRODUCT_NAME", nullable = false, length = 255)
    private String productName;

    @Column(name = "EA_PER_BAG", nullable = false)
    private Integer eaPerBag;

    @Column(name = "BAG_PER_BOX", nullable = false)
    private Integer bagPerBox;

    @Column(name = "EA_PER_BOX", nullable = false)
    private Integer eaPerBox;

    @Column(name = "BAG_PER_PLT", nullable = false)
    private Integer bagPerPlt;

    @Column(name = "BOX_PER_PLT", nullable = false)
    private Integer boxPerPlt;

    @Column(name = "BAG_WEIGHT", nullable = false, precision = 15, scale = 4)
    private BigDecimal bagWeight;

    @Column(name = "BOX_WEIGHT", nullable = false, precision = 15, scale = 4)
    private BigDecimal boxWeight;

    @Column(name = "COMPANY_TYPE", nullable = false, length = 10)
    private String companyType;

    @Builder
    private EprProductMaster(String productCode, String productName, Integer eaPerBag, Integer bagPerBox,
                             Integer eaPerBox, Integer bagPerPlt, Integer boxPerPlt,
                             BigDecimal bagWeight, BigDecimal boxWeight, String companyType, Long createdBy) {
        this.productCode = normalizeCode(productCode);
        this.partPrefix = prefixOf(this.productCode);
        applyDetails(productName, eaPerBag, bagPerBox, eaPerBox, bagPerPlt, boxPerPlt, bagWeight, boxWeight, companyType);
        markCreatedBy(createdBy);
    }

    /** 단건 화면·마스터 엑셀: 제품명·입수량·포장중량·구분 전체를 바꾼다. */
    public void update(String productName, Integer eaPerBag, Integer bagPerBox, Integer eaPerBox,
                       Integer bagPerPlt, Integer boxPerPlt, BigDecimal bagWeight, BigDecimal boxWeight,
                       String companyType, Long updatedBy) {
        applyDetails(productName, eaPerBag, bagPerBox, eaPerBox, bagPerPlt, boxPerPlt, bagWeight, boxWeight, companyType);
        markUpdatedBy(updatedBy);
    }

    /** 포장중량 엑셀: BAG/BOX 중량만 바꾸고 입수량·구분은 그대로 둔다. */
    public void updateWeights(BigDecimal bagWeight, BigDecimal boxWeight, Long updatedBy) {
        this.bagWeight = zeroIfNull(bagWeight);
        this.boxWeight = zeroIfNull(boxWeight);
        markUpdatedBy(updatedBy);
    }

    public void delete(Long deletedBy) {
        markDeleted(deletedBy);
    }

    public void restore(Long updatedBy) {
        markRestored(updatedBy);
    }

    private void applyDetails(String productName, Integer eaPerBag, Integer bagPerBox, Integer eaPerBox,
                              Integer bagPerPlt, Integer boxPerPlt, BigDecimal bagWeight, BigDecimal boxWeight,
                              String companyType) {
        this.productName = productName == null ? "" : productName.trim();
        this.eaPerBag = zeroIfNull(eaPerBag);
        this.bagPerBox = zeroIfNull(bagPerBox);
        this.eaPerBox = zeroIfNull(eaPerBox);
        this.bagPerPlt = zeroIfNull(bagPerPlt);
        this.boxPerPlt = zeroIfNull(boxPerPlt);
        this.bagWeight = zeroIfNull(bagWeight);
        this.boxWeight = zeroIfNull(boxWeight);
        this.companyType = normalizeCompanyType(companyType);
    }

    public static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String prefixOf(String normalizedCode) {
        return normalizedCode.length() >= 3 ? normalizedCode.substring(0, 3) : normalizedCode;
    }

    /** 빈 값은 자사. 자사/타사 외의 값은 거부한다. */
    public static String normalizeCompanyType(String value) {
        if (value == null || value.isBlank()) return OWN_COMPANY;
        String normalized = value.trim();
        if (!OWN_COMPANY.equals(normalized) && !OTHER_COMPANY.equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "구분은 자사 또는 타사만 입력할 수 있습니다.");
        }
        return normalized;
    }

    private static Integer zeroIfNull(Integer value) { return value == null ? 0 : value; }
    private static BigDecimal zeroIfNull(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
