package com.company.module.psinsp.service;

import com.company.module.psinsp.dto.PsInspectionResponse;
import com.company.module.psinsp.dto.PsInspectionSaveRequest;
import com.company.module.psinsp.entity.PsInspection;
import com.company.module.psinsp.repository.PsInspectionRepository;
import com.company.core.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * PS 지분 검사 비즈니스 로직 서비스
 *
 * <p>@Transactional(readOnly = true) 기본, 쓰기 메서드만 @Transactional
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PsInspectionService {

    private final PsInspectionRepository inspectionRepository;

    @Value("${ps-insp.upload.dir:/data/upload/ps_cov_ins}")
    private String uploadDir;

    // ──────────── 저장 (Upsert) ────────────

    @Transactional
    public PsInspectionResponse saveInspection(PsInspectionSaveRequest request,
                                                    MultipartFile originalImage,
                                                    MultipartFile resultImage) {
        log.info("[PS-INSP] 검사 결과 저장 요청 - indBcd: {}, lotnr: {}, matnr: {}",
                request.getIndBcd(), request.getLotnr(), request.getMatnr());

        String matnrSafe = safeName(request.getMatnr());
        String indBcdSafe = safeName(request.getIndBcd());
        boolean isUpdate = false;
        PsInspection inspection = null;

        // Upsert: 동일 자재+LOT+바코드 조합 조회
        if (hasText(request.getMatnr()) && hasText(request.getLotnr()) && hasText(request.getIndBcd())) {
            inspection = inspectionRepository
                    .findByMatnrAndLotnrAndIndBcd(request.getMatnr(), request.getLotnr(), request.getIndBcd())
                    .orElse(null);
        }

        if (inspection != null) {
            // ── UPDATE ──
            isUpdate = true;
            log.info("[PS-INSP] 기존 레코드 발견 -> UPDATE (id: {})", inspection.getInspectionId());

            String origPath = saveUploadedImage(originalImage, "original", inspection.getInspectionId(), matnrSafe, indBcdSafe);
            String resPath = saveUploadedImage(resultImage, "result", inspection.getInspectionId(), matnrSafe, indBcdSafe);
            String yearMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy.MM"));

            inspection.updateInspectionData(
                    request.getInspectedAt(), request.getMsrmDate(),
                    request.getThresholdMax(), request.getTotalCount(),
                    request.getCoverageRatio(), request.getDensityCount(), request.getDensityRatio(),
                    request.getSizeUniformityScore(), request.getDistributionUniformityScore(),
                    request.getMeanSize(), request.getStdSize(),
                    request.getAutoCount(), request.getManualCount(), request.getRemovedAutoCount(),
                    request.getBucketUpTo3(), request.getBucketUpTo5(), request.getBucketUpTo7(), request.getBucketOver7(),
                    request.getQuadrantTopLeft(), request.getQuadrantTopRight(),
                    request.getQuadrantBottomLeft(), request.getQuadrantBottomRight(),
                    request.getObjectPixelCount(), request.getTotalPixels(),
                    request.getManualAddedCount(), request.getManualRemovedCount(),
                    request.getOperatorId(), request.getOperatorNm(), request.getDeviceId(), request.getStatus());

            inspection.updateOriginalImage(
                    origPath != null ? origPath : request.getOriginalImagePath(),
                    origPath != null ? fileName(origPath) : inspection.getOriginalImageName(),
                    origPath != null ? uploadDir + "/original/" + yearMonth : inspection.getOriginalImageDir());
            inspection.updateResultImage(
                    resPath != null ? resPath : request.getResultImagePath(),
                    resPath != null ? fileName(resPath) : inspection.getResultImageName(),
                    resPath != null ? uploadDir + "/result/" + yearMonth : inspection.getResultImageDir());
            inspection.updateMatnrNm(request.getMatnrNm());
            inspection.incrementSeq();       // 차수(seq) 증가: 1→2→3…
            inspection.incrementIndBcdSeq(); // 바코드차수(indBcdSeq) 증가

        } else {
            // ── INSERT ──
            int seq = 1;
            if (hasText(request.getIndBcd())) {
                seq = inspectionRepository.findMaxSeqByIndBcd(request.getIndBcd()).orElse(0) + 1;
            }

            String indBcdSeq = request.getIndBcdSeq();
            if (!hasText(indBcdSeq) && hasText(request.getIndBcd())) {
                long count = inspectionRepository.countByIndBcd(request.getIndBcd());
                indBcdSeq = String.valueOf(count + 1);
            }

            inspection = PsInspection.builder()
                    .seq(seq)
                    .inspItemGrpCd(request.getInspItemGrpCd())
                    .matnr(request.getMatnr())
                    .matnrNm(request.getMatnrNm())
                    .werks(request.getWerks())
                    .msrmDate(request.getMsrmDate())
                    .prcSeqno(request.getPrcSeqno())
                    .lotnr(request.getLotnr())
                    .indBcd(request.getIndBcd())
                    .indBcdSeq(indBcdSeq)
                    .inspectedAt(request.getInspectedAt())
                    .thresholdMax(request.getThresholdMax())
                    .totalCount(request.getTotalCount() != null ? request.getTotalCount() : 0)
                    .coverageRatio(request.getCoverageRatio())
                    .densityCount(request.getDensityCount())
                    .densityRatio(request.getDensityRatio())
                    .sizeUniformityScore(request.getSizeUniformityScore())
                    .distributionUniformityScore(request.getDistributionUniformityScore())
                    .meanSize(request.getMeanSize())
                    .stdSize(request.getStdSize())
                    .autoCount(request.getAutoCount() != null ? request.getAutoCount() : 0)
                    .manualCount(request.getManualCount() != null ? request.getManualCount() : 0)
                    .removedAutoCount(request.getRemovedAutoCount() != null ? request.getRemovedAutoCount() : 0)
                    .bucketUpTo3(request.getBucketUpTo3())
                    .bucketUpTo5(request.getBucketUpTo5())
                    .bucketUpTo7(request.getBucketUpTo7())
                    .bucketOver7(request.getBucketOver7())
                    .quadrantTopLeft(request.getQuadrantTopLeft())
                    .quadrantTopRight(request.getQuadrantTopRight())
                    .quadrantBottomLeft(request.getQuadrantBottomLeft())
                    .quadrantBottomRight(request.getQuadrantBottomRight())
                    .objectPixelCount(request.getObjectPixelCount())
                    .totalPixels(request.getTotalPixels())
                    .manualAddedCount(request.getManualAddedCount() != null ? request.getManualAddedCount() : 0)
                    .manualRemovedCount(request.getManualRemovedCount() != null ? request.getManualRemovedCount() : 0)
                    .operatorId(request.getOperatorId())
                    .operatorNm(request.getOperatorNm())
                    .deviceId(request.getDeviceId())
                    .status(request.getStatus())
                    .build();

            // persist 먼저 실행하여 ID 생성
            inspection = inspectionRepository.save(inspection);

            // ID 확보 후 이미지 저장
            String origPath = saveUploadedImage(originalImage, "original", inspection.getInspectionId(), matnrSafe, indBcdSafe);
            String resPath = saveUploadedImage(resultImage, "result", inspection.getInspectionId(), matnrSafe, indBcdSafe);
            String yearMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy.MM"));

            inspection.updateOriginalImage(
                    origPath != null ? origPath : request.getOriginalImagePath(),
                    origPath != null ? fileName(origPath) : null,
                    origPath != null ? uploadDir + "/original/" + yearMonth : null);
            inspection.updateResultImage(
                    resPath != null ? resPath : request.getResultImagePath(),
                    resPath != null ? fileName(resPath) : null,
                    resPath != null ? uploadDir + "/result/" + yearMonth : null);
        }

        inspection = inspectionRepository.save(inspection);

        log.info("[PS-INSP] 검사 결과 {} - id: {}, seq: {}, indBcd: {}",
                isUpdate ? "갱신(UPDATE)" : "신규(INSERT)",
                inspection.getInspectionId(), inspection.getSeq(), inspection.getIndBcd());

        return PsInspectionResponse.from(inspection, isUpdate);
    }

    // ──────────── 조회 ────────────

    public PsInspectionResponse getInspection(Long id) {
        PsInspection inspection = inspectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("검사 결과를 찾을 수 없습니다. ID: " + id));
        return PsInspectionResponse.from(inspection);
    }

    public Page<PsInspectionResponse> listInspections(Pageable pageable) {
        return inspectionRepository.findAllByOrderByInspectedAtDesc(pageable)
                .map(PsInspectionResponse::from);
    }

    public Page<PsInspectionResponse> searchInspections(String indBcd, String dateFrom, String dateTo, Pageable pageable) {
        boolean hasIndBcd = hasText(indBcd);
        boolean hasDateFrom = hasText(dateFrom);
        boolean hasDateTo = hasText(dateTo);

        if (!hasIndBcd && !hasDateFrom && !hasDateTo) {
            return listInspections(pageable);
        }

        LocalDateTime from = hasDateFrom ? LocalDate.parse(dateFrom).atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime to = hasDateTo ? LocalDate.parse(dateTo).atTime(LocalTime.MAX) : LocalDateTime.of(2099, 12, 31, 23, 59, 59);

        if (hasIndBcd) {
            return inspectionRepository.searchByIndBcdAndDateRange(indBcd, from, to, pageable)
                    .map(PsInspectionResponse::from);
        } else {
            return inspectionRepository.findByDateRange(from, to, pageable)
                    .map(PsInspectionResponse::from);
        }
    }

    public Page<PsInspectionResponse> searchByIndBcd(String keyword, Pageable pageable) {
        return inspectionRepository.searchByIndBcd(keyword, pageable).map(PsInspectionResponse::from);
    }

    public Page<PsInspectionResponse> searchByLotnr(String keyword, Pageable pageable) {
        return inspectionRepository.searchByLotnr(keyword, pageable).map(PsInspectionResponse::from);
    }

    public Page<PsInspectionResponse> searchByMatnr(String keyword, Pageable pageable) {
        return inspectionRepository.searchByMatnr(keyword, pageable).map(PsInspectionResponse::from);
    }

    public Map<String, Object> checkExists(String matnr, String lotnr, String indBcd) {
        return inspectionRepository.findByMatnrAndLotnrAndIndBcd(matnr, lotnr, indBcd)
                .map(entity -> Map.<String, Object>of(
                        "exists", true,
                        "record", Map.of(
                                "inspectionId", entity.getInspectionId(),
                                "seq", entity.getSeq(),
                                "indBcdSeq", entity.getIndBcdSeq() != null ? entity.getIndBcdSeq() : "1",
                                "inspectedAt", entity.getInspectedAt() != null ? entity.getInspectedAt().toString() : "",
                                "coverageRatio", entity.getCoverageRatio() != null ? entity.getCoverageRatio() : java.math.BigDecimal.ZERO,
                                "totalCount", entity.getTotalCount() != null ? entity.getTotalCount() : 0
                        )
                ))
                .orElse(Map.of("exists", false));
    }

    // ──────────── 삭제 ────────────

    @Transactional
    public void deleteInspection(Long id) {
        if (!inspectionRepository.existsById(id)) {
            throw new EntityNotFoundException("검사 결과를 찾을 수 없습니다. ID: " + id);
        }
        inspectionRepository.deleteById(id);
        log.info("[PS-INSP] 검사 결과 삭제 - id: {}", id);
    }

    @Transactional
    public void deleteAllInspections() {
        inspectionRepository.deleteAll();
        log.info("[PS-INSP] 전체 검사 결과 삭제");
    }

    // ──────────── 이미지 저장 ────────────

    private Path getImageSubDir(String category) {
        String yearMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy.MM"));
        Path dir = Paths.get(uploadDir, category, yearMonth);
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("[PS-INSP] 이미지 디렉토리 생성 실패 - dir: {}", dir, e);
        }
        return dir;
    }

    private String saveUploadedImage(MultipartFile file, String prefix, Long inspectionId,
                                      String matnrSafe, String indBcdSafe) {
        if (file == null || file.isEmpty()) return null;
        try {
            String category = "result".equals(prefix) ? "result" : "original";
            Path dir = getImageSubDir(category);

            String originalName = file.getOriginalFilename();
            String ext = "jpg";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf('.') + 1);
            }
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd_HHmmss"));
            String idSuffix = String.valueOf(inspectionId);
            if (idSuffix.length() > 8) idSuffix = idSuffix.substring(0, 8);
            String filename = String.format("%s_%s_%s_%s_%s.%s",
                    prefix, matnrSafe, indBcdSafe, timestamp, idSuffix, ext);
            Path filePath = dir.resolve(filename);
            file.transferTo(filePath.toFile());

            String yearMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy.MM"));
            return String.format("/uploads/%s/%s/%s", category, yearMonth, filename);
        } catch (IOException e) {
            log.error("[PS-INSP] 이미지 저장 실패 - prefix: {}", prefix, e);
            return null;
        }
    }

    // ──────────── Helpers ────────────

    private boolean hasText(String s) { return s != null && !s.isBlank(); }
    private String safeName(String s) { return s != null ? s.replaceAll("[^a-zA-Z0-9_-]", "_") : "unknown"; }
    private String fileName(String path) { return path != null ? path.substring(path.lastIndexOf('/') + 1) : null; }
}
