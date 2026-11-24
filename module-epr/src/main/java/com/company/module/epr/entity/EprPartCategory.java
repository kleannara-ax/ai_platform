package com.company.module.epr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.Locale;

/**
 * 파트 코드 — 제품코드 앞 3자리(PART_PREFIX)와 신고 파트명을 잇는다.
 * 소프트 삭제된 행은 기본 조회에서 빠진다. 같은 코드를 다시 등록하면 그 행을 되살린다.
 */
@Entity
@Table(name = "epr_part_category")
@SQLRestriction("DELETED_YN = 'N'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EprPartCategory extends EprBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PART_CATEGORY_ID")
    private Long partCategoryId;

    @Column(name = "PART_PREFIX", nullable = false, length = 10)
    private String partPrefix;

    @Column(name = "PART_NAME", nullable = false, length = 255)
    private String partName;

    @Builder
    private EprPartCategory(String partPrefix, String partName, Long createdBy) {
        this.partPrefix = normalizePrefix(partPrefix);
        this.partName = clean(partName);
        markCreatedBy(createdBy);
    }

    public void update(String partName, Long updatedBy) {
        this.partName = clean(partName);
        markUpdatedBy(updatedBy);
    }

    public void delete(Long deletedBy) {
        markDeleted(deletedBy);
    }

    public void restore(String partName, Long updatedBy) {
        this.partName = clean(partName);
        markRestored(updatedBy);
    }

    public static String normalizePrefix(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
