package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 유동상 설비개선 항목 — 연도별 · 기록 키 (구분 접두어:번호 — 예: accident-record:1763600000000, improvement-item:xlsx-r12)별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 유동상 운영내역 › 설비개선 › 항목개선 (unit-fluidized-improvement.html)
 * <p>Table: steam_fluidized_improvement_item
 * <p>PK: IMPROVEMENT_ITEM_ID, UNIQUE: (YEAR_NO, RECORD_KEY)
 */
@Entity
@Table(name = "steam_fluidized_improvement_item")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamFluidizedImprovementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IMPROVEMENT_ITEM_ID")
    private Long improvementItemId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "RECORD_KEY", length = 80, nullable = false)
    private String recordKey;

    @Column(name = "CATEGORY", length = 50)
    private String category;

    @Column(name = "ITEM_NO", length = 50)
    private String itemNo;

    @Column(name = "ITEM_NAME", length = 500)
    private String itemName;

    @Column(name = "PLAN_COST", precision = 18, scale = 6)
    private BigDecimal planCost;

    @Column(name = "ACTUAL_COST", precision = 18, scale = 6)
    private BigDecimal actualCost;

    @Column(name = "STATUS", length = 50)
    private String status;

    @Column(name = "REMARK", length = 500)
    private String remark;

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
    public void update(String category, String itemNo, String itemName, BigDecimal planCost, BigDecimal actualCost, String status, String remark, Integer sortOrder, Long updatedBy) {
        this.category = category;
        this.itemNo = itemNo;
        this.itemName = itemName;
        this.planCost = planCost;
        this.actualCost = actualCost;
        this.status = status;
        this.remark = remark;
        this.sortOrder = sortOrder;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, updatedBy);
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
