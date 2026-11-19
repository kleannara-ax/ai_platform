package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFuelDailyUsageBulkRequest;
import com.company.module.steamenergy.dto.SteamFuelDailyUsageResponse;
import com.company.module.steamenergy.dto.SteamFuelDailyUsageSaveRequest;
import com.company.module.steamenergy.service.SteamFuelDailyUsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 유동상·소각로·복합·외부 일일 스팀 구매량 REST API
 *
 * <p>URL Prefix: /steam-energy-api/fuel-daily-usages
 */
@RestController
@RequestMapping("/steam-energy-api/fuel-daily-usages")
@RequiredArgsConstructor
public class SteamFuelDailyUsageController {

    private final SteamFuelDailyUsageService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SteamFuelDailyUsageResponse>>> getList(
            @RequestParam int year, @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.success(service.getList(year, month)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamFuelDailyUsageResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** 일자 업서트 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamFuelDailyUsageResponse>> save(
            @Valid @RequestBody SteamFuelDailyUsageSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(service.save(request)));
    }

    @PutMapping("/month")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamBulkSaveResponse>> saveMonth(
            @Valid @RequestBody SteamFuelDailyUsageBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.saveMonth(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
