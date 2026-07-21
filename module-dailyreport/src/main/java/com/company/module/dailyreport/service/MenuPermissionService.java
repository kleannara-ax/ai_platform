package com.company.module.dailyreport.service;

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

    /** 메뉴 코드 상수 */
    public static final String MENU_INPUT = "DAILY_REPORT_INPUT";
    public static final String MENU_AUTH  = "DAILY_REPORT_AUTH";

    /**
     * 사용자가 '세부공장일보 입력' 페이지에 접근 가능한지 확인
     * → core_menu_permission에서 DAILY_REPORT_INPUT 메뉴의 CAN_READ 확인
     */
    public boolean canAccessInputPage(Long userId) {
        return menuPermissionRepository.hasReadAccess(userId, MENU_INPUT);
    }

    /**
     * 사용자가 '세부공장일보 접근권한' 관리 페이지에 접근 가능한지 확인
     * → core_menu_permission에서 DAILY_REPORT_AUTH 메뉴의 CAN_READ 확인
     */
    public boolean canAccessAuthPage(Long userId) {
        return menuPermissionRepository.hasReadAccess(userId, MENU_AUTH);
    }

    /**
     * 사용자가 '세부공장일보 입력' 페이지에서 쓰기 가능한지 확인
     */
    public boolean canWriteInputPage(Long userId) {
        return menuPermissionRepository.findWritePermissionByUserIdAndMenuCode(userId, MENU_INPUT)
                .isPresent();
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
