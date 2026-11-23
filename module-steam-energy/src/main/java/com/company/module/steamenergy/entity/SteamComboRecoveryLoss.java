package com.company.module.steamenergy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 복합보일러 스팀 회수 감소 — 연/월별 · 줄 번호 (1부터, 그 달의 감소 건 순번)별 한 행 (2026-11 규격 전환, 옛 steam_cell_value 논리 표 대체)
 *
 * <p>화면: 복합보일러 › 스팀 회수 감소 (combo-boiler-recovery-loss.html)
 * <p>Table: steam_combo_recovery_loss
 * <p>PK: COMBO_RECOVERY_ID, UNIQUE: (YEAR_NO, MONTH_NO, LINE_NO)
 */
@Entity
@Table(name = "steam_combo_recovery_loss")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SteamComboRecoveryLoss {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMBO_RECOVERY_ID")
    private Long comboRecoveryId;

    @Column(name = "YEAR_NO", nullable = false)
    private Integer yearNo;

    @Column(name = "MONTH_NO", nullable = false)
    private Integer monthNo;

    @Column(name = "LINE_NO", nullable = false)
    private Integer lineNo;

    @Column(name = "LOSS_DATE", length = 50)
    private String lossDate;

    @Column(name = "LOSS_TIME", length = 100)
    private String lossTime;

    @Column(name = "LOSS_NOTE", length = 500)
    private String lossNote;

    @Column(name = "LOSS_STEAM_RATE", precision = 18, scale = 6)
    private BigDecimal lossSteamRate;

    @Column(name = "LOSS_BASE_RATE", precision = 18, scale = 6)
    private BigDecimal lossBaseRate;

    @Column(name = "LOSS_MINUTES", precision = 18, scale = 6)
    private BigDecimal lossMinutes;

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
    public void update(String lossDate, String lossTime, String lossNote, BigDecimal lossSteamRate, BigDecimal lossBaseRate, BigDecimal lossMinutes, Long updatedBy) {
        this.lossDate = lossDate;
        this.lossTime = lossTime;
        this.lossNote = lossNote;
        this.lossSteamRate = lossSteamRate;
        this.lossBaseRate = lossBaseRate;
        this.lossMinutes = lossMinutes;
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
