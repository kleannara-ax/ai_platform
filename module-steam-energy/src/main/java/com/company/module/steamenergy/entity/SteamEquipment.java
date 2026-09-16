package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 스팀 설비 마스터 엔티티 (PM/TM/유동상/복합/외부/소각로 등)
 *
 * <p>Table: steam_equipment (소문자 snake_case)
 * <p>Column: UPPER_SNAKE_CASE
 * <p>PK: EQUIPMENT_ID (BIGINT, AUTO_INCREMENT)
 */
@Entity
@Table(name = "steam_equipment")
// 삭제된 행은 모든 조회에서 자동 제외 (소프트 삭제)
@SQLRestriction("DELETED_YN = \'N\'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EQUIPMENT_ID")
    private Long equipmentId;

    @Column(name = "EQUIPMENT_CODE", nullable = false, length = 50)
    private String equipmentCode;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "CATEGORY", length = 50)
    private String category;

    @Column(name = "UNIT", length = 20)
    private String unit;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive;

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
        if (this.sortOrder == null) this.sortOrder = 0;
        if (this.isActive == null) this.isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(KST);
    }

    /**
     * 설비 정보 수정 (setter 사용 금지 - 비즈니스 메서드로 갱신)
     */
    public void update(String name, String category, String unit, Integer sortOrder, Boolean isActive) {
        if (name != null) this.name = name;
        this.category = category;
        this.unit = unit;
        if (sortOrder != null) this.sortOrder = sortOrder;
        if (isActive != null) this.isActive = isActive;
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
