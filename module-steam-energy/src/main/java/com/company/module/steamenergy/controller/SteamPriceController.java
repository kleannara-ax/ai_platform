package com.company.module.steamenergy.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.module.steamenergy.dto.SteamPriceResponse;
import com.company.module.steamenergy.dto.SteamPriceSaveRequest;
import com.company.module.steamenergy.service.SteamPriceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 시설별 단가 REST API
 *
 * <p>URL Prefix: /steam-energy-api/prices
 */
@RestController
@RequestMapping("/steam-energy-api/prices")
@RequiredArgsConstructor
public class SteamPriceController {

    private final SteamPriceService priceService;

    /** 연/월 페이징 조회 (month 미지정 시 연도 전체 페이징) */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SteamPriceResponse>>> getList(
            @RequestParam int year,
            @RequestParam(required = false) Integer month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(priceService.getList(year, month, pageable))));
    }

    /** 연도 전체 단가 (관리자 그리드 로드용, 비페이징) */
    @GetMapping("/year/{year}")
    public ResponseEntity<ApiResponse<List<SteamPriceResponse>>> getYear(@PathVariable int year) {
        return ResponseEntity.ok(ApiResponse.success(priceService.getYear(year)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SteamPriceResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(priceService.get(id)));
    }

    /** (연도+월+항목코드) 업서트 */
    @PostMapping
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamPriceResponse>> save(
            @Valid @RequestBody SteamPriceSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.created(priceService.save(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamPriceResponse>> update(
            @PathVariable Long id, @Valid @RequestBody SteamPriceSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(priceService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        priceService.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
