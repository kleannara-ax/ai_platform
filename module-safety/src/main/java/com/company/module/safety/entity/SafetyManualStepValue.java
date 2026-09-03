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
 * 상세 표의 행(단계) x 열 교차 값.
 *
 * <p>열이 {@code TEXT} 면 {@link #textValue}, {@code CHECK} 면 {@link #checkedYn} 을 쓴다.
 * {@code PHOTO} 열은 사진이 {@link SafetyManualStepPhoto} 에 따로 있으므로 이 표에 값을 만들지 않는다.
 */
@Entity
@Table(name = "safety_manual_step_value")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyManualStepValue extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VALUE_ID")
    private Long valueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STEP_ID", nullable = false)
    private SafetyManualStep step;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COLUMN_ID", nullable = false)
    private SafetyManualColumn column;

    @Column(name = "TEXT_VALUE", columnDefinition = "TEXT")
    private String textValue;

    @Column(name = "CHECKED_YN", nullable = false, length = 1)
    private String checkedYn;

    @Builder
    private SafetyManualStepValue(SafetyManualStep step, SafetyManualColumn column,
                                   String textValue, boolean checked, String createdBy) {
        this.step = step;
        this.column = column;
        this.textValue = textValue;
        this.checkedYn = checked ? "Y" : "N";
        markCreatedBy(createdBy);
    }

    // ----------------------------------------------------------------
    // 비즈니스 메서드
    // ----------------------------------------------------------------

    public boolean isChecked() {
        return "Y".equals(this.checkedYn);
    }

    public void updateText(String textValue, String updatedBy) {
        this.textValue = textValue;
        markUpdatedBy(updatedBy);
    }

    public void updateChecked(boolean checked, String updatedBy) {
        this.checkedYn = checked ? "Y" : "N";
        markUpdatedBy(updatedBy);
    }

    /** 소프트 삭제. 물리 DELETE 대신 DELETED_YN='Y' 로만 처리한다. */
    public void delete(String deletedBy) {
        markDeleted(deletedBy);
    }
}
