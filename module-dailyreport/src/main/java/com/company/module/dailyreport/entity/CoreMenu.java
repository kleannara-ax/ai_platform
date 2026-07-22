package com.company.module.dailyreport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 플랫폼 메뉴 마스터 엔티티 (★ Phase 4 — 플랫폼 코어 참조용)
 * - 카테고리/페이지 계층 구조 관리
 * - 읽기 전용 참조 목적 (메뉴 자체의 CRUD는 플랫폼 코어에서 관리)
 *
 * 메뉴 계층:
 *   세부공장일보 (CATEGORY, MENU_ID=100)
 *     ├─ 세부공장일보 입력 (PAGE, MENU_ID=101)
 *     └─ 세부공장일보 접근권한 (PAGE, MENU_ID=102)
 */
@Entity(name = "DrCoreMenu")
@Table(name = "core_menu")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoreMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MENU_ID")
    private Long menuId;

    /** 상위 메뉴 ID (NULL = 최상위 카테고리) */
    @Column(name = "PARENT_MENU_ID")
    private Long parentMenuId;

    /** 메뉴 고유 코드 (DAILY_REPORT, DAILY_REPORT_INPUT 등) */
    @Column(name = "MENU_CODE", nullable = false, length = 50, unique = true)
    private String menuCode;

    /** 메뉴 표시명 */
    @Column(name = "MENU_NAME", nullable = false, length = 100)
    private String menuName;

    /** 메뉴 유형: CATEGORY / PAGE / LINK */
    @Column(name = "MENU_TYPE", nullable = false, length = 20)
    private String menuType;

    /** 페이지 URL 경로 */
    @Column(name = "MENU_URL", length = 300)
    private String menuUrl;

    /** 아이콘 CSS 클래스 */
    @Column(name = "ICON", length = 100)
    private String icon;

    /** 정렬 순서 */
    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;

    /** 메뉴 설명 */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** 활성 여부 */
    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
