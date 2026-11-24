package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamBulkSaveResponse;
import com.company.module.steamenergy.dto.SteamComboRecoveryLossBulkRequest;
import com.company.module.steamenergy.dto.SteamComboRecoveryLossResponse;
import com.company.module.steamenergy.dto.SteamComboRecoveryLossSaveRequest;
import com.company.module.steamenergy.service.SteamComboRecoveryLossService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 복합보일러 스팀 회수 감소 REST API
 *
 * <p>URL Prefix: /steam-energy-api/combo-recovery-losses
 */
@RestController
@RequestMapping("/steam-energy-api/combo-recovery-losses")
@RequiredArgsConstructor
public class SteamComboRecoveryLossController {

    private final SteamComboRecoveryLossService service;

    /** 한 해 (month 를 주면 그 달만) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SteamComboRecoveryLossResponse>>> getList(@RequestParam int year,
                                                                 @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.success(service.getList(year, month)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamComboRecoveryLossResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    /** (YEAR_NO, MONTH_NO, LINE_NO) 업서트 — 행 전체 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamComboRecoveryLossResponse>> save(@Valid @RequestBody SteamComboRecoveryLossSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(service.save(request)));
    }

    /** 범위(한 해 또는 한 달) 일괄 저장 */
    @PutMapping("/bulk")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamBulkSaveResponse>> saveBulk(@Valid @RequestBody SteamComboRecoveryLossBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.saveBulk(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
