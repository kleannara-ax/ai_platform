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
 * 안전작업방식 매뉴얼 (원본 엑셀의 시트 1개 = 매뉴얼 1개).
 * <p>매뉴얼은 분류({@link SafetyManualCategory}) 아래에 속하고,
 * 순서(단계) 목록은 {@link SafetyManualStep} 으로 별도 관리한다.
 */
@Entity
@Table(name = "safety_manual")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyManual extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MANUAL_ID")
    private Long manualId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CATEGORY_ID", nullable = false)
    private SafetyManualCategory category;

    /** 매뉴얼 제목 (원본 엑셀의 "공정명") */
    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    /** 엑셀 일괄업로드로 생성된 경우 원본 파일명 (직접 등록 시 null) */
    @Column(name = "SOURCE_FILE_NAME", length = 255)
    private String sourceFileName;

    /** 엑셀 일괄업로드로 생성된 경우 원본 시트명 (직접 등록 시 null) */
    @Column(name = "SOURCE_SHEET_NAME", length = 100)
    private String sourceSheetName;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    @Builder
    private SafetyManual(SafetyManualCategory category, String title, String sourceFileName,
                          String sourceSheetName, int sortOrder, String createdBy) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "매뉴얼 제목은 필수입니다.");
        }
        this.category = category;
        this.title = title;
        this.sourceFileName = sourceFileName;
        this.sourceSheetName = sourceSheetName;
        this.sortOrder = sortOrder;
        markCreatedBy(createdBy);
    }

    // ----------------------------------------------------------------
    // 비즈니스 메서드
    // ----------------------------------------------------------------

    public void update(SafetyManualCategory category, String title, int sortOrder, String updatedBy) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "매뉴얼 제목은 필수입니다.");
        }
        this.category = category;
        this.title = title;
        this.sortOrder = sortOrder;
        markUpdatedBy(updatedBy);
    }

    public void changeCategory(SafetyManualCategory category, String updatedBy) {
        this.category = category;
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
