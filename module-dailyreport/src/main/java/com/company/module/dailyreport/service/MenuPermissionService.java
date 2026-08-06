package com.company.module.dailyreport.service;

import com.company.module.dailyreport.repository.CellAuthRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메뉴(페이지) 접근 권한 검증 서비스 (★ Phase 4 — cell_auth 단일 기준)
 *
 * 권한 판단 기준:
 *   - admin 여부: core_user_role에 ROLE_ADMIN 매핑 존재 여부 (EntityManager native query)
 *     ★ 2026-08 변경: 다중 역할(Multi-Role) 지원 도입으로 core_user.ROLE(단일 컬럼)은
 *       더 이상 "이 사용자의 유일한 역할"을 의미하지 않는다. core_user.ROLE은 대표 역할
 *       캐시로만 유지되므로, admin 여부 판별은 반드시 core_user_role 매핑 테이블을
 *       조회해야 한다 (사용자가 ROLE_ADMIN을 보조 역할로 보유한 경우도 인식해야 함).
 *   - 일반 사용자: daily_report_cell_auth 활성 레코드 존재 여부
 *
 * ※ core_menu_permission 테이블 의존 제거
 *   → core_role_menu(역할 단위)와 충돌하고, 관리 UI 없이 빈 테이블이라
 *     항상 false를 반환하여 모든 일반 사용자 접근이 차단되는 문제 해결
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuPermissionService {

    private final CellAuthRepository cellAuthRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /** 메뉴 코드 상수 (프론트엔드 호환 유지) */
    public static final String MENU_INPUT = "DAILY_REPORT_INPUT";
    public static final String MENU_AUTH  = "DAILY_REPORT_AUTH";

    /**
     * 사용자가 '세부공장일보 입력' 페이지에 접근 가능한지 확인
     *   → admin이면 무조건 허용
     *   → 일반 사용자: 활성 cell_auth가 1개라도 있으면 허용
     */
    public boolean canAccessInputPage(Long userId) {
        if (isAdmin(userId)) return true;
        return cellAuthRepository.existsByUserIdAndIsActiveTrue(userId);
    }

    /**
     * 사용자가 '세부공장일보 컬럼관리' 관리 페이지에 접근 가능한지 확인
     *   → admin이면 무조건 허용
     *   → 일반 사용자: 활성 cell_auth가 있으면 읽기 전용 접근 허용
     */
    public boolean canAccessAuthPage(Long userId) {
        if (isAdmin(userId)) return true;
        return cellAuthRepository.existsByUserIdAndIsActiveTrue(userId);
    }

    /**
     * 사용자가 '세부공장일보 입력' 페이지에서 쓰기 가능한지 확인
     *   → admin이면 무조건 허용
     *   → 일반 사용자: 활성 cell_auth가 있으면 허용
     */
    public boolean canWriteInputPage(Long userId) {
        if (isAdmin(userId)) return true;
        return cellAuthRepository.existsByUserIdAndIsActiveTrue(userId);
    }

    /**
     * 사용자가 '세부공장일보 컬럼관리' 페이지에서 관리 작업(등록/수정/삭제/재동기화) 가능한지 확인
     *   → admin이면 무조건 허용
     *   → 일반 사용자: 활성 cell_auth가 있으면(=컬럼관리 대시보드에 등록된 사용자) 허용
     *
     * ★ 2026-07 변경: 기존에는 admin만 CRUD 가능했으나, "컬럼관리 대시보드에 등록된
     *   일반 사용자도 관리자와 동일하게 전체 기능(등록/수정/삭제/재동기화)을 제어할 수
     *   있어야 한다"는 요구사항에 따라 canAccessAuthPage와 동일한 기준으로 통일한다.
     *   (페이지 접근 가능 = 모든 CRUD 가능)
     */
    public boolean canAdminAuthPage(Long userId) {
        return canAccessAuthPage(userId);
    }

    /**
     * ★ 프론트엔드 UI 라벨 분기용 — 사용자가 ROLE_ADMIN 역할을 보유하는지 그대로 노출.
     * (canAdminAuthPage는 "컬럼관리 CRUD 가능 여부"이고, isRealAdmin은 "진짜 관리자 계정인지"
     *  구분이 필요한 경우에만 사용 — 예: 화면에 "관리자" 대신 "담당자"로 표시)
     */
    public boolean isRealAdmin(Long userId) {
        return isAdmin(userId);
    }

    // ─────────────────────────────────────────────
    // 내부: admin 여부 판별 (Architecture Rule #4: native query)
    // ─────────────────────────────────────────────

    /**
     * core_user_role 매핑 테이블에서 ROLE_ADMIN 보유 여부 확인 (다중 역할 지원)
     * ※ core 모듈 Entity 직접 import 없이 native query 사용 (아키텍처 규칙 준수)
     * ※ V3.0.0 다중 역할 스키마 기준: core_user_role(user_id, role) 매핑 테이블 조회
     *   (core_user.role 단일 컬럼은 대표 역할 캐시일 뿐이므로 admin 판별에 사용하지 않음 —
     *    사용자가 ROLE_ADMIN을 보조 역할로 보유한 경우도 놓치지 않기 위함)
     */
    private boolean isAdmin(Long userId) {
        if (userId == null) return false;
        Number count = (Number) entityManager
                .createNativeQuery(
                        "SELECT COUNT(*) FROM core_user_role WHERE user_id = ?1 AND role = 'ROLE_ADMIN'")
                .setParameter(1, userId)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
