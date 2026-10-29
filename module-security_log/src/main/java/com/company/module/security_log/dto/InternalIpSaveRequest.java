package com.company.module.security_log.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 내부 IP 대역 등록/수정 요청 */
@Getter
@Setter
public class InternalIpSaveRequest {

    /** CIDR(10.0.0.0/8) 또는 단일 IP(203.0.113.10) */
    @NotBlank(message = "IP 대역은 필수입니다.")
    @Size(max = 50)
    private String ipRange;

    @NotBlank(message = "대역 이름은 필수입니다.")
    @Size(max = 100)
    private String ipName;

    @Size(max = 500)
    private String description;

    @Pattern(regexp = "^[YN]$", message = "사용여부는 Y 또는 N 입니다.")
    private String useYn = "Y";

    @Min(0)
    @Max(99999)
    private Integer sortOrder = 0;
}
