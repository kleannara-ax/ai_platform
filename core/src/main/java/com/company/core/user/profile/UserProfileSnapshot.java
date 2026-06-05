package com.company.core.user.profile;

import java.time.LocalDate;

/**
 * Core 모듈에서 사용자 프로필 정보를 응답에 포함하기 위한 읽기 전용 스냅샷.
 * 실제 저장소 구현은 module-common에서 담당한다.
 */
public record UserProfileSnapshot(
        String deptCode,
        String deptName,
        String position,
        String jobTitle,
        String employeeNo,
        LocalDate joinDate,
        String officePhone,
        String internalExt
) {
}
