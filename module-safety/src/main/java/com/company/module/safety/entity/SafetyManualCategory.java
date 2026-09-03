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
 * 안전작업 매뉴얼 분류 — 정확히 2단계로 고정된 계층 구조.
 * <ul>
 *   <li>1단계(대분류) — 예: 화장지생산팀 / 공무팀</li>
 *   <li>2단계(중분류) — 예: 초지 5호기 / 기계정비반 (매뉴얼은 항상 이 단계에만 속한다)</li>
 * </ul>
 * 예전에는 3단계(소분류)까지 있었으나 쓰지 않기로 해서 중분류까지만 둔다.
 * 각 단계의 이름은 사용자가 화면에서 자유롭게 추가/수정할 수 있으며, 스키마에는 값으로 고정하지 않는다.
 * {@link #parent} 로 자기참조 트리를 구성하고, {@link #levelNo} 로 정확한 단계를 명시적으로 검증한다.
 */
@Entity
@Table(name = "safety_manual_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyManualCategory extends BaseTimeEntity {

    public static final int LEVEL_MAJOR = 1;   // 대분류
    public static final int LEVEL_MIDDLE = 2;  // 중분류 (매뉴얼이 속하는 최하위 단계)
    /** 매뉴얼이 붙을 수 있는 단계 */
    public static final int LEVEL_LEAF = LEVEL_MIDDLE;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CATEGORY_ID")
    private Long categoryId;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    /** 상위 분류 (대분류면 null) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_ID")
    private SafetyManualCategory parent;

    /** 분류 단계: 1=대분류, 2=중분류 */
    @Column(name = "LEVEL_NO", nullable = false)
    private int levelNo;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    @Builder
    private SafetyManualCategory(String name, SafetyManualCategory parent, int sortOrder, String createdBy) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "분류명은 필수입니다.");
        }
        int level = LEVEL_MAJOR;
        if (parent != null) {
            level = parent.getLevelNo() + 1;
            if (level > LEVEL_LEAF) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "분류는 대분류/중분류 2단계까지만 만들 수 있습니다.");
            }
        }
        this.name = name;
        this.parent = parent;
        this.levelNo = level;
        this.sortOrder = sortOrder;
        markCreatedBy(createdBy);
    }

    // ----------------------------------------------------------------
    // 비즈니스 메서드
    // ----------------------------------------------------------------

    /** 매뉴얼을 붙일 수 있는 최하위 단계(중분류)인지 */
    public boolean isLeaf() {
        return levelNo == LEVEL_LEAF;
    }

    public void update(String name, int sortOrder, String updatedBy) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "분류명은 필수입니다.");
        }
        this.name = name;
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
}
