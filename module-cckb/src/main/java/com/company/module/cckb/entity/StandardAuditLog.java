package com.company.module.cckb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 기준값({@link InspectionStandard}) 변경 이력 감사 로그.
 * "⚙ 기준값 설정" 탭에서 기준값을 수정할 때마다 변경된 필드별로 1건씩 기록한다.
 */
@Entity
@Table(name = "standard_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StandardAuditLog extends CckbBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUDIT_ID")
    private Long auditId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STANDARD_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_AUDITLOG_STANDARD"))
    private InspectionStandard standard;

    @Column(name = "CHANGED_FIELD", nullable = false, length = 50)
    private String changedField;

    @Column(name = "OLD_VALUE", length = 50)
    private String oldValue;

    @Column(name = "NEW_VALUE", nullable = false, length = 50)
    private String newValue;

    /** 변경을 수행한 사용자 ID (필수) */
    @Column(name = "CHANGED_BY", nullable = false)
    private Long changedBy;

    @Column(name = "CHANGE_REASON", length = 200)
    private String changeReason;

    @Column(name = "CHANGED_AT", nullable = false)
    private LocalDateTime changedAt;

    @Builder
    private StandardAuditLog(InspectionStandard standard, String changedField, String oldValue,
                              String newValue, Long changedBy, String changeReason) {
        this.standard = standard;
        this.changedField = changedField;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedBy = changedBy;
        this.changeReason = changeReason;
        this.changedAt = LocalDateTime.now();
        markCreatedBy(changedBy);
    }
}
