package com.company.module.steamenergy.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamMovePlanImportResponse;
import com.company.module.steamenergy.service.SteamMovePlanExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/**
 * 이동계획 수립 엑셀 가져오기 — 생산계획 파일("생산계획" 시트)
 *
 * <p>URL Prefix: /steam-energy-api/move-plan-excel
 * <p>호기별 생산량·가동시간을 steam_move_plan_machine 에 넣는다. 파일에 값이 있는 달만 바꾼다.
 */
@RestController
@RequestMapping("/steam-energy-api/move-plan-excel")
@RequiredArgsConstructor
public class SteamMovePlanExcelController {

    private final SteamMovePlanExcelService excelService;

    /**
     * @param year 머리글에 연도가 한 칸도 없을 때 쓰는 연도 (화면에서 고른 연도). "25년 12월" 처럼 연도가 적힌 칸이 있으면 그 값이 우선한다.
     */
    @PostMapping("/import-production-plan")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamMovePlanImportResponse>> importProductionPlan(
            @RequestParam("file") MultipartFile file, @RequestParam("year") int year) {
        if (year < 2000 || year > 2100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연도가 올바르지 않습니다: " + year);
        }
        String name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xlsx") || name.endsWith(".xlsm") || name.endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일(.xlsx, .xls)만 올릴 수 있습니다.");
        }
        return ResponseEntity.ok(ApiResponse.success(excelService.importProductionPlan(file, year)));
    }
}
