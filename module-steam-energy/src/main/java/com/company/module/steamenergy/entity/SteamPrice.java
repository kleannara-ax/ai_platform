package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 시설별 계약·구매 단가 엔티티 (연/월별)
 *
 * <p>스팀 대시보드의 "시설별 단가 등록(관리자)"을 반영.
 * <p>시설(FACILITY): LNG / 복합보일러 / 폐합성소각로 / 유동상소각로 / 신설소각로 등
 * <p>입력방식(INPUT_TYPE): DIRECT(직접입력) / AUTO(자동계산)
 *
 * <p>Table: steam_price
 * <p>PK: PRICE_ID (BIGINT, AUTO_INCREMENT)
 * <p>UNIQUE: (YEAR_NO, MONTH_NO, ITEM_CODE)
 */
@Entity
@Table(name = "steam_price")
// 삭제된 행은 모든 조회에서 자동 제외 (소프트 삭제)
@SQLRestriction("DELETED_YN = \'N\'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRICE_ID")
    private Long priceId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "FACILITY", nullable = false, length = 50)
    private String facility;

    @Column(name = "ITEM_CODE", nullable = false, length = 50)
    private String itemCode;

    @Column(name = "ITEM_NAME", nullable = false, length = 200)
    private String itemName;

    @Column(name = "INPUT_TYPE", nullable = false, length = 20)
    private String inputType;

    @Column(name = "UNIT", length = 20)
    private String unit;

    @Column(name = "PRICE_VALUE", precision = 18, scale = 6)
    private BigDecimal priceValue;

    @Column(name = "REMARK", length = 500)
    private String remark;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;


    // ── 규격 공통 컬럼 (감사/소프트 삭제) ─────────────────────────
    // CREATED_BY/UPDATED_BY/DELETED_BY 는 core 의 CurrentUserProvider 가 제공되면 Service 에서 채운다.
    // (현재 core 에 해당 컴포넌트가 없어 null 로 둔다 — sql/module-steam-energy/README.md 참고)
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
        if (this.inputType == null) this.inputType = "DIRECT";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(KST);
    }

    /**
     * 단가 값·부가 정보 수정 (재등록 시 호출)
     */
    public void update(String facility, String itemName, String inputType,
                       String unit, BigDecimal priceValue, String remark) {
        if (facility != null) this.facility = facility;
        if (itemName != null) this.itemName = itemName;
        if (inputType != null) this.inputType = inputType;
        this.unit = unit;
        this.priceValue = priceValue;
        this.remark = remark;
    }

    /** 단가 값만 갱신 (일괄/월별 입력용) */
    public void updatePriceValue(BigDecimal priceValue) {
        this.priceValue = priceValue;
    }

    /** 소프트 삭제 — 물리 삭제하지 않고 DELETED_YN 을 'Y' 로 바꾼다. */
    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = LocalDateTime.now(KST);
    }

    /** 삭제 취소 */
    public void restore(Long updatedBy) {
        this.deletedYn = "N";
        this.deletedBy = null;
        this.deletedAt = null;
        this.updatedBy = updatedBy;
    }
}
