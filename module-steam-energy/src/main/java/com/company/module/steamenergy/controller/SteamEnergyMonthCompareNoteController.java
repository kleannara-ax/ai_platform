package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamEnergyMonthCompareNoteResponse;
import com.company.module.steamenergy.dto.SteamEnergyMonthCompareNoteSaveRequest;
import com.company.module.steamenergy.service.SteamEnergyMonthCompareNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 회계비용 마감비교 비고 REST API
 *
 * <p>URL Prefix: /steam-energy-api/energy-month-compare-notes
 */
@RestController
@RequestMapping("/steam-energy-api/energy-month-compare-notes")
@RequiredArgsConstructor
public class SteamEnergyMonthCompareNoteController {

    private final SteamEnergyMonthCompareNoteService service;

    /** 연도 전체 (연도 행 1개) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SteamEnergyMonthCompareNoteResponse>>> getList(@RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.success(service.getList(year)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamEnergyMonthCompareNoteResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** 연도 업서트 — 행 전체 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamEnergyMonthCompareNoteResponse>> save(@Valid @RequestBody SteamEnergyMonthCompareNoteSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(service.save(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
