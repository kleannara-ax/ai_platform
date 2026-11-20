package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamEnergyMonthCompareBulkRequest;
import com.company.module.steamenergy.dto.SteamEnergyMonthCompareResponse;
import com.company.module.steamenergy.dto.SteamEnergyMonthCompareSaveRequest;
import com.company.module.steamenergy.service.SteamEnergyMonthCompareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 회계비용 마감비교 REST API
 *
 * <p>URL Prefix: /steam-energy-api/energy-month-compares
 */
@RestController
@RequestMapping("/steam-energy-api/energy-month-compares")
@RequiredArgsConstructor
public class SteamEnergyMonthCompareController {

    private final SteamEnergyMonthCompareService service;

    /** 연도 전체 (월별 행, 최대 12개) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SteamEnergyMonthCompareResponse>>> getList(@RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.success(service.getList(year)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamEnergyMonthCompareResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** (연, 월) 업서트 — 행 전체 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamEnergyMonthCompareResponse>> save(@Valid @RequestBody SteamEnergyMonthCompareSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(service.save(request)));
    }

    /** 한 해치 일괄 저장 (엑셀 가져오기) */
    @PutMapping("/year")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamBulkSaveResponse>> saveYear(@Valid @RequestBody SteamEnergyMonthCompareBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.saveYear(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
