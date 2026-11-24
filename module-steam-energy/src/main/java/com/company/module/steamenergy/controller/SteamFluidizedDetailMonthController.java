package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailMonthBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedDetailMonthResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailMonthSaveRequest;
import com.company.module.steamenergy.service.SteamFluidizedDetailMonthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 유동상 세부 운영내역 (월별 단가·비용) REST API
 *
 * <p>URL Prefix: /steam-energy-api/fluidized-detail-months
 */
@RestController
@RequestMapping("/steam-energy-api/fluidized-detail-months")
@RequiredArgsConstructor
public class SteamFluidizedDetailMonthController {

    private final SteamFluidizedDetailMonthService service;

    /** 한 해 (month 를 주면 그 달만) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SteamFluidizedDetailMonthResponse>>> getList(@RequestParam int year,
                                                                 @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.success(service.getList(year, month)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamFluidizedDetailMonthResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** (YEAR_NO, MONTH_NO) 업서트 — 행 전체 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamFluidizedDetailMonthResponse>> save(@Valid @RequestBody SteamFluidizedDetailMonthSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(service.save(request)));
    }

    /** 범위(한 해 또는 한 달) 일괄 저장 */
    @PutMapping("/bulk")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamBulkSaveResponse>> saveBulk(@Valid @RequestBody SteamFluidizedDetailMonthBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.saveBulk(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
