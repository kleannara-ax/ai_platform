package com.company.core.permission.support;

import java.util.List;
import java.util.Map;

/**
 * 역할(Role) 목록 제공 인터페이스.
 * core 모듈은 공통코드(module-code)에 직접 의존하지 않고,
 * app 모듈에서 공통코드 기반 구현체를 주입받는다.
 */
public interface RoleProvider {

    /**
     * 활성 역할 코드 목록 (정렬순)
     * e.g. ["ROLE_ADMIN", "ROLE_MANAGER", "ROLE_USER"]
     */
    List<String> getActiveRoles();

    /**
     * 역할 코드 → 역할 이름 맵
     * e.g. {"ROLE_ADMIN":"관리자", "ROLE_MANAGER":"매니저", ...}
     */
    Map<String, String> getRoleNameMap();
}
