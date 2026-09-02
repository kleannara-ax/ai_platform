package com.company.module.safety.entity;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
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

/**
 * 안전작업 매뉴얼 화면 좌측에 노출하는 공지사항.
 *
 * <p>분류/매뉴얼과 직접 연결되지 않는 독립 게시물이며, 상단 고정({@code PINNED_YN})한 글이
 * 먼저 보인다. 삭제는 물리 삭제하지 않고 {@code DELETED_YN='Y'} 로만 처리한다.
 */
@Entity
@Table(name = "safety_notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyNotice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NOTICE_ID")
    private Long noticeId;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Column(name = "CONTENT", columnDefinition = "TEXT")
    private String content;

    /** 상단 고정 여부 (Y/N) */
    @Column(name = "PINNED_YN", nullable = false, length = 1)
    private String pinnedYn;

    @Builder
    private SafetyNotice(String title, String content, boolean pinned, String createdBy) {
        validateTitle(title);
        this.title = title;
        this.content = content;
        this.pinnedYn = pinned ? "Y" : "N";
        markCreatedBy(createdBy);
    }

    // ----------------------------------------------------------------
    // 비즈니스 메서드
    // ----------------------------------------------------------------

    public boolean isPinned() {
        return "Y".equals(this.pinnedYn);
    }

    public void update(String title, String content, boolean pinned, String updatedBy) {
        validateTitle(title);
        this.title = title;
        this.content = content;
        this.pinnedYn = pinned ? "Y" : "N";
        markUpdatedBy(updatedBy);
    }

    /** 소프트 삭제. 물리 DELETE 대신 DELETED_YN='Y' 로만 처리한다. */
    public void delete(String deletedBy) {
        markDeleted(deletedBy);
    }

    public void restore() {
        markRestored();
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "공지 제목은 필수입니다.");
        }
    }
}
