package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.entity.PiContract;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 현재 사용자가 볼 수 있는 계약 범위.
 *
 * <ul>
 *   <li>관리자(ROLE_ADMIN, ROLE_PI_ADMIN): 전체 계약</li>
 *   <li>그 밖의 사용자(계약 담당자, 조회 사용자): 계약의 담당부서가 <b>본인 소속부서</b>인 계약만</li>
 *   <li>소속부서가 지정되지 않은 비관리자: 볼 수 있는 계약 없음</li>
 * </ul>
 * 부서 비교는 앞뒤·중간 공백과 영문 대소문자를 무시한다(예: "인사 팀" = "인사팀").
 * 소속부서는 플랫폼 사용자 프로필(부서 코드 → 공통코드 DEPT 이름)에서 가져오며, 부서 이름 또는 부서 코드와 같으면 같은 부서로 본다.
 */
public record PiAccessScope(boolean all, String deptName, Set<String> deptKeys) {

    public static final PiAccessScope ALL = new PiAccessScope(true, null, Set.of());

    /** JPQL IN 절에 빈 목록을 넘기지 않기 위한 자리표시 값 (실제 부서명과 겹치지 않음) */
    private static final List<String> NO_MATCH = List.of("\u0000");

    public static PiAccessScope dept(String deptName, String deptCode) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        if (StringUtils.hasText(deptName)) keys.add(key(deptName));
        if (StringUtils.hasText(deptCode)) keys.add(key(deptCode));
        String label = StringUtils.hasText(deptName) ? deptName.trim() : (StringUtils.hasText(deptCode) ? deptCode.trim() : null);
        return new PiAccessScope(false, label, Set.copyOf(keys));
    }

    /** 부서명 비교 키: 공백 제거 + 소문자 (JPQL: lower(replace(deptName, ' ', ''))) */
    public static String key(String dept) {
        return dept == null ? "" : dept.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    /** 비관리자이면서 소속부서가 있음 */
    public boolean hasDept() {
        return all || !deptKeys.isEmpty();
    }

    public boolean allows(PiContract c) {
        return c != null && (all || allowsDept(c.getDeptName()));
    }

    public boolean allowsDept(String dept) {
        return all || (StringUtils.hasText(dept) && deptKeys.contains(key(dept.trim())));
    }

    /** 검색 JPQL 파라미터: 부서 조건 사용 여부 (0/1) */
    public int deptOn() {
        return all ? 0 : 1;
    }

    /** 검색 JPQL 파라미터: 허용 부서 키 목록 (비어 있으면 자리표시 값) */
    public List<String> deptKeyList() {
        return deptKeys.isEmpty() ? NO_MATCH : List.copyOf(deptKeys);
    }

    /** 다른 부서 계약이면 403 */
    public void check(PiContract c) {
        if (!allows(c)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, deptName == null
                    ? "소속부서가 지정되지 않아 계약을 볼 수 없습니다. 관리자에게 사용자 부서 지정을 요청하세요."
                    : "소속부서(" + deptName + ") 계약만 볼 수 있습니다.");
        }
    }

    /**
     * 등록·수정 시 저장할 담당부서.
     * 관리자는 입력값 그대로. 비관리자는 비우면 소속부서로 채우고, 다른 부서는 거부한다.
     */
    public String deptForSave(String requested) {
        if (all) {
            return requested;
        }
        if (deptKeys.isEmpty()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "소속부서가 지정되지 않아 계약을 등록·수정할 수 없습니다. 관리자에게 사용자 부서 지정을 요청하세요.");
        }
        if (!StringUtils.hasText(requested)) {
            return deptName;
        }
        if (!allowsDept(requested)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "담당부서는 소속부서(" + deptName + ")만 입력할 수 있습니다. 다른 부서 계약은 관리자에게 요청하세요.");
        }
        return requested;
    }
}
