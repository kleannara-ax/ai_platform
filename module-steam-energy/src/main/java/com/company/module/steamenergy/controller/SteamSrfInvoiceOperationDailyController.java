package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceOperationDailyBulkRequest;
import com.company.module.steamenergy.dto.SteamSrfInvoiceOperationDailyResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceOperationDailySaveRequest;
import com.company.module.steamenergy.service.SteamSrfInvoiceOperationDailyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SRF 인보이스 운영내역 REST API
 *
 * <p>URL Prefix: /steam-energy-api/srf-invoice-operation-dailies
 */
@RestController
@RequestMapping("/steam-energy-api/srf-invoice-operation-dailies")
@RequiredArgsConstructor
public class SteamSrfInvoiceOperationDailyController {

    private final SteamSrfInvoiceOperationDailyService service;

    /** 한 해 (month 를 주면 그 달만) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SteamSrfInvoiceOperationDailyResponse>>> getList(@RequestParam int year,
                                                                 @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.success(service.getList(year, month)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamSrfInvoiceOperationDailyResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** (USAGE_DATE) 업서트 — 행 전체 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamSrfInvoiceOperationDailyResponse>> save(@Valid @RequestBody SteamSrfInvoiceOperationDailySaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(service.save(request)));
    }

    /** 범위(한 해 또는 한 달) 일괄 저장 */
    @PutMapping("/bulk")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamBulkSaveResponse>> saveBulk(@Valid @RequestBody SteamSrfInvoiceOperationDailyBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.saveBulk(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
