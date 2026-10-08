package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.LogUploadDetailResponse;
import com.company.module.security_log.dto.LogUploadRequest;
import com.company.module.security_log.dto.LogUploadResponse;
import com.company.module.security_log.dto.LogUploadUpdateRequest;
import com.company.module.security_log.dto.MonthlySummaryResponse;
import com.company.module.security_log.dto.RuleHitCount;
import com.company.module.security_log.dto.UploadReviewRequest;
import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.entity.ReviewStatus;
import com.company.module.security_log.entity.SecLogDetection;
import com.company.module.security_log.entity.SecLogRule;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.entity.SecLogUploadFile;
import com.company.module.security_log.entity.Severity;
import com.company.module.security_log.entity.UploadStatus;
import com.company.module.security_log.repository.SecLogDetectionRepository;
import com.company.module.security_log.repository.SecLogUploadFileRepository;
import com.company.module.security_log.repository.SecLogUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 로그 업로드 / 분석 / 월별 모니터링
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogUploadService {

    private final SecLogUploadRepository uploadRepository;
    private final SecLogUploadFileRepository uploadFileRepository;
    private final SecLogDetectionRepository detectionRepository;
    private final SecLogRuleService ruleService;
    private final LogAnalysisEngine analysisEngine;

    private static final String MSG_INVALID_EXT = "허용 확장자: log, txt, csv, json, out";
    private static final String MSG_REVIEWED_LOCKED = "검토완료된 건은 변경할 수 없습니다. 검토취소 후 진행하세요.";

    // ------------------------------------------------------------------ 조회

    /** 목록 화면 - 월별 조회 */
    public Page<LogUploadResponse> getList(String targetYm, String systemName, LogType logType,
                                           UploadStatus status, Severity maxSeverity, int page, int size) {
        validateYm(targetYm);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Order.desc("uploadId")));
        String sys = StringUtils.hasText(systemName) ? systemName.trim() : null;
        return uploadRepository.search(targetYm, sys, logType, status, maxSeverity, pageable)
                .map(LogUploadResponse::from);
    }

    /** 상세 화면 */
    public LogUploadDetailResponse getDetail(Long uploadId) {
        SecLogUpload upload = findUpload(uploadId);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (ReviewStatus rs : ReviewStatus.values()) {
            statusCounts.put(rs.name(), 0L);
        }
        for (Object[] row : detectionRepository.countByReviewStatus(uploadId)) {
            statusCounts.put(((ReviewStatus) row[0]).name(), (Long) row[1]);
        }

        return LogUploadDetailResponse.of(upload, statusCounts,
                toRuleHits(detectionRepository.countByRule(uploadId)));
    }

    /** 월별 모니터링 요약 */
    public MonthlySummaryResponse getMonthlySummary(String targetYm) {
        validateYm(targetYm);
        List<SecLogUpload> uploads = uploadRepository.findByTargetYmAndDeletedYn(targetYm, SecLogConstants.DELETED_N);

        long totalLines = 0;
        long critical = 0;
        long high = 0;
        long medium = 0;
        long low = 0;
        int reviewed = 0;
        for (SecLogUpload u : uploads) {
            totalLines += u.getTotalLineCount();
            critical += u.getCriticalCount();
            high += u.getHighCount();
            medium += u.getMediumCount();
            low += u.getLowCount();
            if (u.isReviewed()) {
                reviewed++;
            }
        }
        List<RuleHitCount> top = toRuleHits(detectionRepository.countByRuleForMonth(targetYm));

        return MonthlySummaryResponse.builder()
                .targetYm(targetYm)
                .uploadCount(uploads.size())
                .systemCount((int) uploads.stream().map(SecLogUpload::getSystemName).distinct().count())
                .reviewedUploadCount(reviewed)
                .pendingUploadCount(uploads.size() - reviewed)
                .totalLineCount(totalLines)
                .detectionCount(critical + high + medium + low)
                .criticalCount(critical)
                .highCount(high)
                .mediumCount(medium)
                .lowCount(low)
                .pendingDetectionCount(detectionRepository.countForMonthByReviewStatus(targetYm, ReviewStatus.PENDING))
                .topRules(top.size() > 10 ? top.subList(0, 10) : top)
                .build();
    }

    // ------------------------------------------------------------------ 등록

    /** 등록 화면 - 로그 업로드 및 즉시 분석 */
    @Transactional
    public LogUploadResponse upload(LogUploadRequest request, MultipartFile file, Long userId) {
        if (request.getLogType() == LogType.ALL) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드 로그 유형에는 ALL 을 사용할 수 없습니다.");
        }
        String originalName = validateFile(file);
        byte[] content = readBytes(file);
        String hash = sha256(content);

        // 동일 월/시스템에 동일 파일 중복 업로드 방지
        if (uploadRepository.existsByTargetYmAndSystemNameAndFileHashAndDeletedYn(
                request.getTargetYm(), request.getSystemName().trim(), hash, SecLogConstants.DELETED_N)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "동일 월/시스템에 같은 내용의 로그 파일이 이미 업로드되어 있습니다.");
        }

        String charsetName = StringUtils.hasText(request.getFileCharset()) ? request.getFileCharset() : "UTF-8";

        SecLogUpload upload = uploadRepository.save(SecLogUpload.builder()
                .targetYm(request.getTargetYm())
                .systemName(request.getSystemName().trim())
                .logType(request.getLogType())
                .originalFileName(originalName)
                .fileSize((long) content.length)
                .fileHash(hash)
                .fileCharset(charsetName)
                .remark(request.getRemark())
                .createdBy(userId)
                .build());

        uploadFileRepository.save(SecLogUploadFile.builder()
                .uploadId(upload.getUploadId())
                .fileData(gzip(content))
                .createdBy(userId)
                .build());

        runAnalysis(upload, content, null);
        return LogUploadResponse.from(upload);
    }

    // ------------------------------------------------------------------ 수정

    /** 수정 화면 - 기본정보 수정 (옵션: 재분석) */
    @Transactional
    public LogUploadResponse update(Long uploadId, LogUploadUpdateRequest request, Long userId) {
        if (request.getLogType() == LogType.ALL) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드 로그 유형에는 ALL 을 사용할 수 없습니다.");
        }
        SecLogUpload upload = findUpload(uploadId);
        if (upload.isReviewed()) {
            // 검토 완료 건은 증적 보존을 위해 수정 불가 (검토취소 후 수정)
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, MSG_REVIEWED_LOCKED);
        }
        boolean logTypeChanged = upload.getLogType() != request.getLogType();

        upload.update(request.getTargetYm(), request.getSystemName().trim(), request.getLogType(),
                request.getRemark(), userId);

        if (request.isReanalyze() || logTypeChanged) {
            return LogUploadResponse.from(reanalyzeInternal(upload, userId));
        }
        return LogUploadResponse.from(upload);
    }

    /** 현재 룰셋으로 재분석 (기존 탐지 결과는 소프트 삭제 후 재생성) */
    @Transactional
    public LogUploadResponse reanalyze(Long uploadId, Long userId) {
        SecLogUpload upload = findUpload(uploadId);
        if (upload.isReviewed()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, MSG_REVIEWED_LOCKED);
        }
        return LogUploadResponse.from(reanalyzeInternal(upload, userId));
    }

    /** 정보보안팀 최종 검토 완료 (미검토 탐지 건이 남아 있으면 불가) */
    @Transactional
    public LogUploadResponse completeReview(Long uploadId, UploadReviewRequest request, Long userId) {
        SecLogUpload upload = findUpload(uploadId);
        long pending = detectionRepository.countByUploadIdAndReviewStatusAndDeletedYn(
                uploadId, ReviewStatus.PENDING, SecLogConstants.DELETED_N);
        if (pending > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "미검토 탐지 건이 " + pending + "건 남아 있어 검토완료할 수 없습니다.");
        }
        upload.completeReview(request.getReviewComment(), userId);
        return LogUploadResponse.from(upload);
    }

    @Transactional
    public LogUploadResponse cancelReview(Long uploadId, Long userId) {
        SecLogUpload upload = findUpload(uploadId);
        if (!upload.isReviewed()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "검토완료 상태가 아닙니다.");
        }
        upload.cancelReview(userId);
        return LogUploadResponse.from(upload);
    }

    // ------------------------------------------------------------------ 삭제

    /** 소프트 삭제 (업로드 + 원본파일 + 탐지결과) - 관리자 전용 */
    @Transactional
    public void delete(Long uploadId, Long userId) {
        findUpload(uploadId); // 존재 여부 검증

        detectionRepository.softDeleteByUploadId(uploadId, userId, LocalDateTime.now());
        uploadFileRepository.findByUploadIdAndDeletedYn(uploadId, SecLogConstants.DELETED_N)
                .ifPresent(f -> f.delete(userId));
        // softDeleteByUploadId 의 clearAutomatically 로 영속성 컨텍스트가 비워지므로 재조회
        findUpload(uploadId).delete(userId);
    }

    // ------------------------------------------------------------------ 원본 다운로드

    public DownloadFile getOriginalFile(Long uploadId) {
        SecLogUpload upload = findUpload(uploadId);
        SecLogUploadFile file = uploadFileRepository.findByUploadIdAndDeletedYn(uploadId, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        return new DownloadFile(upload.getOriginalFileName(), gunzip(file.getFileData()));
    }

    public record DownloadFile(String fileName, byte[] content) {
    }

    // ------------------------------------------------------------------ internal

    /** 재분석 후 영속 상태의 업로드 엔티티를 반환 */
    private SecLogUpload reanalyzeInternal(SecLogUpload upload, Long userId) {
        SecLogUploadFile file = uploadFileRepository
                .findByUploadIdAndDeletedYn(upload.getUploadId(), SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        byte[] content = gunzip(file.getFileData());
        Long uploadId = upload.getUploadId();

        detectionRepository.softDeleteByUploadId(uploadId, userId, LocalDateTime.now());
        // clearAutomatically 로 detach 되었으므로 재조회 후 반영
        SecLogUpload managed = findUpload(uploadId);
        runAnalysis(managed, content, userId);
        return managed;
    }

    private void runAnalysis(SecLogUpload upload, byte[] content, Long updatedBy) {
        List<SecLogRule> rules = ruleService.getActiveRules(upload.getLogType());
        LogAnalysisEngine.AnalysisResult result =
                analysisEngine.analyze(content, toCharset(upload.getFileCharset()), rules);

        Long creator = updatedBy != null ? updatedBy : upload.getCreatedBy();
        int critical = 0;
        int high = 0;
        int medium = 0;
        int low = 0;
        List<SecLogDetection> detections = new ArrayList<>();

        for (LogAnalysisEngine.RuleHit hit : result.getRuleHits()) {
            SecLogRule rule = hit.rule();
            for (LogAnalysisEngine.MatchedLine line : hit.lines()) {
                detections.add(SecLogDetection.builder()
                        .uploadId(upload.getUploadId())
                        .ruleId(rule.getRuleId())
                        .ruleCode(rule.getRuleCode())
                        .ruleName(rule.getRuleName())
                        .severity(rule.getSeverity())
                        .lineNumber(line.lineNumber())
                        .logContent(line.content())
                        .createdBy(creator)
                        .build());
            }
            int n = hit.lines().size();
            switch (rule.getSeverity()) {
                case CRITICAL -> critical += n;
                case HIGH -> high += n;
                case MEDIUM -> medium += n;
                case LOW -> low += n;
                default -> { }
            }
        }
        detectionRepository.saveAll(detections);
        upload.applyAnalysisResult(result.getTotalLineCount(), critical, high, medium, low, updatedBy);
    }

    private SecLogUpload findUpload(Long uploadId) {
        return uploadRepository.findByUploadIdAndDeletedYn(uploadId, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드할 로그 파일이 없습니다.");
        }
        if (file.getSize() > SecLogConstants.MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "로그 파일은 최대 50MB 까지 업로드할 수 있습니다.");
        }
        String name = file.getOriginalFilename();
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파일명이 올바르지 않습니다.");
        }
        // 경로 조작 방지: 파일명만 사용
        String cleaned = StringUtils.getFilename(StringUtils.cleanPath(name).replace('\\', '/'));
        if (!StringUtils.hasText(cleaned) || cleaned.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파일명이 올바르지 않습니다(최대 255자).");
        }
        String ext = StringUtils.getFilenameExtension(cleaned);
        if (ext == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, MSG_INVALID_EXT);
        }
        String lowerExt = ext.toLowerCase(Locale.ROOT);
        for (String allowed : SecLogConstants.ALLOWED_EXTENSIONS) {
            if (allowed.equals(lowerExt)) {
                return cleaned;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, MSG_INVALID_EXT);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            // 바이너리 파일 업로드 차단 (앞부분 NUL 바이트 검사)
            int checkLen = Math.min(bytes.length, 8192);
            for (int i = 0; i < checkLen; i++) {
                if (bytes[i] == 0) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "텍스트 로그 파일만 업로드할 수 있습니다.");
                }
            }
            return bytes;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드 파일을 읽을 수 없습니다.");
        }
    }

    private static Charset toCharset(String name) {
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private static byte[] gzip(byte[] content) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(content.length / 4, 64));
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(content);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return bos.toByteArray();
    }

    private static byte[] gunzip(byte[] compressed) {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private static List<RuleHitCount> toRuleHits(List<Object[]> rows) {
        return rows.stream()
                .map(r -> {
                    Severity sev = (Severity) r[2];
                    return RuleHitCount.builder()
                            .ruleCode((String) r[0])
                            .ruleName((String) r[1])
                            .severity(sev)
                            .severityLabel(sev.getLabel())
                            .hitCount((Long) r[3])
                            .build();
                })
                .toList();
    }

    private static void validateYm(String targetYm) {
        if (targetYm == null || !targetYm.matches("^\\d{4}(0[1-9]|1[0-2])$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "대상 연월은 YYYYMM 형식입니다.");
        }
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 50;
        }
        return Math.min(size, SecLogConstants.MAX_PAGE_SIZE);
    }
}
