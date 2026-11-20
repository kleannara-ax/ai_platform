package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioMachineBulkRequest;
import com.company.module.steamenergy.dto.SteamEnergyRatioMachineResponse;
import com.company.module.steamenergy.dto.SteamEnergyRatioMachineSaveRequest;
import com.company.module.steamenergy.service.SteamEnergyRatioMachineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 연료비 배분비율(호기별) REST API
 *
 * <p>URL Prefix: /steam-energy-api/energy-ratio-machines
 */
@RestController
@RequestMapping("/steam-energy-api/energy-ratio-machines")
@RequiredArgsConstructor
public class SteamEnergyRatioMachineController {

    private final SteamEnergyRatioMachineService service;

    /** 연도 전체 (월별 행, 최대 12개) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SteamEnergyRatioMachineResponse>>> getList(@RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.success(service.getList(year)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamEnergyRatioMachineResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** (연, 월) 업서트 — 행 전체 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamEnergyRatioMachineResponse>> save(@Valid @RequestBody SteamEnergyRatioMachineSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(service.save(request)));
    }

    /** 한 해치 일괄 저장 (엑셀 가져오기) */
    @PutMapping("/year")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamBulkSaveResponse>> saveYear(@Valid @RequestBody SteamEnergyRatioMachineBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.saveYear(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
