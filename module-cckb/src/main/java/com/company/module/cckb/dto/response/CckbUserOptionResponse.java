package com.company.module.cckb.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * CCKB 관리자 추가용 사용자 검색 드롭다운 옵션 DTO.
 * (module-dailyreport의 UserSimpleResponse와 동일한 목적 — core_user를 JdbcTemplate으로
 *  직접 조회해서 플랫폼 전체 사용자 목록을 제공한다.)
 */
@Getter
@Builder
public class CckbUserOptionResponse {
    private Long userId;
    private String loginId;
    private String userName;
}
