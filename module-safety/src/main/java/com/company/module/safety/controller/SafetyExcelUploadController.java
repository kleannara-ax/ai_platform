package com.company.module.safety.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.safety.dto.request.ExcelSheetAssignRequest;
import com.company.module.safety.dto.response.ExcelImportResultResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;

/**
 * 안전작업방식 매뉴얼 엑셀 일괄업로드 REST API (SAFETY 관리자만).
 *
 * <p>사용자 요구사항: "시트의 형식 확인한 후 업로드" — 반드시 2단계로 나눠 호출한다.
 * <ol>
 *   <li>{@code POST /safety-api/excel-upload/preview} — 파일만 보내 시트별 형식 확인 결과를 받는다.
 *       (DB 변경 없음, "초지" 같은 개요/범례 시트는 자동으로 recognized=false 처리됨)</li>
 *   <li>{@code POST /safety-api/excel-upload/confirm} — 사용자가 화면에서 확인 후,
 *       같은 파일 + 시트별 등록 분류 목록(assignments)을 보내 실제로 매뉴얼을 생성한다.
 *       시트마다 다른 분류를 지정할 수 있고, 목록에 없는 시트는 가져오지 않는다.</li>
 * </ol>
 */
@RestController
@RequiredArgsConstructor
public class SafetyExcelUploadController {

    private final SafetyExcelUploadService excelUploadService;
    private final ObjectMapper objectMapper;

    /** 1단계: 형식 확인 / 미리보기 */
    @PostMapping("/safety-api/excel-upload/preview")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<List<ExcelSheetPreviewResponse>>> preview(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(excelUploadService.preview(file)));
    }

    /**
     * 2단계: 확정 업로드 (선택된 시트만 실제 저장).
     *
     * <p>{@code assignments} 는 {@code [{"sheetName":"...","categoryId":1}, ...]} 형태의 JSON 문자열이다.
     * 파일과 함께 multipart 로 보내야 해서 본문을 JSON 으로 받을 수 없기 때문에 문자열 파트로 받아 파싱한다.
     * (시트명에 쉼표가 들어갈 수 있어 CSV 대신 JSON 을 쓴다)
     */
    @PostMapping("/safety-api/excel-upload/confirm")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<ExcelImportResultResponse>> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam("assignments") String assignmentsJson,
            Authentication authentication) {
        String createdBy = (authentication != null) ? authentication.getName() : null;
        List<ExcelSheetAssignRequest> assignments = parseAssignments(assignmentsJson);
        return ResponseEntity.ok(ApiResponse.created(
                excelUploadService.confirmImport(file, assignments, createdBy)));
    }

    private List<ExcelSheetAssignRequest> parseAssignments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ExcelSheetAssignRequest>>() { });
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "시트별 분류 지정 형식이 올바르지 않습니다: " + e.getMessage());
        }
    }
}
