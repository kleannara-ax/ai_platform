package com.company.module.security_log.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 룰 사용여부 토글 요청 */
@Getter
@Setter
public class RuleUseYnRequest {

    @NotBlank
    @Pattern(regexp = "^[YN]$", message = "사용여부는 Y 또는 N 입니다.")
    private String useYn;
}
