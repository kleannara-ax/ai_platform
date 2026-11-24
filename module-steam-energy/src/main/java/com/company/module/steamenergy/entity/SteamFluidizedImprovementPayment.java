package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 유동상 설비개선 월별 비용 — 연도별 · 기록 키 (구분 접두어:번호 — 예: accident-record:1763600000000, improvement-item:xlsx-r12)별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 유동상 운영내역 › 설비개선 › 월별개선 (unit-fluidized-improvement.html)
 * <p>Table: steam_fluidized_improvement_payment
 * <p>PK: IMPROVEMENT_PAYMENT_ID, UNIQUE: (YEAR_NO, RECORD_KEY)
 */
@Entity
@Table(name = "steam_fluidized_improvement_payment")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedImprovementPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IMPROVEMENT_PAYMENT_ID")
    private Long improvementPaymentId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "RECORD_KEY", length = 80, nullable = false)
    private String recordKey;

    @Column(name = "PAY_MONTH", length = 10)
    private String payMonth;

    @Column(name = "PAYMENT_AMOUNT", precision = 18, scale = 6)
    private BigDecimal paymentAmount;

    @Column(name = "SPENT_AMOUNT", precision = 18, scale = 6)
    private BigDecimal spentAmount;

    @Column(name = "VENDOR_NAME", length = 200)
    private String vendorName;

    @Column(name = "USAGE_DESC", length = 500)
    private String usageDesc;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "DELETED_YN", nullable = false, length = 1)
    private String deletedYn;

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    @Column(name = "DELETED_BY")
    private Long deletedBy;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(KST);
        if (this.deletedYn == null) this.deletedYn = "N";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(KST);
    }

    /** 행 전체 값 갱신. null 은 빈 값으로 저장한다. */
    public void update(String payMonth, BigDecimal paymentAmount, BigDecimal spentAmount, String vendorName, String usageDesc, Integer sortOrder, Long updatedBy) {
        this.payMonth = payMonth;
        this.paymentAmount = paymentAmount;
        this.spentAmount = spentAmount;
        this.vendorName = vendorName;
        this.usageDesc = usageDesc;
        this.sortOrder = sortOrder;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, updatedBy);
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = LocalDateTime.now(KST);
    }

    public void restore(Long updatedBy) {
        this.deletedYn = "N";
        this.deletedBy = null;
        this.deletedAt = null;
        this.updatedBy = updatedBy;
    }
}
