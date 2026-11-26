package com.company.module.security_log.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 점검 대상 시스템 사용여부 토글 요청 */
@Getter
@Setter
public class SystemUseYnRequest {

    @NotBlank
    @Pattern(regexp = "^[YN]$", message = "사용여부는 Y 또는 N 입니다.")
    private String useYn;
}
