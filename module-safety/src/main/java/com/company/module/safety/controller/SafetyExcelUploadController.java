package com.company.module.safety.controller;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.ApiResponse;
import com.company.module.safety.dto.request.ExcelSheetAssignRequest;
import com.company.module.safety.dto.request.ExcelStagedImportRequest;
import com.company.module.safety.dto.request.ExcelStagedPhotoRequest;
import com.company.module.safety.dto.request.ExcelStagedPreviewRequest;
import com.company.module.safety.dto.request.ExcelStagedSheetRequest;
import com.company.module.safety.dto.response.ExcelChunkUploadResponse;
import com.company.module.safety.dto.response.ExcelImportResultResponse;
import com.company.module.safety.dto.response.ExcelSheetDetailResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.module.safety.dto.response.ExcelSheetPreviewResponse;
import com.company.module.safety.support.SafetyExcelParser.ParsedPhoto;
import com.company.module.safety.service.SafetyExcelStagingService;
import com.company.module.safety.service.SafetyExcelStagingService.Staged;
import com.company.module.safety.service.SafetyExcelUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 안전작업 매뉴얼 엑셀 일괄업로드 REST API (SAFETY 관리자만).
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
    private final SafetyExcelStagingService stagingService;
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

    // ================================================================
    // 분할 업로드 — 앞단 웹서버의 본문 크기 제한(nginx client_max_body_size 등)을 피한다
    // ================================================================

    /**
     * 파일 조각 하나를 올린다. 브라우저가 파일을 잘라 0번부터 순서대로 호출한다.
     *
     * <p>첫 호출은 {@code uploadId} 를 비워 보내고, 응답으로 받은 값을 이후 조각에 그대로 실어 보낸다.
     * 요청 하나의 크기가 조각 크기(기본 4MB)뿐이라 웹서버 제한에 걸리지 않는다.
     */
    @PostMapping("/safety-api/excel-upload/chunk")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<ExcelChunkUploadResponse>> uploadChunk(
            @RequestParam(value = "uploadId", required = false) String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Staged staged = stagingService.appendChunk(
                uploadId, chunkIndex, totalChunks, fileName, file, ownerOf(authentication));
        return ResponseEntity.ok(ApiResponse.success(ExcelChunkUploadResponse.builder()
                .uploadId(staged.getUploadId())
                .receivedChunks(staged.getReceivedChunks())
                .totalChunks(staged.getTotalChunks())
                .completed(staged.isCompleted())
                .build()));
    }

    /** 1단계(분할 업로드판): 이미 올라온 파일의 시트별 형식 확인. 파일을 다시 보내지 않는다. */
    @PostMapping("/safety-api/excel-upload/preview-staged")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<List<ExcelSheetPreviewResponse>>> previewStaged(
            @Valid @RequestBody ExcelStagedPreviewRequest request, Authentication authentication) {
        Staged staged = stagingService.complete(request.getUploadId(), ownerOf(authentication));
        return ResponseEntity.ok(ApiResponse.success(excelUploadService.preview(staged.getPath())));
    }

    /**
     * 1단계 상세: 고른 시트가 어떤 표로 등록될지 그대로 보여준다.
     *
     * <p>목록 응답은 시트마다 몇 줄만 요약해 주므로, 확정 전에 실제 내용을 확인하려면 이 API 를 쓴다.
     * 사진 원본은 싣지 않고 번호만 주며, 화면이 보이는 것만 아래 preview-photo 로 가져간다.
     */
    @PostMapping("/safety-api/excel-upload/preview-sheet")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<ExcelSheetDetailResponse>> previewSheet(
            @Valid @RequestBody ExcelStagedSheetRequest request, Authentication authentication) {
        Staged staged = stagingService.complete(request.getUploadId(), ownerOf(authentication));
        return ResponseEntity.ok(ApiResponse.success(
                excelUploadService.previewSheet(staged.getPath(), request.getSheetName())));
    }

    /**
     * 미리보기용 사진 한 장. 아직 저장 전이라 올려 둔 임시 파일에서 그때그때 꺼내 준다.
     *
     * <p>&lt;img&gt; 태그가 직접 부르지 않고 화면 스크립트가 토큰을 실어 받아 가므로
     * (blob 으로 바꿔 표시) 다른 API 처럼 인증이 필요하다.
     */
    @PostMapping("/safety-api/excel-upload/preview-photo")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<byte[]> previewPhoto(
            @Valid @RequestBody ExcelStagedPhotoRequest request, Authentication authentication) {
        Staged staged = stagingService.complete(request.getUploadId(), ownerOf(authentication));
        ParsedPhoto photo = excelUploadService.previewPhoto(
                staged.getPath(), request.getSheetName(), request.getPhotoIndex());
        MediaType type = (photo.contentType() != null)
                ? MediaType.parseMediaType(photo.contentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(type)
                .body(photo.data());
    }

    /** 2단계(분할 업로드판): 이미 올라온 파일로 확정 저장하고, 임시 파일을 지운다. */
    @PostMapping("/safety-api/excel-upload/confirm-staged")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<ExcelImportResultResponse>> confirmStaged(
            @Valid @RequestBody ExcelStagedImportRequest request, Authentication authentication) {
        String owner = ownerOf(authentication);
        Staged staged = stagingService.complete(request.getUploadId(), owner);
        try {
            return ResponseEntity.ok(ApiResponse.created(excelUploadService.confirmImport(
                    staged.getPath(), staged.getFileName(), request.getAssignments(), owner)));
        } finally {
            stagingService.discard(request.getUploadId(), owner);
        }
    }

    /** 업로드를 취소하고 임시 파일을 지운다. (다른 파일을 다시 고를 때 화면에서 호출) */
    @PostMapping("/safety-api/excel-upload/discard-staged")
    @PreAuthorize("@safetyPerm.isAdmin(authentication)")
    public ResponseEntity<ApiResponse<Void>> discardStaged(
            @Valid @RequestBody ExcelStagedPreviewRequest request, Authentication authentication) {
        stagingService.discard(request.getUploadId(), ownerOf(authentication));
        return ResponseEntity.ok(ApiResponse.success());
    }

    private String ownerOf(Authentication authentication) {
        return (authentication != null) ? authentication.getName() : null;
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
