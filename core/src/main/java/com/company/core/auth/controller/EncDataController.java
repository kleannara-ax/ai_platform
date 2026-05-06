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
 * 암호화 데이터 수신 API
 * URL: POST /api/login/sendEncData
 * Content-Type: application/x-www-form-urlencoded
 *
 * Request Parameter:
 *   encData=암호화된 데이터 문자열
 */
@RestController
@RequestMapping("/api/login")
@RequiredArgsConstructor
public class EncDataController {

    private final EncDataService encDataService;

    /**
     * encData 수신 엔드포인트
     *
     * @param request encData가 포함된 form 요청 파라미터
     * @return 처리 결과
     */
    @PostMapping(value = "/sendEncData", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<EncDataResponse>> sendEncData(
            @Valid @ModelAttribute EncDataRequest request) {

        EncDataResponse response = encDataService.processEncData(request);
        return ResponseEntity.ok(ApiResponse.success("encData 수신 완료", response));
    }
}
