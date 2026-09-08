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
 * 매뉴얼 단계별 사진 (1:N — 한 단계에 여러 장 가능, 원본 엑셀 확인 결과 실제로 존재).
 * <p>실제 파일은 디스크(업로드 디렉토리)에 저장하고, 이 엔티티에는 메타데이터만 보관한다.
 * (module-kims 의 RequestAttachment 와 동일한 패턴)
 */
@Entity
@Table(name = "safety_manual_step_photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyManualStepPhoto extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PHOTO_ID")
    private Long photoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STEP_ID", nullable = false)
    private SafetyManualStep step;

    /**
     * 이 사진이 들어갈 표의 열. {@code null} 이면 매뉴얼의 기본 '사진' 열에 표시한다.
     *
     * <p>비고 칸처럼 글과 사진이 함께 들어가는 칸이 있어서, 행뿐 아니라 열까지 기억해야 한다.
     * 외래키는 걸지 않는다 — 열이 지워져도 사진은 남고, 그때는 기본 사진 열로 돌아간다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COLUMN_ID")
    private SafetyManualColumn column;

    /** 사용자가 올린(또는 엑셀에서 추출된) 원본 파일명 */
    @Column(name = "ORIGINAL_NAME", nullable = false, length = 255)
    private String originalName;

    /** 디스크에 저장된 파일명 (UUID 기반, 충돌 방지) */
    @Column(name = "STORED_NAME", nullable = false, length = 255)
    private String storedName;

    @Column(name = "CONTENT_TYPE", length = 150)
    private String contentType;

    /** 파일 크기 (byte) */
    @Column(name = "FILE_SIZE", nullable = false)
    private long fileSize;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    @Builder
    private SafetyManualStepPhoto(SafetyManualStep step, SafetyManualColumn column,
                                   String originalName, String storedName,
                                   String contentType, long fileSize, int sortOrder, String createdBy) {
        this.step = step;
        this.column = column;
        this.originalName = originalName;
        this.storedName = storedName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sortOrder = sortOrder;
        markCreatedBy(createdBy);
    }

    /** 사진이 표시될 열을 옮긴다. null 이면 기본 사진 열로 되돌린다. */
    public void moveToColumn(SafetyManualColumn column, String updatedBy) {
        this.column = column;
        markUpdatedBy(updatedBy);
    }

    /** 소프트 삭제. 물리 DELETE 대신 DELETED_YN='Y' 로만 처리한다. */
    public void delete(String deletedBy) {
        markDeleted(deletedBy);
    }
}
