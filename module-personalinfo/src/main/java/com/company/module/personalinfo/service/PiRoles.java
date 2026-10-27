package com.company.module.personalinfo.service;

import com.company.module.personalinfo.dto.PermissionResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 개인정보 위수탁 모듈 권한(역할) 기준.
 *
 * <p>플랫폼 core 는 사용자에게 부여된 역할 코드(core_user_role.ROLE)를 그대로
 * GrantedAuthority 로 넣어 준다. 이 모듈은 아래 역할 코드만 확인하며,
 * 역할 자체의 생성/부여는 core(공통코드 ROLE 그룹 + 사용자 관리 화면)에서 한다.
 *
 * <table>
 *   <tr><th>역할 코드</th><th>의미</th><th>조회</th><th>등록/수정</th><th>삭제·일괄작업</th></tr>
 *   <tr><td>ROLE_ADMIN</td><td>플랫폼 관리자</td><td>O</td><td>O</td><td>O</td></tr>
 *   <tr><td>ROLE_PI_ADMIN</td><td>개인정보 위수탁 관리자(보안팀)</td><td>O</td><td>O</td><td>O</td></tr>
 *   <tr><td>ROLE_PI_USER</td><td>일반 사용자(계약 담당자)</td><td>O</td><td>O</td><td>X</td></tr>
 *   <tr><td>ROLE_PI_VIEWER</td><td>조회 사용자</td><td>O</td><td>X</td><td>X</td></tr>
 * </table>
 */
public final class PiRoles {

    public static final String PLATFORM_ADMIN = "ROLE_ADMIN";
    public static final String PI_ADMIN = "ROLE_PI_ADMIN";
    public static final String PI_USER = "ROLE_PI_USER";
    public static final String PI_VIEWER = "ROLE_PI_VIEWER";

    /** 조회 권한 (PERSONALINFO_READ) */
    public static final String CAN_READ =
            "hasAnyAuthority('ROLE_ADMIN','ROLE_PI_ADMIN','ROLE_PI_USER','ROLE_PI_VIEWER')";
    /** 등록/수정 권한 (PERSONALINFO_WRITE) */
    public static final String CAN_WRITE =
            "hasAnyAuthority('ROLE_ADMIN','ROLE_PI_ADMIN','ROLE_PI_USER')";
    /** 삭제·일괄작업 권한 (PERSONALINFO_DELETE) - 관리자 전용 */
    public static final String CAN_DELETE =
            "hasAnyAuthority('ROLE_ADMIN','ROLE_PI_ADMIN')";

    private PiRoles() {
    }

    public static PermissionResponse permissionsOf(Authentication authentication) {
        Set<String> roles = authentication == null ? Set.of()
                : authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
        boolean admin = roles.contains(PLATFORM_ADMIN) || roles.contains(PI_ADMIN);
        boolean write = admin || roles.contains(PI_USER);
        boolean read = write || roles.contains(PI_VIEWER);
        return PermissionResponse.builder()
                .canRead(read)
                .canWrite(write)
                .canDelete(admin)
                .canSendMail(write)
                .build();
    }
}
