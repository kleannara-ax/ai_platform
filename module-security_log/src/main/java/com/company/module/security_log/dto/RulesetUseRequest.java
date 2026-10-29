package com.company.module.security_log.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 시스템별 룰셋 사용/미사용 요청
 * - 보낸 값만 변경한다 (null 이면 그대로)
 */
@Getter
@Setter
public class RulesetUseRequest {

    @Pattern(regexp = "^[YN]$", message = "공통 룰셋 사용 여부는 Y 또는 N 입니다.")
    private String commonRuleYn;

    @Pattern(regexp = "^[YN]$", message = "시스템 전용 룰셋 사용 여부는 Y 또는 N 입니다.")
    private String systemRuleYn;
}
