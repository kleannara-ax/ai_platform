package com.company.module.safety.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.safety.dto.response.ExcelImportResultResponse;
import com.company.module.safety.dto.response.ExcelSheetPreviewResponse;
import com.company.module.safety.service.SafetyExcelUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 안전작업방식 매뉴얼 엑셀 일괄업로드 REST API (SAFETY 관리자만).
 *
 * <p>사용자 요구사항: "시트의 형식 확인한 후 업로드" — 반드시 2단계로 나눠 호출한다.
 * <ol>
 *   <li>{@code POST /safety-api/excel-upload/preview} — 파일만 보내 시트별 형식 확인 결과를 받는다.
 *       (DB 변경 없음, "초지" 같은 개요/범례 시트는 자동으로 recognized=false 처리됨)</li>
 *   <li>{@code POST /safety-api/excel-upload/confirm} — 사용자가 화면에서 확인 후,
 *       같은 파일 + 등록할 분류(categoryId) + 선택한 시트명 목록을 보내 실제로 매뉴얼을 생성한다.</li>
 * </ol>
 */
@RestController
@RequiredArgsConstructor
public class SafetyExcelUploadController {

    private final SafetyExcelUploadService excelUploadService;

    /** 1단계: 형식 확인 / 미리보기 */
    @PostMapping("/safety-api/excel-upload/preview")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<List<ExcelSheetPreviewResponse>>> preview(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(excelUploadService.preview(file)));
    }

    /** 2단계: 확정 업로드 (선택된 시트만 실제 저장) */
    @PostMapping("/safety-api/excel-upload/confirm")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<ExcelImportResultResponse>> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam("sheetNames") String sheetNamesCsv,
            Authentication authentication) {
        String createdBy = (authentication != null) ? authentication.getName() : null;
        Set<String> selected = Arrays.stream(sheetNamesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return ResponseEntity.ok(ApiResponse.created(
                excelUploadService.confirmImport(file, categoryId, selected, createdBy)));
    }
}
