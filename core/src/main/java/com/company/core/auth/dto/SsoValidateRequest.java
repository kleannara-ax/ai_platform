package com.company.core.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SSO 암호화 데이터 검증 요청 DTO
 * POST https://sso.kleannara.com/rest/security/encValidateProduct
 * Content-Type: application/json
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoValidateRequest {

    /** 제품 ID (고정값: PRO_000643) */
    private String productId;

    /** 암호화된 데이터 */
    private String encData;
}
