package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamUnitDailyUsageBulkRequest;
import com.company.module.steamenergy.dto.SteamUnitDailyUsageResponse;
import com.company.module.steamenergy.dto.SteamUnitDailyUsageSaveRequest;
import com.company.module.steamenergy.service.SteamUnitDailyUsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 원단위 일지(호기별 일일 실적) REST API
 *
 * <p>URL Prefix: /steam-energy-api/unit-daily-usages
 */
@RestController
@RequestMapping("/steam-energy-api/unit-daily-usages")
@RequiredArgsConstructor
public class SteamUnitDailyUsageController {

    private final SteamUnitDailyUsageService service;

    /** 연/월(월 생략 시 연도 전체)·호기(생략 시 전체) 목록 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SteamUnitDailyUsageResponse>>> getList(
            @RequestParam int year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String machineCode) {
        return ResponseEntity.ok(ApiResponse.success(service.getList(year, month, machineCode)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamUnitDailyUsageResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** (일자 + 호기) 업서트 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamUnitDailyUsageResponse>> save(
            @Valid @RequestBody SteamUnitDailyUsageSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(service.save(request)));
    }

    /** 한 달치 일괄 저장 (엑셀 가져오기) */
    @PutMapping("/month")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamBulkSaveResponse>> saveMonth(
            @Valid @RequestBody SteamUnitDailyUsageBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.saveMonth(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
