package com.company.module.kims.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.kims.dto.request.QrRequestCreateRequest;
import com.company.module.kims.dto.request.ServiceRequestCreateRequest;
import com.company.module.kims.dto.response.QrLocationResponse;
import com.company.module.kims.dto.response.ServiceRequestResponse;
import com.company.module.kims.entity.enums.ReceivedChannel;
import com.company.module.kims.service.QrLocationService;
import com.company.module.kims.service.ServiceRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * QR 스캔 공개 API (비로그인 접근 가능).
 * <p>휴대폰으로 QR 을 스캔해 열린 페이지가 토큰으로 위치/부서를 조회하고,
 * 그 자리에서 업무 요청을 제출한다.
 * <p>URL prefix: {@code /qr-api} (SecurityConfig 에서 permitAll)
 */
@RestController
@RequestMapping("/qr-api")
@RequiredArgsConstructor
public class QrPublicController {

    private final QrLocationService qrLocationService;
    private final ServiceRequestService serviceRequestService;

    /** 토큰으로 QR 구역(위치/부서) 정보 조회 */
    @GetMapping("/locations/{token}")
    public ResponseEntity<ApiResponse<QrLocationResponse>> getByToken(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(qrLocationService.getByToken(token)));
    }

    /**
     * QR 스캔 후 업무 요청 제출 (비로그인).
     * <p>위치/부서는 토큰에서 채우고, 접수채널은 QR 로 기록한다.
     */
    @PostMapping("/requests/{token}")
    public ResponseEntity<ApiResponse<ServiceRequestResponse>> submit(
            @PathVariable String token,
            @Valid @RequestBody QrRequestCreateRequest req) {

        // 토큰 검증 → 위치/부서 확보 (비활성/무효 토큰은 예외)
        QrLocationResponse loc = qrLocationService.getByToken(token);

        ServiceRequestCreateRequest scr = new ServiceRequestCreateRequest();
        scr.setRequesterName(req.getRequesterName());
        scr.setContact(req.getContact());
        scr.setDepartment(loc.getDepartment());
        scr.setLocation(loc.getLocation());
        scr.setRequestType(req.getRequestType());
        scr.setIssueType(req.getIssueType());
        scr.setIpKind(req.getIpKind());
        scr.setChangerName(req.getChangerName());
        scr.setContent(req.getContent());
        scr.setUrgent(req.isUrgent());
        scr.setReceivedChannel(ReceivedChannel.QR);

        return ResponseEntity.ok(ApiResponse.created(serviceRequestService.register(scr)));
    }
}
