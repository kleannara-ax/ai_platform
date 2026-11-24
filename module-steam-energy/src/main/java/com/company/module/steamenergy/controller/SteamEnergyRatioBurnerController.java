package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioBurnerBulkRequest;
import com.company.module.steamenergy.dto.SteamEnergyRatioBurnerResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioBurnerSaveRequest;
import com.company.module.steamenergy.service.SteamEnergyRatioBurnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 연료비 배분비율(버너 LNG) REST API
 *
 * <p>URL Prefix: /steam-energy-api/energy-ratio-burners
 */
@RestController
@RequestMapping("/steam-energy-api/energy-ratio-burners")
@RequiredArgsConstructor
public class SteamEnergyRatioBurnerController {

    private final SteamEnergyRatioBurnerService service;

    /** 연도 전체 (월별 행, 최대 12개) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SteamEnergyRatioBurnerResponse>>> getList(@RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.success(service.getList(year)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamEnergyRatioBurnerResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** (연, 월) 업서트 — 행 전체 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamEnergyRatioBurnerResponse>> save(@Valid @RequestBody SteamEnergyRatioBurnerSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(service.save(request)));
    }

    /** 한 해치 일괄 저장 (엑셀 가져오기) */
    @PutMapping("/year")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamBulkSaveResponse>> saveYear(@Valid @RequestBody SteamEnergyRatioBurnerBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.saveYear(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
