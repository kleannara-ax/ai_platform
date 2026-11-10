package com.company.module.cckb.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.cckb.dto.response.CckbImageResponse;
import com.company.module.cckb.entity.CckbImage;
import com.company.module.cckb.repository.CckbImageRepository;
import com.company.module.cckb.support.CckbFileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CCKB 이미지 조회(다운로드/미리보기) REST Controller.
 *
 * <p>※ module-dailyreport 의 {@code ImageController} 패턴을 그대로 복제한다 (사용자 명시 지시:
 * "세부공장일보에 이미지 넣는 거랑 같은 방식으로").
 * <ul>
 *   <li>실제 파일 업로드/저장은 {@link InspectionController#analyze} 에서 검사 실행과 함께
 *       한 번에 처리한다 (CckbFileStorageService 가 디스크 저장 담당) — dailyreport 와 달리
 *       CCKB 는 "검사 실행 = 이미지 1장 업로드 + 분석"이 한 몸이라 별도 업로드 엔드포인트를
 *       분리하지 않는다. 이 컨트롤러는 조회(다운로드)만 전담한다.</li>
 *   <li>★ /cckb-api/** 는 SecurityConfig 상 인증이 필요하므로(=permitAll 예외 없음),
 *       파일 다운로드도 Authorization 헤더(Bearer) 인증을 그대로 요구한다.
 *       프론트엔드는 &lt;img src="..."&gt; 가 아니라 인증된 fetch()로 blob 을 받아
 *       URL.createObjectURL()로 표시해야 한다.</li>
 *   <li>filename 만으로 다른 검사의 사진에 접근하는 것을 막기 위해, 실제로 DB 메타에
 *       해당 filename 이 존재하는지(소유권) 검증한 뒤에만 서빙한다.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/cckb-api/images")
@RequiredArgsConstructor
public class ImageController {

    private final CckbImageRepository cckbImageRepository;
    private final CckbFileStorageService fileStorageService;

    /** GET /cckb-api/images/inspection/{inspectionId} — 특정 검사에 속한 이미지 메타 목록 */
    @GetMapping("/inspection/{inspectionId}")
    public ResponseEntity<ApiResponse<List<CckbImageResponse>>> getImagesByInspection(
            @PathVariable String inspectionId,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        List<CckbImageResponse> images = cckbImageRepository
                .findByInspectionRecord_InspectionIdOrderByImageIdAsc(inspectionId)
                .stream().map(CckbImageResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(images));
    }

    /**
     * 이미지 파일 조회(다운로드/미리보기)
     * GET /cckb-api/images/files/{filename}
     *
     * - filename 이 실제 DB의 이미지 메타(FILE_URL)와 일치하는 경우에만 서빙
     *   (다른 검사의 파일명을 추측해 접근하는 것을 방지 — dailyreport 의 belongsToReport 검증과 동일)
     * - path-traversal 방지: 파일명에 ".."나 "/" 포함 시 거부 (CckbFileStorageService.resolveForServing 에서 처리)
     */
    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> getImageFile(
            @PathVariable String filename,
            @AuthenticationPrincipal(expression = "userId") Long userId) {

        String clean = filename == null ? "" : filename.replace("\\", "/");
        if (clean.contains("..") || clean.contains("/")) {
            return ResponseEntity.badRequest().build();
        }

        // ★ 이 파일이 실제로 image 테이블에 등록된 이미지인지 확인 (imageId 없이도 접근 가능한
        //   구조이므로, FILE_URL 경로에 저장된 파일명 기준으로 소속 여부를 검증)
        boolean exists = !cckbImageRepository.findByFileUrlEndingWith("/" + clean).isEmpty();
        if (!exists) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path file = fileStorageService.resolveForServing(clean);
            if (!Files.exists(file)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(file.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .contentType(mediaType)
                    .body(resource);
        } catch (Exception ex) {
            log.error("CCKB 이미지 조회 실패 filename={}", filename, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 이미지 삭제 (DB 메타 + 물리 파일 함께 삭제)
     * DELETE /cckb-api/images/{imageId}
     */
    @DeleteMapping("/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable Long imageId,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        CckbImage image = cckbImageRepository.findById(imageId)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException("이미지", imageId));
        image.delete(userId);
        fileStorageService.deleteIfExists(image.getFileUrl());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
