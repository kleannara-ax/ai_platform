package com.company.module.dailyreport.service;

import com.company.module.dailyreport.repository.CellAuthRepository;
import com.company.module.dailyreport.repository.CoreMenuPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메뉴(페이지) 접근 권한 검증 서비스 (★ Phase 4 신규)
 * - 1계층: '세부공장일보 입력' 페이지 접근 권한 확인
 * - 3계층: '세부공장일보 접근권한' 관리 페이지 접근 권한 확인
 *
 * 참조하는 메뉴 코드:
 *   - DAILY_REPORT_INPUT  (MENU_ID=101): 입력 페이지
 *   - DAILY_REPORT_AUTH   (MENU_ID=102): 접근권한 관리 페이지
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuPermissionService {

    private final CoreMenuPermissionRepository menuPermissionRepository;
    private final CellAuthRepository cellAuthRepository;

    /** 메뉴 코드 상수 */
    public static final String MENU_INPUT = "DAILY_REPORT_INPUT";
    public static final String MENU_AUTH  = "DAILY_REPORT_AUTH";

    /**
     * 사용자가 '세부공장일보 입력' 페이지에 접근 가능한지 확인
     *
     * ★ 변경: core_menu_permission 대신 daily_report_cell_auth 기반 판단
     *   → 사용자에게 활성 셀 권한이 1개라도 있으면 접근 허용
     *   → 관리자(canAdminAuthPage)도 접근 허용
     *
     * 이유: core_menu_permission은 사용자별 UI가 없어 SQL 직접 INSERT만 가능.
     *       cell_auth 기반으로 전환하면 '접근권한 관리' 페이지에서 담당 셀 추가만으로
     *       메뉴 접근이 자동 허용/차단되어 관리 편의성 확보.
     */
    public boolean canAccessInputPage(Long userId) {
        // 관리자(접근권한 관리 페이지 접근 가능자)는 무조건 허용
        if (menuPermissionRepository.hasReadAccess(userId, MENU_AUTH)) {
            return true;
        }
        // 일반 사용자: 활성 cell_auth가 있으면 허용
        return cellAuthRepository.existsByUserIdAndIsActiveTrue(userId);
    }

    /**
     * 사용자가 '세부공장일보 접근권한' 관리 페이지에 접근 가능한지 확인
     * → 관리자(core_menu_permission MENU_AUTH 읽기) 또는 cell_auth 보유자
     *   관리자: CRUD 가능, 담당자: 읽기 전용 (canAdmin으로 분기)
     */
    public boolean canAccessAuthPage(Long userId) {
        if (menuPermissionRepository.hasReadAccess(userId, MENU_AUTH)) {
            return true;
        }
        return cellAuthRepository.existsByUserIdAndIsActiveTrue(userId);
    }

    /**
     * 사용자가 '세부공장일보 입력' 페이지에서 쓰기 가능한지 확인
     * → cell_auth 기반: 활성 셀 권한이 있으면 쓰기도 허용
     */
    public boolean canWriteInputPage(Long userId) {
        return cellAuthRepository.existsByUserIdAndIsActiveTrue(userId);
    }

    /**
     * 사용자가 접근권한 관리 페이지에서 관리 작업(CRUD) 가능한지 확인
     */
    public boolean canAdminAuthPage(Long userId) {
        return menuPermissionRepository.hasAdminAccess(userId, MENU_AUTH);
    }

    /**
     * 일반적인 메뉴 코드 기반 읽기 권한 확인
     */
    public boolean hasReadAccess(Long userId, String menuCode) {
        return menuPermissionRepository.hasReadAccess(userId, menuCode);
    }
}
