package com.company.module.security_log.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** IP 확인 요청 (저장하지 않음) */
@Getter
@Setter
public class InternalIpTestRequest {

    @NotBlank(message = "확인할 IP 를 입력하세요.")
    @Size(max = 50)
    private String ip;
}
