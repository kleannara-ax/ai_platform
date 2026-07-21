package com.company.module.dailyreport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자별 메뉴 접근 권한 엔티티 (★ Phase 4 — 플랫폼 코어 참조용)
 * - 1계층: MENU_ID=101 (세부공장일보 입력) → 일보 입력 페이지 접근 권한
 * - 3계층: MENU_ID=102 (세부공장일보 접근권한) → 권한 관리 페이지 접근 권한
 *
 * 읽기 전용 참조 목적 — 권한 부여/수정은 AI 플랫폼의 기존 접근 권한 페이지에서 관리
 */
@Entity
@Table(name = "core_menu_permission", uniqueConstraints = {
        @UniqueConstraint(name = "UK_MENU_PERM_USER_MENU",
                columnNames = {"USER_ID", "MENU_ID"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoreMenuPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PERM_ID")
    private Long permId;

    /** 대상 사용자 ID */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 대상 메뉴 ID */
    @Column(name = "MENU_ID", nullable = false)
    private Long menuId;

    /** 조회 권한 */
    @Column(name = "CAN_READ", nullable = false)
    private Boolean canRead;

    /** 쓰기 권한 */
    @Column(name = "CAN_WRITE", nullable = false)
    private Boolean canWrite;

    /** 삭제 권한 */
    @Column(name = "CAN_DELETE", nullable = false)
    private Boolean canDelete;

    /** 관리 권한 */
    @Column(name = "CAN_ADMIN", nullable = false)
    private Boolean canAdmin;

    /** 권한 부여자 */
    @Column(name = "GRANTED_BY")
    private Long grantedBy;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────
    // 비즈니스 메서드 (조회용)
    // ─────────────────────────────────────────────

    /** 읽기 권한 있는지 확인 */
    public boolean hasReadAccess() {
        return Boolean.TRUE.equals(canRead);
    }

    /** 쓰기 권한 있는지 확인 */
    public boolean hasWriteAccess() {
        return Boolean.TRUE.equals(canWrite);
    }

    /** 관리 권한 있는지 확인 */
    public boolean hasAdminAccess() {
        return Boolean.TRUE.equals(canAdmin);
    }
}
