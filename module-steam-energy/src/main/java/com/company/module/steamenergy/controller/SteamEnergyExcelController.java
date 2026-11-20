package com.company.module.steamenergy.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamEnergyExcelImportResponse;
import com.company.module.steamenergy.legacy.service.EnergyAccountingPlanWorkbookService;
import com.company.module.steamenergy.legacy.service.EnergyAllWorkbookService;
import com.company.module.steamenergy.legacy.service.EnergyFuelPlanWorkbookService;
import com.company.module.steamenergy.legacy.service.EnergyFuelRatioWorkbookService;
import com.company.module.steamenergy.legacy.service.EnergyMonthCompareWorkbookService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * 에너지 회계비용 엑셀 가져오기/내보내기 (워크북 "6. {연도}년 에너지 회계비용 실적.xlsx")
 *
 * <p>URL Prefix: /steam-energy-api/energy-excel/{page}
 * <p>page: accounting-plan(회계비용 계획) · fuel-plan(연료비 사업계획) · fuel-ratio(연료비 배분비율) · month-compare(마감비교) ·
 * all(연료비 요약 — 네 시트를 한 파일로). 저장소는 규격 표 (EnergyCellBridge). 내보내기는 파일 자체를 돌려주므로 ApiResponse 로 감싸지 않는다.
 */
@RestController
@RequestMapping("/steam-energy-api/energy-excel")
@RequiredArgsConstructor
public class SteamEnergyExcelController {

    private final EnergyAccountingPlanWorkbookService accountingPlan;
    private final EnergyFuelPlanWorkbookService fuelPlan;
    private final EnergyFuelRatioWorkbookService fuelRatio;
    private final EnergyMonthCompareWorkbookService monthCompare;
    private final EnergyAllWorkbookService all;

    @PostMapping("/{page}/import-excel")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamEnergyExcelImportResponse>> importExcel(
            @PathVariable String page, @RequestParam("file") MultipartFile file, @RequestParam("year") int year) {
        requireYear(year);
        String name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".xlsm"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일(.xls/.xlsx)만 올릴 수 있습니다.");
        }
        try {
            Map<String, Object> result = switch (page) {
                case "accounting-plan" -> accountingPlan.importWorkbook(file, year);
                case "fuel-plan" -> fuelPlan.importWorkbook(file, year);
                case "fuel-ratio" -> fuelRatio.importWorkbook(file, year);
                case "month-compare" -> monthCompare.importWorkbook(file, year);
                case "all" -> all.importWorkbook(file, year);
                default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 화면입니다: " + page);
            };
            int imported = result.get("imported") instanceof Number n ? n.intValue() : 0;
            return ResponseEntity.ok(ApiResponse.success(SteamEnergyExcelImportResponse.of(page, year, imported)));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "에너지 회계비용 엑셀을 읽을 수 없습니다: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    @GetMapping("/{page}/export-excel")
    public ResponseEntity<ByteArrayResource> exportExcel(@PathVariable String page, @RequestParam("year") int year) {
        requireYear(year);
        try {
            byte[] bytes = switch (page) {
                case "accounting-plan" -> accountingPlan.exportWorkbook(year);
                case "fuel-plan" -> fuelPlan.exportWorkbook(year);
                case "fuel-ratio" -> fuelRatio.exportWorkbook(year);
                case "month-compare" -> monthCompare.exportWorkbook(year);
                case "all" -> all.exportWorkbook(year);
                default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 화면입니다: " + page);
            };
            String fileName = "6. " + year + "년 에너지 회계비용 실적.xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(fileName, StandardCharsets.UTF_8).build().toString())
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(bytes.length)
                    .body(new ByteArrayResource(bytes));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "에너지 회계비용 서식을 열 수 없습니다: " + e.getMessage());
        }
    }

    private static void requireYear(int year) {
        if (year < 2000 || year > 2100) throw new BusinessException(ErrorCode.INVALID_INPUT, "연도가 올바르지 않습니다: " + year);
    }
}
