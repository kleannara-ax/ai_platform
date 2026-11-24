package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.steamenergy.dto.SteamEquipmentResponse;
import com.company.module.steamenergy.dto.SteamEquipmentSaveRequest;
import com.company.module.steamenergy.service.SteamEquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 스팀 설비 마스터 REST API
 *
 * <p>URL Prefix: /steam-energy-api/equipments
 * <p>응답은 ApiResponse&lt;T&gt;로 감싸서 반환
 */
@RestController
@RequestMapping("/steam-energy-api/equipments")
@RequiredArgsConstructor
public class SteamEquipmentController {

    private final SteamEquipmentService equipmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SteamEquipmentResponse>>> getList(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(equipmentService.getList(q, category, pageable))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamEquipmentResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(equipmentService.get(id)));
    }

    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamEquipmentResponse>> save(
            @Valid @RequestBody SteamEquipmentSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(equipmentService.save(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamEquipmentResponse>> update(
            @PathVariable Long id, @Valid @RequestBody SteamEquipmentSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(equipmentService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        equipmentService.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
