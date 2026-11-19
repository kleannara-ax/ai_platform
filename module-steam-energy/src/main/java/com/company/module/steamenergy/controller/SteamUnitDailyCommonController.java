package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamUnitDailyCommonBulkRequest;
import com.company.module.steamenergy.dto.SteamUnitDailyCommonResponse;
import com.company.module.steamenergy.dto.SteamUnitDailyCommonSaveRequest;
import com.company.module.steamenergy.service.SteamUnitDailyCommonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 원단위 일지(일자별 공통 계측값) REST API
 *
 * <p>URL Prefix: /steam-energy-api/unit-daily-commons
 */
@RestController
@RequestMapping("/steam-energy-api/unit-daily-commons")
@RequiredArgsConstructor
public class SteamUnitDailyCommonController {

    private final SteamUnitDailyCommonService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SteamUnitDailyCommonResponse>>> getList(
            @RequestParam int year, @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.success(service.getList(year, month)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamUnitDailyCommonResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** 일자 업서트 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamUnitDailyCommonResponse>> save(
            @Valid @RequestBody SteamUnitDailyCommonSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(service.save(request)));
    }

    @PutMapping("/month")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamBulkSaveResponse>> saveMonth(
            @Valid @RequestBody SteamUnitDailyCommonBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.saveMonth(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
