package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailDailyBulkRequest;
import com.company.module.steamenergy.dto.SteamFluidizedDetailDailyResponse;
import com.company.module.steamenergy.dto.SteamFluidizedDetailDailySaveRequest;
import com.company.module.steamenergy.service.SteamFluidizedDetailDailyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 유동상 세부 운영내역 (일별) REST API
 *
 * <p>URL Prefix: /steam-energy-api/fluidized-detail-dailies
 */
@RestController
@RequestMapping("/steam-energy-api/fluidized-detail-dailies")
@RequiredArgsConstructor
public class SteamFluidizedDetailDailyController {

    private final SteamFluidizedDetailDailyService service;

    /** 한 해 (month 를 주면 그 달만) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SteamFluidizedDetailDailyResponse>>> getList(@RequestParam int year,
                                                                 @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.success(service.getList(year, month)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamFluidizedDetailDailyResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** (USAGE_DATE) 업서트 — 행 전체 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamFluidizedDetailDailyResponse>> save(@Valid @RequestBody SteamFluidizedDetailDailySaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(service.save(request)));
    }

    /** 범위(한 해 또는 한 달) 일괄 저장 */
    @PutMapping("/bulk")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamBulkSaveResponse>> saveBulk(@Valid @RequestBody SteamFluidizedDetailDailyBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.saveBulk(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
