package com.company.module.steamenergy.legacy.web;

import com.company.module.steamenergy.legacy.db.TableService;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogExportService;
import com.company.module.steamenergy.legacy.service.FluidizedDailyLogImportService;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogExportService;
import com.company.module.steamenergy.legacy.service.WasteIncineratorLogImportService;
import com.company.module.steamenergy.legacy.service.FluidizedDetailWorkbookService;
import com.company.module.steamenergy.legacy.service.FluidizedSummaryWorkbookService;
import com.company.module.steamenergy.legacy.service.SrfBoilerInvoiceWorkbookService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/steam/api/tables")
public class TableController {
    private final TableService tableService;
    private final FluidizedDetailWorkbookService fluidizedDetailWorkbookService;
    private final FluidizedDailyLogImportService fluidizedDailyLogImportService;
    private final WasteIncineratorLogImportService wasteIncineratorLogImportService;
    private final WasteIncineratorLogExportService wasteIncineratorLogExportService;
    private final FluidizedDailyLogExportService fluidizedDailyLogExportService;
    private final FluidizedSummaryWorkbookService fluidizedSummaryWorkbookService;
    private final SrfBoilerInvoiceWorkbookService srfBoilerInvoiceWorkbookService;

    public TableController(
            TableService tableService,
            FluidizedDetailWorkbookService fluidizedDetailWorkbookService,
            FluidizedDailyLogImportService fluidizedDailyLogImportService,
            WasteIncineratorLogImportService wasteIncineratorLogImportService,
            WasteIncineratorLogExportService wasteIncineratorLogExportService,
            FluidizedDailyLogExportService fluidizedDailyLogExportService,
            FluidizedSummaryWorkbookService fluidizedSummaryWorkbookService,
            SrfBoilerInvoiceWorkbookService srfBoilerInvoiceWorkbookService
    ) {
        this.tableService = tableService;
        this.fluidizedDetailWorkbookService = fluidizedDetailWorkbookService;
        this.fluidizedDailyLogImportService = fluidizedDailyLogImportService;
        this.wasteIncineratorLogImportService = wasteIncineratorLogImportService;
        this.wasteIncineratorLogExportService = wasteIncineratorLogExportService;
        this.fluidizedDailyLogExportService = fluidizedDailyLogExportService;
        this.fluidizedSummaryWorkbookService = fluidizedSummaryWorkbookService;
        this.srfBoilerInvoiceWorkbookService = srfBoilerInvoiceWorkbookService;
    }

    @GetMapping("/{tableName}")
    public Map<String, Object> list(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String fromMonth,
            @RequestParam(required = false) String toMonth,
            @RequestParam(value = "tableNames", required = false) String tableNames
    ) {
        ensureTable(tableName);
        return tableService.list(tableName, Math.max(page, 1), Math.max(limit, 1), month, fromMonth, toMonth, tableNames);
    }

    @PostMapping("/{tableName}")
    public Map<String, Object> upsert(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> payload
    ) {
        ensureTable(tableName);
        Map<String, Object> saved = tableService.upsert(tableName, payload);
        // 데이터 변경 → fluidized-detail rendered 캐시 무효화 (해당 월만)
        Object month = payload.get("month");
        if (month instanceof String monthStr) fluidizedDetailWorkbookService.invalidateRenderCache(monthStr);
        return saved;
    }

    @PatchMapping("/{tableName}/{id}")
    public Map<String, Object> patch(
            @PathVariable String tableName,
            @PathVariable long id,
            @RequestBody Map<String, Object> payload
    ) {
        ensureTable(tableName);
        Map<String, Object> saved = tableService.patch(tableName, id, payload);
        Object month = payload.get("month");
        if (month instanceof String monthStr) fluidizedDetailWorkbookService.invalidateRenderCache(monthStr);
        return saved;
    }

    @DeleteMapping("/{tableName}/{id}")
    public Map<String, Object> deleteById(
            @PathVariable String tableName,
            @PathVariable long id
    ) {
        ensureTable(tableName);
        int affected = tableService.deleteById(tableName, id);
        return Map.of("ok", true, "deleted", affected);
    }

    @DeleteMapping("/steam_cell_value/row-group")
    public Map<String, Object> deleteTableCellRowGroup(
            @RequestParam("month") String month,
            @RequestParam("rowKey") String rowKey
    ) {
        if (month == null || month.isBlank() || rowKey == null || rowKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month and rowKey are required");
        }
        Map<String, Object> result = tableService.deleteTableCellRowGroup(month, rowKey);
        fluidizedDetailWorkbookService.invalidateRenderCache(month);
        return result;
    }

    @DeleteMapping("/steam_cell_value/by-table")
    public Map<String, Object> deleteTableCellByTable(
            @RequestParam("month") String month,
            @RequestParam("tableName") String tableName
    ) {
        if (month == null || month.isBlank() || tableName == null || tableName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month and tableName are required");
        }
        int affected = tableService.deleteTableCellValuesByTables(month, java.util.List.of(tableName));
        fluidizedDetailWorkbookService.invalidateRenderCache(month);
        return Map.of("ok", true, "deleted", affected);
    }

    // 원단위 일지 엑셀(/unit-usage/import-excel · export-excel)은 2026-11 규격 전환으로
    // /steam-energy-api/unit-log/import-excel · export-excel (SteamUnitLogExcelController) 로 옮겼다.

    @GetMapping("/server-date")
    public Map<String, Object> serverDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return Map.of(
                "date", today.toString(),
                "year", today.getYear(),
                "month", String.format("%02d", today.getMonthValue()),
                "day", String.format("%02d", today.getDayOfMonth())
        );
    }

    @GetMapping("/fluidized-detail/rendered")
    public Map<String, Object> renderedFluidizedDetail(
            @RequestParam("month") String month,
            @RequestParam(value = "view", required = false) String view
    ) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month");
        }
        return fluidizedDetailWorkbookService.renderMonth(month, view);
    }

    @GetMapping("/fluidized-summary/detail-actuals")
    public Map<String, Object> fluidizedSummaryDetailActuals(@RequestParam("year") int year) {
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid year");
        }
        return fluidizedSummaryWorkbookService.detailActuals(year);
    }

    @GetMapping("/fluidized-daily-log/export-excel")
    public ResponseEntity<ByteArrayResource> exportFluidizedDailyLogExcel(
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\uC5F0/\uC6D4\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4.");
        }
        try {
            byte[] body = fluidizedDailyLogExportService.exportWorkbook(year, month);
            String fileName = fluidizedDailyLogExportService.fileName(year, month);
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(body.length)
                    .body(new ByteArrayResource(body));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    exception.getMessage() == null ? "\uC5D1\uC140\uC744 \uB9CC\uB4E4\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4." : exception.getMessage(), exception);
        }
    }

    @PostMapping("/fluidized-daily-log/import-excel")
    public Map<String, Object> importFluidizedDailyLogExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        ensureExcelFile(file, true);
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "연/월이 올바르지 않습니다.");
        }
        try {
            return fluidizedDailyLogImportService.importWorkbook(file, year, month);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유동상 운전일지 엑셀 파일을 읽을 수 없습니다.", exception);
        }
    }

    @GetMapping("/waste-incinerator-log/export-excel")
    public ResponseEntity<ByteArrayResource> exportWasteIncineratorLogExcel(
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "연/월이 올바르지 않습니다.");
        }
        try {
            byte[] body = wasteIncineratorLogExportService.exportWorkbook(year, month);
            String fileName = wasteIncineratorLogExportService.fileName(year, month);
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(body.length)
                    .body(new ByteArrayResource(body));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    exception.getMessage() == null ? "엑셀을 만들지 못했습니다." : exception.getMessage(), exception);
        }
    }

    @PostMapping("/waste-incinerator-log/import-excel")
    public Map<String, Object> importWasteIncineratorLogExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        ensureExcelFile(file, true);
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "연/월이 올바르지 않습니다.");
        }
        try {
            return wasteIncineratorLogImportService.importWorkbook(file, year, month);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "폐합성소각로 운전일지 엑셀 파일을 읽을 수 없습니다.", exception);
        }
    }

    @PostMapping("/fluidized-summary/import-excel")
    public Map<String, Object> importFluidizedSummaryExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        ensureExcelFile(file, true);
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\uC5F0/\uC6D4\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4.");
        }
        try {
            return fluidizedSummaryWorkbookService.importWorkbook(file, year, month);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\uC720\uB3D9\uC0C1 \uC6B4\uC601\uB0B4\uC5ED \uC5D1\uC140 \uD30C\uC77C\uC744 \uC77D\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.", exception);
        }
    }

    @GetMapping("/fluidized-summary/export-excel")
    public ResponseEntity<ByteArrayResource> exportFluidizedSummaryExcel(
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid period");
        }
        try {
            byte[] content = fluidizedSummaryWorkbookService.exportWorkbook(year, month);
            ByteArrayResource resource = new ByteArrayResource(content);
            String filename = "\uC720\uB3D9\uC0C1 \uC6B4\uC601\uB0B4\uC5ED(" + year + "\uB144" + String.format("%02d", month) + "\uC6D4).xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, attachmentFilename(filename, "fluidized_summary_" + year + "_" + String.format("%02d", month) + ".xlsx"))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(content.length)
                    .body(resource);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to export fluidized summary Excel file", exception);
        }
    }

    @PostMapping("/fluidized-summary/import-compare-excel")
    public Map<String, Object> importFluidizedCompareExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year
    ) {
        ensureExcelFile(file, true);
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "연도가 올바르지 않습니다.");
        }
        try {
            return fluidizedSummaryWorkbookService.importCompareWorkbook(file, year);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "실적비교 엑셀 파일을 읽을 수 없습니다.", exception);
        }
    }

    @GetMapping("/fluidized-summary/export-compare-excel")
    public ResponseEntity<ByteArrayResource> exportFluidizedCompareExcel(@RequestParam("year") int year) {
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid year");
        }
        try {
            byte[] content = fluidizedSummaryWorkbookService.exportCompareWorkbook(year);
            ByteArrayResource resource = new ByteArrayResource(content);
            String filename = "유동상 실적비교(" + year + "년).xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, attachmentFilename(filename, "fluidized_compare_" + year + ".xlsx"))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(content.length)
                    .body(resource);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to export fluidized compare Excel file", exception);
        }
    }

    @PostMapping("/fluidized-summary/import-contract-excel")
    public Map<String, Object> importFluidizedContractExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        ensureExcelFile(file, true);
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "연/월이 올바르지 않습니다.");
        }
        try {
            return fluidizedSummaryWorkbookService.importContractWorkbook(file, year, month);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "도급비용 엑셀 파일을 읽을 수 없습니다.", exception);
        }
    }

    @GetMapping("/fluidized-summary/export-contract-excel")
    public ResponseEntity<ByteArrayResource> exportFluidizedContractExcel(
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid period");
        }
        try {
            byte[] content = fluidizedSummaryWorkbookService.exportContractWorkbook(year, month);
            ByteArrayResource resource = new ByteArrayResource(content);
            String filename = "유동상 도급비용(" + year + "년" + String.format("%02d", month) + "월).xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, attachmentFilename(filename, "fluidized_contract_" + year + "_" + String.format("%02d", month) + ".xlsx"))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(content.length)
                    .body(resource);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to export fluidized contract Excel file", exception);
        }
    }

    // 복합보일러 엑셀: /steam-energy-api/combo-excel 로 옮김 (SteamComboExcelController, 2026-11 규격 전환)

    // /combo-boiler/validate-excel \uC81C\uAC70\uB428 \u2014 \uD074\uB77C\uC774\uC5B8\uD2B8\uC5D0\uC11C \uC0AC\uC6A9 \uC548 \uB418\uB294 \uB370\uB4DC \uC5D4\uB4DC\uD3EC\uC778\uD2B8\uC600\uACE0,
    // \uB0B4\uBD80 \uAD6C\uD604\uC774 Excel \uC744 \uB2E4\uC2DC \uC5F4\uC5B4 \uD30C\uC2F1\uD558\uBBC0\uB85C Excel \uC811\uADFC \uC815\uCC45\uC5D0 \uC704\uBC30\uB428.

@GetMapping("/srf-boiler-invoice/summary")
    public Map<String, Object> renderSrfBoilerInvoiceSummary(
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid period");
        }
        try {
            return srfBoilerInvoiceWorkbookService.renderSummary(year, month);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/srf-boiler-invoice/operation")
    public Map<String, Object> renderSrfBoilerInvoiceOperation(
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid period");
        }
        try {
            return srfBoilerInvoiceWorkbookService.renderOperation(year, month);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    // 소각로 위탁운영비 엑셀: /steam-energy-api/incinerator-excel 로 옮김 (SteamIncineratorExcelController, 2026-11 규격 전환)

    /* ── 에너지 회계비용 계획 ─────────────────────────────── */

    // 에너지 회계비용 엑셀(/energy-*/import|export-excel)은 2026-11 규격 전환으로 /steam-energy-api/energy-excel/{page}/… (SteamEnergyExcelController) 로 옮겼다.

    private void ensureTable(String tableName) {
        if (!tableService.supports(tableName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown table");
        }
    }

    private void ensureExcelFile(MultipartFile file, boolean allowXls) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\uC5D1\uC140 \uD30C\uC77C\uC744 \uC120\uD0DD\uD574\uC8FC\uC138\uC694.");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean valid = filename.endsWith(".xlsx") || filename.endsWith(".xlsm") || (allowXls && filename.endsWith(".xls"));
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, allowXls
                    ? "\uC5C5\uB85C\uB4DC \uAC00\uB2A5\uD55C \uD30C\uC77C\uC740 .xls, .xlsx, .xlsm \uD615\uC2DD\uC785\uB2C8\uB2E4."
                    : "\uC5C5\uB85C\uB4DC \uAC00\uB2A5\uD55C \uD30C\uC77C\uC740 .xlsx, .xlsm \uD615\uC2DD\uC785\uB2C8\uB2E4.");
        }
    }

    private String attachmentFilename(String filename, String fallbackFilename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + fallbackFilename + "\"; filename*=UTF-8''" + encoded;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException exception) {
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? exception.getStatusCode().toString()
                : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "ok", false,
                "message", message,
                "detail", message
        ));
    }
}
