package com.company.module.cckb.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * CCKB_PERM(공통코드) 명단에 등록된 관리자 1명을 나타내는 응답 DTO.
 */
@Getter
@Builder
public class CckbAdminResponse {
    /** 플랫폼 로그인 ID (code_detail.CODE) */
    private String loginId;
    /** 표시용 이름 (code_detail.CODE_NAME) */
    private String name;
    /** 활성 여부 (code_detail.IS_ACTIVE) */
    private boolean active;
}
