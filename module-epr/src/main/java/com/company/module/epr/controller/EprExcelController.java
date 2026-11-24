package com.company.module.epr.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.epr.dto.UploadSummary;
import com.company.module.epr.service.EprWorkbookService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/** 엑셀 양식 내려받기 · 마스터/포장중량 엑셀 올리기 */
@RestController
@RequestMapping("/epr-api/excel")
@RequiredArgsConstructor
@PreAuthorize(EprAccess.HAS_MENU)
public class EprExcelController {

    private final EprWorkbookService workbookService;

    /** GET /epr-api/excel/templates/master?includeData=false — 빈 양식(예시 1행) 또는 현재 전체 마스터 */
    @GetMapping("/templates/master")
    public ResponseEntity<ByteArrayResource> masterTemplate(@RequestParam(defaultValue = "false") boolean includeData) {
        return EprAccess.xlsx(workbookService.masterTemplate(includeData), "EPR_제품마스터_" + LocalDate.now() + ".xlsx");
    }

    /** GET /epr-api/excel/templates/weight — 현재 SKU 와 중량이 채워진 포장중량 양식 */
    @GetMapping("/templates/weight")
    public ResponseEntity<ByteArrayResource> weightTemplate() {
        return EprAccess.xlsx(workbookService.weightTemplate(), "EPR_포장중량_업데이트_" + LocalDate.now() + ".xlsx");
    }

    /** GET /epr-api/excel/templates/sales — 판매수량 양식 */
    @GetMapping("/templates/sales")
    public ResponseEntity<ByteArrayResource> salesTemplate() {
        return EprAccess.xlsx(workbookService.salesTemplate(), "EPR_판매수량_양식.xlsx");
    }

    /** POST /epr-api/excel/uploads/master — 제품 마스터 엑셀 일괄 등록·갱신 */
    @PostMapping(value = "/uploads/master", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadSummary>> uploadMaster(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(workbookService.uploadMaster(file, userId)));
    }

    /** POST /epr-api/excel/uploads/weight — 제품코드 기준 포장중량만 갱신 */
    @PostMapping(value = "/uploads/weight", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadSummary>> uploadWeights(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(workbookService.uploadWeights(file, userId)));
    }
}
