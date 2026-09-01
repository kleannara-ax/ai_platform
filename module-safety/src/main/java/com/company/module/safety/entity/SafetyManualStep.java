package com.company.module.safety.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

/**
 * 매뉴얼의 순서(단계). 원본 엑셀의 행(No.) 1개 = 단계 1개에 대응한다.
 * <p>컬럼 매핑: description=공정 순서(설명), hazard=위험요인,
 * safetyEquipment=안전 보호구, remark=비고(개선사항).
 */
@Entity
@Table(name = "safety_manual_step")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyManualStep extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STEP_ID")
    private Long stepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MANUAL_ID", nullable = false)
    private SafetyManual manual;

    /** 원본 엑셀의 No. (화면 표시용 순번, 1부터) */
    @Column(name = "STEP_NO", nullable = false)
    private int stepNo;

    /** 공정 순서 (설명) */
    @Column(name = "DESCRIPTION", columnDefinition = "TEXT")
    private String description;

    /** 위험요인 */
    @Column(name = "HAZARD", columnDefinition = "TEXT")
    private String hazard;

    /** 안전 보호구 */
    @Column(name = "SAFETY_EQUIPMENT", columnDefinition = "TEXT")
    private String safetyEquipment;

    /** 비고 (개선사항) */
    @Column(name = "REMARK", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    @Builder
    private SafetyManualStep(SafetyManual manual, int stepNo, String description, String hazard,
                              String safetyEquipment, String remark, int sortOrder, String createdBy) {
        this.manual = manual;
        this.stepNo = stepNo;
        this.description = description;
        this.hazard = hazard;
        this.safetyEquipment = safetyEquipment;
        this.remark = remark;
        this.sortOrder = sortOrder;
        markCreatedBy(createdBy);
    }

    // ----------------------------------------------------------------
    // 비즈니스 메서드
    // ----------------------------------------------------------------

    public void update(int stepNo, String description, String hazard, String safetyEquipment,
                        String remark, int sortOrder, String updatedBy) {
        this.stepNo = stepNo;
        this.description = description;
        this.hazard = hazard;
        this.safetyEquipment = safetyEquipment;
        this.remark = remark;
        this.sortOrder = sortOrder;
        markUpdatedBy(updatedBy);
    }

    /** 소프트 삭제. 물리 DELETE 대신 DELETED_YN='Y' 로만 처리한다. */
    public void delete(String deletedBy) {
        markDeleted(deletedBy);
    }
}
