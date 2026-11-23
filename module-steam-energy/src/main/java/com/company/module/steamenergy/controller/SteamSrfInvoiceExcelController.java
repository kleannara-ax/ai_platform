package com.company.module.steamenergy.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamSrfInvoiceExcelImportResponse;
import com.company.module.steamenergy.legacy.service.SrfInvoiceExcelExportService;
import com.company.module.steamenergy.legacy.service.SrfInvoiceExcelImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * SRF 보일러 인보이스 통합본(KleanNara SRF Boiler AOT invoice(N월).xls) 엑셀 가져오기/내보내기
 *
 * <p>URL Prefix: /steam-energy-api/srf-invoice-excel
 * <p>저장소는 규격 표 steam_srf_invoice_* 7개와 steam_incident(사고이력). 가져오기는 엑셀의 인보이스 기간
 * (Summary 의 Invoice for the period)으로 넣고, year · month 를 함께 보내면 그 기간과 같은지 확인한다.
 * 내보내기는 파일 자체를 돌려주므로 ApiResponse 로 감싸지 않는다.
 */
@RestController
@RequestMapping("/steam-energy-api/srf-invoice-excel")
@RequiredArgsConstructor
public class SteamSrfInvoiceExcelController {

    private final SrfInvoiceExcelImportService importService;
    private final SrfInvoiceExcelExportService exportService;

    @PostMapping("/import-excel")
    @PreAuthorize("@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')")
    public ResponseEntity<ApiResponse<SteamSrfInvoiceExcelImportResponse>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month) {
        if (year != null || month != null) requirePeriod(year == null ? 2000 : year, month == null ? 1 : month);
        String name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (file == null || file.isEmpty() || !(name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".xlsm"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일(.xls, .xlsx)만 올릴 수 있습니다.");
        }
        try (InputStream input = file.getInputStream()) {
            return ResponseEntity.ok(ApiResponse.success(importService.importWorkbook(input, year, month)));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "SRF 보일러 인보이스 엑셀 파일을 읽을 수 없습니다.");
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
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
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
