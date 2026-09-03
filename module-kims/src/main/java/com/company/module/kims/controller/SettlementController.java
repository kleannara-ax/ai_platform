package com.company.module.kims.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.kims.dto.response.SettlementResponse;
import com.company.module.kims.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 월말 결산 REST API.
 * <p>URL prefix: {@code /kims-api/settlement}
 * <p>결산 조회는 관리자(ADMIN) 권한이 필요하다. (기간 미입력 시 이번 달)
 */
@RestController
@RequestMapping("/kims-api/settlement")
@RequiredArgsConstructor
@PreAuthorize("@kimsPerm.isAdmin(authentication)")
public class SettlementController {

    private final SettlementService settlementService;

    /** 월말 결산 조회 */
    @GetMapping
    public ResponseEntity<ApiResponse<SettlementResponse>> getSettlement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(settlementService.getSettlement(from, to)));
    }

    /** 월말 결산 Excel 다운로드 (여러 시트) */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = settlementService.exportExcel(from, to);
        String filename = "settlement.xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
