package com.company.module.steamenergy.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamEnergyExcelImportResponse;
import com.company.module.steamenergy.legacy.service.ComboBoilerWorkbookService;
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
 * 복합보일러 엑셀 가져오기/내보내기 (워크북 "3. 복합보일러({연도}년) 스팀구매 현황.xlsx", 시트 스팀생산실적(N월) 12장)
 *
 * <p>URL Prefix: /steam-energy-api/combo-excel
 * <p>저장소는 규격 표 5개 (ComboCellStore). 가져오기는 시트가 있는 달을 파일 기준으로 바꾼다.
 * 내보내기는 파일 자체를 돌려주므로 ApiResponse 로 감싸지 않는다.
 */
@RestController
@RequestMapping("/steam-energy-api/combo-excel")
@RequiredArgsConstructor
public class SteamComboExcelController {

    private final ComboBoilerWorkbookService workbookService;

    @PostMapping("/import-excel")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamEnergyExcelImportResponse>> importExcel(
            @RequestParam("file") MultipartFile file, @RequestParam("year") int year) {
        requireYear(year);
        String name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xlsx") || name.endsWith(".xlsm"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일(.xlsx)만 올릴 수 있습니다.");
        }
        try {
            Map<String, Object> result = workbookService.importWorkbook(file, year);
            int imported = result.get("imported") instanceof Number n ? n.intValue() : 0;
            return ResponseEntity.ok(ApiResponse.success(SteamEnergyExcelImportResponse.of("combo", year, imported)));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "복합보일러 스팀구매 현황 엑셀을 읽을 수 없습니다: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    @GetMapping("/export-excel")
    public ResponseEntity<ByteArrayResource> exportExcel(@RequestParam("year") int year) {
        requireYear(year);
        try {
            byte[] bytes = workbookService.exportWorkbook(year);
            String fileName = "3. 복합보일러(" + year + "년) 스팀구매 현황.xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(fileName, StandardCharsets.UTF_8).build().toString())
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(bytes.length)
                    .body(new ByteArrayResource(bytes));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "복합보일러 서식을 열 수 없습니다: " + e.getMessage());
        }
    }

    private static void requireYear(int year) {
        if (year < 2000 || year > 2100) throw new BusinessException(ErrorCode.INVALID_INPUT, "연도가 올바르지 않습니다: " + year);
    }
}
