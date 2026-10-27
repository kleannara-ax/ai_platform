package com.company.module.personalinfo.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.personalinfo.dto.AlertLogResponse;
import com.company.module.personalinfo.dto.AlertRunResponse;
import com.company.module.personalinfo.dto.AlertSendRequest;
import com.company.module.personalinfo.service.PiAlertMailService;
import com.company.module.personalinfo.service.PiRoles;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 계약 만료 알림 메일 API.
 */
@RestController
@RequestMapping("/personalinfo-api")
@RequiredArgsConstructor
public class PiAlertController {

    private final PiAlertMailService alertMailService;

    /** 계약 담당자에게 만료 안내 메일 수동 발송 */
    @PostMapping("/contracts/{contractId}/alerts")
    @PreAuthorize(PiRoles.CAN_WRITE)
    public ResponseEntity<ApiResponse<AlertLogResponse>> sendManual(
            @PathVariable Long contractId,
            @Valid @RequestBody(required = false) AlertSendRequest request,
            @AuthenticationPrincipal(expression = "userId") Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.success(alertMailService.sendManual(contractId, request, currentUserId)));
    }

    /**
     * 만료 알림 일괄 실행 (관리자 전용).
     * 스케줄러(@EnableScheduling)가 없는 환경이나 장애 후 재실행 시 사용. 이미 보낸 단계는 다시 보내지 않는다.
     */
    @PostMapping("/alerts/run")
    @PreAuthorize(PiRoles.CAN_DELETE)
    public ResponseEntity<ApiResponse<AlertRunResponse>> run(
            @RequestParam(value = "baseDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {
        return ResponseEntity.ok(ApiResponse.success(alertMailService.runDailyAlerts(baseDate)));
    }
}
