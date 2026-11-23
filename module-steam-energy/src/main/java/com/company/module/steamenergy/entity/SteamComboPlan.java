package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 복합보일러 연간 계획 — 연도별 · 대상 월 (YYYY-MM, 기준연도 화면의 월 칸)별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 복합보일러 › 기타(계획·LNG사용량) (combo-boiler-etc.html)
 * <p>Table: steam_combo_plan
 * <p>PK: COMBO_PLAN_ID, UNIQUE: (YEAR_NO, TARGET_YM)
 */
@Entity
@Table(name = "steam_combo_plan")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamComboPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMBO_PLAN_ID")
    private Long comboPlanId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "TARGET_YM", length = 7, nullable = false)
    private String targetYm;

    @Column(name = "COMBO_BASIC", precision = 18, scale = 6)
    private BigDecimal comboBasic;

    @Column(name = "COMBO_SPECIAL", precision = 18, scale = 6)
    private BigDecimal comboSpecial;

    @Column(name = "COMBO_ACTUAL", precision = 18, scale = 6)
    private BigDecimal comboActual;

    @Column(name = "JESCO_PLAN", precision = 18, scale = 6)
    private BigDecimal jescoPlan;

    @Column(name = "JESCO_ACTUAL", precision = 18, scale = 6)
    private BigDecimal jescoActual;

    @Column(name = "LNG_BOILER", precision = 18, scale = 6)
    private BigDecimal lngBoiler;

    @Column(name = "LNG_COMBO", precision = 18, scale = 6)
    private BigDecimal lngCombo;

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
    public void update(BigDecimal comboBasic, BigDecimal comboSpecial, BigDecimal comboActual, BigDecimal jescoPlan, BigDecimal jescoActual, BigDecimal lngBoiler, BigDecimal lngCombo, Long updatedBy) {
        this.comboBasic = comboBasic;
        this.comboSpecial = comboSpecial;
        this.comboActual = comboActual;
        this.jescoPlan = jescoPlan;
        this.jescoActual = jescoActual;
        this.lngBoiler = lngBoiler;
        this.lngCombo = lngCombo;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, updatedBy);
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
