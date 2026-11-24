package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 소각로 도급내역 — 일자별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 폐합성수지 소각로 › 도급내역 (incinerator-contract.html)
 * <p>Table: steam_incinerator_contract_daily
 * <p>PK: CONTRACT_DAILY_ID, UNIQUE: (USAGE_DATE)
 */
@Entity
@Table(name = "steam_incinerator_contract_daily")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamIncineratorContractDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONTRACT_DAILY_ID")
    private Long contractDailyId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    @Column(name = "U1_STEAM", precision = 18, scale = 6)
    private BigDecimal u1Steam;

    @Column(name = "U1_UNIT", precision = 18, scale = 6)
    private BigDecimal u1Unit;

    @Column(name = "U1_RUNTIME", precision = 18, scale = 6)
    private BigDecimal u1Runtime;

    @Column(name = "U1_FIXED", precision = 18, scale = 6)
    private BigDecimal u1Fixed;

    @Column(name = "U2_STEAM", precision = 18, scale = 6)
    private BigDecimal u2Steam;

    @Column(name = "U2_UNIT", precision = 18, scale = 6)
    private BigDecimal u2Unit;

    @Column(name = "U2_RUNTIME", precision = 18, scale = 6)
    private BigDecimal u2Runtime;

    @Column(name = "U2_FIXED", precision = 18, scale = 6)
    private BigDecimal u2Fixed;

    @Column(name = "REAL_STEAM1", precision = 18, scale = 6)
    private BigDecimal realSteam1;

    @Column(name = "REAL_UNIT1", precision = 18, scale = 6)
    private BigDecimal realUnit1;

    @Column(name = "REAL_STEAM2", precision = 18, scale = 6)
    private BigDecimal realSteam2;

    @Column(name = "REAL_UNIT2", precision = 18, scale = 6)
    private BigDecimal realUnit2;

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
    public void update(BigDecimal u1Steam, BigDecimal u1Unit, BigDecimal u1Runtime, BigDecimal u1Fixed, BigDecimal u2Steam, BigDecimal u2Unit, BigDecimal u2Runtime, BigDecimal u2Fixed, BigDecimal realSteam1, BigDecimal realUnit1, BigDecimal realSteam2, BigDecimal realUnit2, Long updatedBy) {
        this.u1Steam = u1Steam;
        this.u1Unit = u1Unit;
        this.u1Runtime = u1Runtime;
        this.u1Fixed = u1Fixed;
        this.u2Steam = u2Steam;
        this.u2Unit = u2Unit;
        this.u2Runtime = u2Runtime;
        this.u2Fixed = u2Fixed;
        this.realSteam1 = realSteam1;
        this.realUnit1 = realUnit1;
        this.realSteam2 = realSteam2;
        this.realUnit2 = realUnit2;
        this.updatedBy = updatedBy;
    }

    /** 값만 비운다 — 엑셀 가져오기에서 파일에 없는 행. */
    public void clearValues(Long updatedBy) {
        update(null, null, null, null, null, null, null, null, null, null, null, null, updatedBy);
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
