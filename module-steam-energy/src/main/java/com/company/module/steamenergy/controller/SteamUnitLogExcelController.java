package com.company.module.steamenergy.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamUnitLogImportResponse;
import com.company.module.steamenergy.service.SteamUnitLogExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 원단위 일지 누계 엑셀 가져오기/내보내기
 *
 * <p>URL Prefix: /steam-energy-api/unit-log
 * <p>내보내기는 파일 자체를 돌려주므로 ApiResponse 로 감싸지 않는다 (다른 모듈의 파일 응답과 같음).
 */
@RestController
@RequestMapping("/steam-energy-api/unit-log")
@RequiredArgsConstructor
public class SteamUnitLogExcelController {

    private final SteamUnitLogExcelService excelService;

    /** 원단위 일지 누계 파일(N월일지 시트) 가져오기 — 파일이 그 달의 기준이 된다 */
    @PostMapping("/import-excel")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamUnitLogImportResponse>> importExcel(
            @RequestParam("file") MultipartFile file, @RequestParam("year") int year) {
        String name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".xlsm"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일(.xls/.xlsx)만 올릴 수 있습니다.");
        }
        return ResponseEntity.ok(ApiResponse.success(excelService.importWorkbook(file, year)));
    }

    /** 연도 전체를 서식에 채워 내려받기 */
    @GetMapping("/export-excel")
    public ResponseEntity<ByteArrayResource> exportExcel(@RequestParam("year") int year) {
        byte[] bytes = excelService.exportWorkbook(year);
        String fileName = "원단위 일지 누계(" + year + "년).xls";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
}
