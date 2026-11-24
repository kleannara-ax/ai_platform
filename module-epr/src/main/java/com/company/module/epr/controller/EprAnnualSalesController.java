package com.company.module.epr.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.epr.dto.AnnualExistsResponse;
import com.company.module.epr.dto.AnnualSalesSummary;
import com.company.module.epr.dto.SalesCalculationResult;
import com.company.module.epr.service.EprAnnualSalesService;
import com.company.module.epr.service.EprWorkbookService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/** 판매수량 계산 · 연도별 실적 (01 탭) */
@RestController
@RequestMapping("/epr-api/annual")
@RequiredArgsConstructor
@PreAuthorize(EprAccess.HAS_MENU)
public class EprAnnualSalesController {

    private final EprAnnualSalesService annualSalesService;
    private final EprWorkbookService workbookService;

    /** POST /epr-api/annual/calculate — 판매수량 엑셀 업로드 → 계산 → 그 연도 실적으로 저장 */
    @PostMapping(value = "/calculate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SalesCalculationResult>> calculate(
            @RequestPart("file") MultipartFile file,
            @RequestParam int year,
            @RequestParam(defaultValue = "false") boolean replaceExisting,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.created(workbookService.calculateSales(file, year, replaceExisting, userId)));
    }

    /** GET /epr-api/annual/years — 실적이 저장된 연도 */
    @GetMapping("/years")
    public ResponseEntity<ApiResponse<List<Integer>>> years() {
        return ResponseEntity.ok(ApiResponse.success(annualSalesService.availableYears()));
    }

    /** GET /epr-api/annual/compare?fromYear=&toYear= — 연도별 판매량·신고중량과 전년 대비 증감 */
    @GetMapping("/compare")
    public ResponseEntity<ApiResponse<List<AnnualSalesSummary>>> compare(
            @RequestParam int fromYear, @RequestParam int toYear) {
        return ResponseEntity.ok(ApiResponse.success(annualSalesService.compare(fromYear, toYear)));
    }

    /** GET /epr-api/annual/{year} — 저장된 연도의 신고중량 · 파트별 집계 · SKU 상세 */
    @GetMapping("/{year}")
    public ResponseEntity<ApiResponse<SalesCalculationResult>> result(@PathVariable int year) {
        return ResponseEntity.ok(ApiResponse.success(annualSalesService.result(year)));
    }

    /** GET /epr-api/annual/{year}/exists — 그 연도 실적이 이미 저장돼 있는지 */
    @GetMapping("/{year}/exists")
    public ResponseEntity<ApiResponse<AnnualExistsResponse>> exists(@PathVariable int year) {
        return ResponseEntity.ok(ApiResponse.success(new AnnualExistsResponse(year, annualSalesService.exists(year))));
    }

    /** GET /epr-api/annual/{year}/report — 결과 리포트 엑셀 (파트별 집계 · SKU 상세 두 시트) */
    @GetMapping("/{year}/report")
    public ResponseEntity<ByteArrayResource> report(@PathVariable int year) {
        return EprAccess.xlsx(workbookService.annualReport(year),
                "EPR_중량신고_결과_" + year + "_" + LocalDate.now() + ".xlsx");
    }
}
