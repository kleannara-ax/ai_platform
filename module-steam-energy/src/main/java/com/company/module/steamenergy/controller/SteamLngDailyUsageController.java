package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamLngDailyUsageBulkRequest;
import com.company.module.steamenergy.dto.SteamLngDailyUsageResponse;
import com.company.module.steamenergy.dto.SteamLngDailyUsageSaveRequest;
import com.company.module.steamenergy.service.SteamLngDailyUsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 일별 LNG 연료 사용량 REST API
 *
 * <p>URL Prefix: /steam-energy-api/lng-daily-usages
 */
@RestController
@RequestMapping("/steam-energy-api/lng-daily-usages")
@RequiredArgsConstructor
public class SteamLngDailyUsageController {

    private final SteamLngDailyUsageService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SteamLngDailyUsageResponse>>> getList(
            @RequestParam int year, @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.success(service.getList(year, month)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamLngDailyUsageResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** 일자 업서트 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamLngDailyUsageResponse>> save(
            @Valid @RequestBody SteamLngDailyUsageSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(service.save(request)));
    }

    @PutMapping("/month")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamBulkSaveResponse>> saveMonth(
            @Valid @RequestBody SteamLngDailyUsageBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.saveMonth(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
