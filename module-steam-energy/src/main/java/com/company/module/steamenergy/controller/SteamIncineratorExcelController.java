package com.company.module.steamenergy.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamIncineratorExcelImportResponse;
import com.company.module.steamenergy.legacy.service.IncineratorWorkbookService;
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
 * 소각로 위탁운영비 엑셀 가져오기/내보내기 (워크북 "5. 소각로 위탁운영비({연도}년{월}월).xlsx", 시트 6장)
 *
 * <p>URL Prefix: /steam-energy-api/incinerator-excel
 * <p>가져오기는 고른 달의 도급내역, 그 해의 사업계획대비 표·가동실적·총 운영비용·사고이력을 파일 기준으로 바꾼다.
 * 다른 달의 도급내역은 건드리지 않는다. 내보내기는 파일 자체를 돌려주므로 ApiResponse 로 감싸지 않는다.
 */
@RestController
@RequestMapping("/steam-energy-api/incinerator-excel")
@RequiredArgsConstructor
public class SteamIncineratorExcelController {

    private final IncineratorWorkbookService workbookService;

    @PostMapping("/import-excel")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamIncineratorExcelImportResponse>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam(value = "month", defaultValue = "1") int month) {
        requirePeriod(year, month);
        String name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".xlsm"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일(.xls/.xlsx)만 올릴 수 있습니다.");
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    SteamIncineratorExcelImportResponse.of(year, month, workbookService.importWorkbook(file, year, month))));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "소각로 위탁운영 엑셀 파일을 읽을 수 없습니다: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    @GetMapping("/export-excel")
    public ResponseEntity<ByteArrayResource> exportExcel(@RequestParam("year") int year,
                                                         @RequestParam(value = "month", defaultValue = "1") int month) {
        requirePeriod(year, month);
        try {
            byte[] bytes = workbookService.exportWorkbook(year, month);
            String fileName = "5. 소각로 위탁운영비(" + year + "년" + String.format("%02d", month) + "월).xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(fileName, StandardCharsets.UTF_8).build().toString())
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(bytes.length)
                    .body(new ByteArrayResource(bytes));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "소각로 위탁운영비 서식을 열 수 없습니다: " + e.getMessage());
        }
    }

    private static void requirePeriod(int year, int month) {
        if (year < 2000 || year > 2100) throw new BusinessException(ErrorCode.INVALID_INPUT, "연도가 올바르지 않습니다: " + year);
        if (month < 1 || month > 12) throw new BusinessException(ErrorCode.INVALID_INPUT, "월이 올바르지 않습니다: " + month);
    }
}
