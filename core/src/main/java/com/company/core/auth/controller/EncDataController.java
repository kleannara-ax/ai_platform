package com.company.core.auth.controller;

import com.company.core.auth.dto.EncDataRequest;
import com.company.core.auth.dto.EncDataResponse;
import com.company.core.auth.service.EncDataService;
import com.company.core.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * SSO 암호화 데이터 수신 및 로그인 API
 * URL: POST /api/login/sendEncData
 * Content-Type: application/x-www-form-urlencoded
 *
 * 처리 흐름:
 * 1. encData를 form 파라미터로 수신
 * 2. SSO 서버(encValidateProduct)에 productId + encData 전달하여 검증
 * 3. 검증 성공 시 sproId로 사용자 조회 → JWT 토큰 발급 (로그인)
 *
 * Request Parameter:
 *   encData=암호화된 데이터 문자열
 *
 * Response (성공):
 * {
 *   "success": true,
 *   "data": {
 *     "accessToken": "...",
 *     "refreshToken": "...",
 *     "tokenType": "Bearer",
 *     "expiresIn": 3600,
 *     "sproId": "사용자아이디",
 *     "resultMessage": "SSO 인증 및 로그인 처리 완료"
 *   }
 * }
 */
@RestController
@RequestMapping("/api/login")
@RequiredArgsConstructor
public class EncDataController {

    private final EncDataService encDataService;

    /**
     * encData 수신 → SSO 검증 → 로그인 처리 엔드포인트
     *
     * @param request encData가 포함된 form 요청 파라미터
     * @return JWT 토큰이 포함된 로그인 응답
     */
    @PostMapping(value = "/sendEncData", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<EncDataResponse>> sendEncData(
            @Valid @ModelAttribute EncDataRequest request) {

        EncDataResponse response = encDataService.processEncData(request);
        return ResponseEntity.ok(ApiResponse.success("SSO 인증 및 로그인 처리 완료", response));
    }
}
