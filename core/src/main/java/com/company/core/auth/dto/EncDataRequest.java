package com.company.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 암호화 데이터 수신 요청 DTO
 * POST /api/login/sendEncData
 */
@Getter
@Setter
@NoArgsConstructor
public class EncDataRequest {

    @NotBlank(message = "encData는 필수입니다.")
    private String encData;
}
