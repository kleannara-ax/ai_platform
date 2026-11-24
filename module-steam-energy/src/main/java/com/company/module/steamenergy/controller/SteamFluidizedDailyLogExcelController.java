package com.company.module.steamenergy.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamDailyLogExcelImportResponse;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogExportService;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogImportService;
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

/**
 * 유동상 운전일지 엑셀 가져오기/내보내기 (월 단위 통합본, 시트 "1"~"31" + "함수율")
 *
 * <p>URL Prefix: /steam-energy-api/fluidized-daily-log-excel
 * <p>저장소는 규격 표 steam_fluidized_daily_log + steam_fluidized_detail_daily + steam_fuel_daily_usage(스팀 송기량).
 * 가져오기는 값이 있는 칸만 덮어쓴다. 내보내기는 파일 자체를 돌려주므로 ApiResponse 로 감싸지 않는다.
 */
@RestController
@RequestMapping("/steam-energy-api/fluidized-daily-log-excel")
@RequiredArgsConstructor
public class SteamFluidizedDailyLogExcelController {

    private final FluidizedDailyLogImportService importService;
    private final FluidizedDailyLogExportService exportService;

    @PostMapping("/import-excel")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamDailyLogExcelImportResponse>> importExcel(
            @RequestParam("file") MultipartFile file, @RequestParam("year") int year, @RequestParam("month") int month) {
        requirePeriod(year, month);
        String name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xlsx") || name.endsWith(".xlsm") || name.endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일(.xlsx, .xls)만 올릴 수 있습니다.");
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    SteamDailyLogExcelImportResponse.of(year, month, importService.importWorkbook(file, year, month))));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유동상 운전일지 엑셀 파일을 읽을 수 없습니다.");
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    @GetMapping("/export-excel")
    public ResponseEntity<ByteArrayResource> exportExcel(@RequestParam("year") int year, @RequestParam("month") int month) {
        requirePeriod(year, month);
        try {
            byte[] bytes = exportService.exportWorkbook(year, month);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(exportService.fileName(year, month), StandardCharsets.UTF_8).build().toString())
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(bytes.length)
                    .body(new ByteArrayResource(bytes));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage() == null ? "엑셀을 만들지 못했습니다." : e.getMessage());
        }
    }

    private static void requirePeriod(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연/월이 올바르지 않습니다.");
        }
    }
}
