package com.company.module.steamenergy.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.steamenergy.dto.SteamFluidizedExcelImportResponse;
import com.company.module.steamenergy.legacy.service.FluidizedSummaryWorkbookService;
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
 * 유동상 운영내역 엑셀 가져오기/내보내기
 *
 * <p>URL Prefix: /steam-energy-api/fluidized-excel
 * <ul>
 *   <li>import-excel · export-excel — 전체 워크북 (사업계획대비 · 도급비용 · 세부 운영내역 · SRF 입고 · 설비개선 · 사고이력)</li>
 *   <li>import-compare-excel · export-compare-excel — 실적비교 한 장</li>
 *   <li>import-contract-excel · export-contract-excel — 도급비용 한 장</li>
 * </ul>
 * 저장소는 규격 표 (FluidizedCellStore · FluidizedDetailCellStore · RecordCellStore).
 * 내보내기는 파일 자체를 돌려주므로 ApiResponse 로 감싸지 않는다.
 */
@RestController
@RequestMapping("/steam-energy-api/fluidized-excel")
@RequiredArgsConstructor
public class SteamFluidizedExcelController {

    private static final String WRITE = "@coreMenuService.hasMenuAccessByAuth(authentication.authorities, 'STEAM_ENERGY_MGMT')";
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final FluidizedSummaryWorkbookService workbookService;

    @PostMapping("/import-excel")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<SteamFluidizedExcelImportResponse>> importExcel(
            @RequestParam("file") MultipartFile file, @RequestParam("year") int year, @RequestParam("month") int month) {
        requirePeriod(year, month);
        requireExcel(file);
        return imported(year, month, "유동상 운영내역 엑셀 파일을 읽을 수 없습니다.", () -> workbookService.importWorkbook(file, year, month));
    }

    @GetMapping("/export-excel")
    public ResponseEntity<ByteArrayResource> exportExcel(@RequestParam("year") int year, @RequestParam("month") int month) {
        requirePeriod(year, month);
        return file("유동상 운영내역(" + year + "년" + String.format("%02d", month) + "월).xlsx", () -> workbookService.exportWorkbook(year, month));
    }

    @PostMapping("/import-compare-excel")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<SteamFluidizedExcelImportResponse>> importCompareExcel(
            @RequestParam("file") MultipartFile file, @RequestParam("year") int year) {
        requireYear(year);
        requireExcel(file);
        return imported(year, null, "실적비교 엑셀 파일을 읽을 수 없습니다.", () -> workbookService.importCompareWorkbook(file, year));
    }

    @GetMapping("/export-compare-excel")
    public ResponseEntity<ByteArrayResource> exportCompareExcel(@RequestParam("year") int year) {
        requireYear(year);
        return file("유동상 실적비교(" + year + "년).xlsx", () -> workbookService.exportCompareWorkbook(year));
    }

    @PostMapping("/import-contract-excel")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<SteamFluidizedExcelImportResponse>> importContractExcel(
            @RequestParam("file") MultipartFile file, @RequestParam("year") int year, @RequestParam("month") int month) {
        requirePeriod(year, month);
        requireExcel(file);
        return imported(year, month, "도급비용 엑셀 파일을 읽을 수 없습니다.", () -> workbookService.importContractWorkbook(file, year, month));
    }

    @GetMapping("/export-contract-excel")
    public ResponseEntity<ByteArrayResource> exportContractExcel(@RequestParam("year") int year, @RequestParam("month") int month) {
        requirePeriod(year, month);
        return file("유동상 도급비용(" + year + "년" + String.format("%02d", month) + "월).xlsx", () -> workbookService.exportContractWorkbook(year, month));
    }

    private interface Import { Map<String, Object> run() throws IOException; }

    private interface Export { byte[] run() throws IOException; }

    private static ResponseEntity<ApiResponse<SteamFluidizedExcelImportResponse>> imported(int year, Integer month, String ioMessage, Import job) {
        try {
            return ResponseEntity.ok(ApiResponse.success(SteamFluidizedExcelImportResponse.of(year, month, job.run())));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, ioMessage);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    private static ResponseEntity<ByteArrayResource> file(String fileName, Export job) {
        try {
            byte[] bytes = job.run();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                    .contentType(XLSX).contentLength(bytes.length).body(new ByteArrayResource(bytes));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀을 만들지 못했습니다: " + e.getMessage());
        }
    }

    private static void requireExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일을 선택해주세요.");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xlsx") || name.endsWith(".xlsm") || name.endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드 가능한 파일은 .xls, .xlsx, .xlsm 형식입니다.");
        }
    }

    private static void requireYear(int year) {
        if (year < 2000 || year > 2100) throw new BusinessException(ErrorCode.INVALID_INPUT, "연도가 올바르지 않습니다: " + year);
    }

    private static void requirePeriod(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) throw new BusinessException(ErrorCode.INVALID_INPUT, "연/월이 올바르지 않습니다.");
    }
}
