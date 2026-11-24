package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.steamenergy.dto.SteamDailyUsageResponse;
import com.company.module.steamenergy.dto.SteamDailyUsageSaveRequest;
import com.company.module.steamenergy.service.SteamDailyUsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 일일 스팀 사용 실적 REST API
 *
 * <p>URL Prefix: /steam-energy-api/daily-usages
 */
@RestController
@RequestMapping("/steam-energy-api/daily-usages")
@RequiredArgsConstructor
public class SteamDailyUsageController {

    private final SteamDailyUsageService usageService;

    /** 기간·설비별 페이징 조회 */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SteamDailyUsageResponse>>> getList(
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(usageService.getList(equipmentId, dateFrom, dateTo, pageable))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamDailyUsageResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(usageService.get(id)));
    }

    /** (일자+설비) 업서트 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamDailyUsageResponse>> save(
            @Valid @RequestBody SteamDailyUsageSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(usageService.save(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        usageService.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
