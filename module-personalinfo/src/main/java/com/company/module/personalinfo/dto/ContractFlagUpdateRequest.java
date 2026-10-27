package com.company.module.personalinfo.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 계약 목록에서 Y/N 항목을 바로 바꿀 때 쓰는 요청.
 * 보낸 항목만 바꾸고, 보내지 않은(null) 항목은 그대로 둔다.
 */
@Getter
@Setter
public class ContractFlagUpdateRequest {

    /** ISMS 인증 범위 여부 (Y/N) */
    @Pattern(regexp = "[YN]", message = "ISMS 인증 여부는 Y 또는 N 이어야 합니다.")
    private String ismsScopeYn;

    /** 재위탁 여부 (Y/N) */
    @Pattern(regexp = "[YN]", message = "재위탁 여부는 Y 또는 N 이어야 합니다.")
    private String subcontractYn;
}
