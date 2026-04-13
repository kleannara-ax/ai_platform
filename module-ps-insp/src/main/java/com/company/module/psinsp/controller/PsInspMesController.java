package com.company.module.psinsp.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.psinsp.dto.PsInspMesSendRequest;
import com.company.module.psinsp.dto.PsInspMesSendResponse;
import com.company.module.psinsp.service.PsInspMesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * MES 결과 전송 REST API 컨트롤러
 *
 * <p>프론트엔드에서 DB 저장 완료 후 자동으로 호출하여
 * 검사 결과를 MES에 전달합니다.
 *
 * <p>전송 규격:
 * <pre>
 *   POST /ps-insp-api/mes/send-result
 *   Content-Type: application/json
 *   Body: {
 *     "IND_BCD": "26228J0039",
 *     "ResultData": 265.30
 *   }
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/ps-insp-api/mes")
@RequiredArgsConstructor
public class PsInspMesController {

    private final PsInspMesService mesService;

    /**
     * MES에 검사 결과 전송
     *
     * @param request IND_BCD(개별바코드) + ResultData(지분 커버리지 ppm 값)
     * @return ApiResponse 래핑된 전송 결과
     */
    @PreAuthorize("@coreMenuService.hasMenuAccess(authentication.authorities.iterator().next().authority, 'PS_INSP_MGMT')")
    @PostMapping("/send-result")
    public ResponseEntity<ApiResponse<PsInspMesSendResponse>> sendResult(
            @Valid @RequestBody PsInspMesSendRequest request) {
        PsInspMesSendResponse response = mesService.sendResult(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
