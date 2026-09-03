package com.company.module.safety.entity;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
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
 * 매뉴얼 머리말 항목 (라벨-값).
 *
 * <p>작업 위험성 평가서의 부서명·작업인원·작업장소·목적·개인보호구·중요위험요소처럼
 * 서식마다 다른 머리말을 담는다. 서식이 늘어나도 스키마를 바꾸지 않도록 키-값으로 둔다.
 */
@Entity
@Table(name = "safety_manual_meta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyManualMeta extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "META_ID")
    private Long metaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MANUAL_ID", nullable = false)
    private SafetyManual manual;

    @Column(name = "LABEL", nullable = false, length = 100)
    private String label;

    @Column(name = "VALUE_TEXT", columnDefinition = "TEXT")
    private String valueText;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    @Builder
    private SafetyManualMeta(SafetyManual manual, String label, String valueText,
                              int sortOrder, String createdBy) {
        this.manual = manual;
        this.label = requireLabel(label);
        this.valueText = valueText;
        this.sortOrder = sortOrder;
        markCreatedBy(createdBy);
    }

    // ----------------------------------------------------------------
    // 비즈니스 메서드
    // ----------------------------------------------------------------

    public void update(String label, String valueText, int sortOrder, String updatedBy) {
        this.label = requireLabel(label);
        this.valueText = valueText;
        this.sortOrder = sortOrder;
        markUpdatedBy(updatedBy);
    }

    /** 소프트 삭제. 물리 DELETE 대신 DELETED_YN='Y' 로만 처리한다. */
    public void delete(String deletedBy) {
        markDeleted(deletedBy);
    }

    private String requireLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "머리말 항목명은 필수입니다.");
        }
        return label.trim();
    }
}
