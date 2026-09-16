package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 일일 스팀 사용 실적 엔티티 (설비별 일자)
 *
 * <p>스팀 대시보드의 "오늘 일지 등록 / 스팀 사용량"을 반영.
 * <p>Table: steam_daily_usage
 * <p>PK: USAGE_ID (BIGINT, AUTO_INCREMENT)
 * <p>UNIQUE: (USAGE_DATE, EQUIPMENT_ID)
 */
@Entity
@Table(name = "steam_daily_usage")
// 삭제된 행은 모든 조회에서 자동 제외 (소프트 삭제)
@SQLRestriction("DELETED_YN = \'N\'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamDailyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USAGE_ID")
    private Long usageId;

    @Column(name = "USAGE_DATE", nullable = false)
    private LocalDate usageDate;

    /** 설비 참조 (동일 모듈 내부 FK, steam_equipment.EQUIPMENT_ID) */
    @Column(name = "EQUIPMENT_ID", nullable = false)
    private Long equipmentId;

    @Column(name = "STEAM_AMOUNT", precision = 18, scale = 4)
    private BigDecimal steamAmount;

    @Column(name = "RUNTIME_HOURS", precision = 12, scale = 4)
    private BigDecimal runtimeHours;

    @Column(name = "PRODUCTION_KG", precision = 18, scale = 4)
    private BigDecimal productionKg;

    @Column(name = "UNIT_RATE", precision = 18, scale = 6)
    private BigDecimal unitRate;

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
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(KST);
    }

    /**
     * 사용 실적 수정 (재등록 시 호출)
     */
    public void update(BigDecimal steamAmount, BigDecimal runtimeHours,
                       BigDecimal productionKg, BigDecimal unitRate, String remark) {
        this.steamAmount = steamAmount;
        this.runtimeHours = runtimeHours;
        this.productionKg = productionKg;
        this.unitRate = unitRate;
        this.remark = remark;
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
