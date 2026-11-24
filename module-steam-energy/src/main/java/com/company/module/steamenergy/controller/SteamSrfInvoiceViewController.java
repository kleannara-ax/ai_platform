package com.company.module.steamenergy.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.legacy.service.SrfBoilerInvoiceWorkbookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SRF 보일러 인보이스 서식 조회 (읽기 전용)
 *
 * <p>URL Prefix: /steam-energy-api/srf-invoice-view
 * <p>Summary(VK) · 운영내역 화면이 그릴 서식(행 · 칸 배치와 고정 글자, 다른 표에서 이어 온 값)을 돌려준다.
 * 서식 배치는 classpath 의 sheet-metadata JSON 그대로라 맵으로 전달한다. 입력값은 /steam-energy-api/srf-invoice-* 표 API 에 있다.
 */
@RestController
@RequestMapping("/steam-energy-api/srf-invoice-view")
@RequiredArgsConstructor
public class SteamSrfInvoiceViewController {

    private final SrfBoilerInvoiceWorkbookService workbookService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(@RequestParam("year") int year, @RequestParam("month") int month) {
        requirePeriod(year, month);
        try {
            return ResponseEntity.ok(ApiResponse.success(workbookService.renderSummary(year, month)));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    @GetMapping("/operation")
    public ResponseEntity<ApiResponse<Map<String, Object>>> operation(@RequestParam("year") int year, @RequestParam("month") int month) {
        requirePeriod(year, month);
        try {
            return ResponseEntity.ok(ApiResponse.success(workbookService.renderOperation(year, month)));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    private static void requirePeriod(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) throw new BusinessException(ErrorCode.INVALID_INPUT, "연/월이 올바르지 않습니다.");
    }
}
