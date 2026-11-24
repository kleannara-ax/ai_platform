package com.company.module.cckb.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CCKB_PERM 관리자 추가 요청 DTO.
 */
@Getter
@NoArgsConstructor
public class CckbAdminAddRequest {

    /** 추가할 플랫폼 로그인 ID */
    @NotBlank
    private String loginId;

    /** 표시용 이름 (미입력 시 loginId 사용) */
    private String name;
}
