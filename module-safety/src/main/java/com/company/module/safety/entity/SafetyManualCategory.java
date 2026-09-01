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
 * 안전작업방식 매뉴얼 분류 (계층형: 부서 &gt; 라인/호기 등).
 * <p>예: "화장지생산팀"(최상위) &gt; "초지"(하위) 처럼 자기참조로 트리를 구성한다.
 * 최상위 분류는 {@link #parent} 가 null 이다.
 */
@Entity
@Table(name = "safety_manual_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyManualCategory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CATEGORY_ID")
    private Long categoryId;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    /** 상위 분류 (최상위면 null) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_ID")
    private SafetyManualCategory parent;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    @Builder
    private SafetyManualCategory(String name, SafetyManualCategory parent, int sortOrder, String createdBy) {
        this.name = name;
        this.parent = parent;
        this.sortOrder = sortOrder;
        markCreatedBy(createdBy);
    }

    // ----------------------------------------------------------------
    // 비즈니스 메서드
    // ----------------------------------------------------------------

    public void update(String name, int sortOrder, String updatedBy) {
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
