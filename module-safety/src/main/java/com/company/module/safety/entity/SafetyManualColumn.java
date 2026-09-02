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
 * 매뉴얼 상세 표의 열 정의.
 *
 * <p>열 구성이 서식마다 다르고(안전작업 매뉴얼 vs 작업 위험성 평가서), 관리자가 화면에서
 * 열을 추가하거나 이름·순서를 바꿀 수 있어야 하므로 열을 코드가 아닌 데이터로 둔다.
 * 체크버튼도 {@code COLUMN_TYPE = CHECK} 인 열 하나로 표현한다.
 */
@Entity
@Table(name = "safety_manual_column")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyManualColumn extends BaseTimeEntity {

    /** 글자를 담는 열 */
    public static final String TYPE_TEXT = "TEXT";
    /** 체크버튼 열 */
    public static final String TYPE_CHECK = "CHECK";
    /** 단계 사진을 보여주는 열 (매뉴얼당 하나만 의미가 있다) */
    public static final String TYPE_PHOTO = "PHOTO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COLUMN_ID")
    private Long columnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MANUAL_ID", nullable = false)
    private SafetyManual manual;

    @Column(name = "LABEL", nullable = false, length = 100)
    private String label;

    @Column(name = "COLUMN_TYPE", nullable = false, length = 20)
    private String columnType;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    /** 화면에서 가용 폭을 나눌 때 쓰는 비중 (px 이 아니라 상대값) */
    @Column(name = "WIDTH_WEIGHT", nullable = false)
    private int widthWeight;

    @Builder
    private SafetyManualColumn(SafetyManual manual, String label, String columnType,
                                int sortOrder, int widthWeight, String createdBy) {
        this.manual = manual;
        this.label = requireLabel(label);
        this.columnType = normalizeType(columnType);
        this.sortOrder = sortOrder;
        this.widthWeight = widthWeight > 0 ? widthWeight : 100;
        markCreatedBy(createdBy);
    }

    // ----------------------------------------------------------------
    // 비즈니스 메서드
    // ----------------------------------------------------------------

    public boolean isCheck() {
        return TYPE_CHECK.equals(this.columnType);
    }

    public boolean isPhoto() {
        return TYPE_PHOTO.equals(this.columnType);
    }

    /** 이름/유형/순서/폭을 한 번에 고친다. 유형을 바꾸면 기존 값의 해석이 달라지므로 화면에서 확인을 받는다. */
    public void update(String label, String columnType, int sortOrder, int widthWeight, String updatedBy) {
        this.label = requireLabel(label);
        this.columnType = normalizeType(columnType);
        this.sortOrder = sortOrder;
        this.widthWeight = widthWeight > 0 ? widthWeight : this.widthWeight;
        markUpdatedBy(updatedBy);
    }

    /** 순서만 바꾼다 (열 순서 변경). */
    public void changeSortOrder(int sortOrder, String updatedBy) {
        this.sortOrder = sortOrder;
        markUpdatedBy(updatedBy);
    }

    /** 소프트 삭제. 물리 DELETE 대신 DELETED_YN='Y' 로만 처리한다. */
    public void delete(String deletedBy) {
        markDeleted(deletedBy);
    }

    public void restore() {
        markRestored();
    }

    private String requireLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "열 이름은 필수입니다.");
        }
        return label.trim();
    }

    private String normalizeType(String columnType) {
        if (columnType == null) {
            return TYPE_TEXT;
        }
        String upper = columnType.trim().toUpperCase();
        if (!TYPE_TEXT.equals(upper) && !TYPE_CHECK.equals(upper) && !TYPE_PHOTO.equals(upper)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "열 유형은 TEXT, CHECK, PHOTO 중 하나여야 합니다. 입력값=" + columnType);
        }
        return upper;
    }
}
