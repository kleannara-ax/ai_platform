package com.company.module.security_log.service;

import com.company.module.security_log.support.SecLogTime;
import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.AccessReportResponse;
import com.company.module.security_log.dto.AccessSummaryResponse;
import com.company.module.security_log.dto.LogUploadDetailResponse;
import com.company.module.security_log.dto.LogUploadRequest;
import com.company.module.security_log.dto.LogUploadResponse;
import com.company.module.security_log.dto.LogUploadUpdateRequest;
import com.company.module.security_log.dto.MonthlySummaryResponse;
import com.company.module.security_log.dto.RuleHitCount;
import com.company.module.security_log.dto.UploadReviewRequest;
import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.entity.SecLogAccessSummary;
import com.company.module.security_log.entity.SecLogSystem;
import com.company.module.security_log.entity.ReviewStatus;
import com.company.module.security_log.entity.SecLogDetection;
import com.company.module.security_log.entity.SecLogRule;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.entity.SecLogUploadFile;
import com.company.module.security_log.entity.Severity;
import com.company.module.security_log.entity.UploadStatus;
import com.company.module.security_log.repository.SecLogAccessSummaryRepository;
import com.company.module.security_log.repository.SecLogAccessDailyRepository;
import com.company.module.security_log.entity.SecLogAccessDaily;
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
    private final AccessLogAnalyzer accessLogAnalyzer;
    private final ExcelTextConverter excelTextConverter;
    private final SecLogAccessSummaryRepository accessSummaryRepository;
    private final SecLogAccessDailyRepository accessDailyRepository;
    private final SecLogSystemService systemService;
    private final SecLogInternalIpService internalIpService;

    private static final String MSG_INVALID_EXT = "허용 확장자: log, txt, csv, tsv, json, out, xlsx, xls";
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

        List<RuleHitCount> hits = withRuleReviewCounts(uploadId, toRuleHits(detectionRepository.countByRule(uploadId)));
        return LogUploadDetailResponse.of(upload, statusCounts, withZeroHitRules(upload, hits));
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
        String originalName = validateFile(file);
        byte[] content = readBytes(file, ExcelTextConverter.isExcel(originalName));
        return uploadContent(request, originalName, content, userId);
    }

    /**
     * 파일 내용으로 등록 + 분석 (수동 업로드, API 자동 수집 공통)
     * - API 수집분은 userId 가 null 이다.
     */
    @Transactional
    public LogUploadResponse uploadContent(LogUploadRequest request, String originalName, byte[] content, Long userId) {
        if (request.getLogType() == LogType.ALL) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드 로그 유형에는 ALL 을 사용할 수 없습니다.");
        }
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "로그 내용이 비어 있습니다.");
        }
        String systemName = resolveSystemName(request.getSystemId(), request.getSystemName());
        String hash = sha256(content);

        // 동일 월/시스템에 동일 파일 중복 업로드 방지
        if (uploadRepository.existsByTargetYmAndSystemNameAndFileHashAndDeletedYn(
                request.getTargetYm(), systemName, hash, SecLogConstants.DELETED_N)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "동일 월/시스템에 같은 내용의 로그 파일이 이미 업로드되어 있습니다.");
        }

        String charsetName = ExcelTextConverter.isExcel(originalName) ? "UTF-8"
                : (StringUtils.hasText(request.getFileCharset()) ? request.getFileCharset() : "UTF-8");

        SecLogUpload upload = uploadRepository.save(SecLogUpload.builder()
                .systemId(request.getSystemId())
                .targetYm(request.getTargetYm())
                .systemName(systemName)
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

        runAnalysis(upload, analysisText(originalName, content), null);
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

        upload.update(request.getSystemId(), request.getTargetYm(),
                resolveSystemName(request.getSystemId(), request.getSystemName()), request.getLogType(),
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

        detectionRepository.softDeleteByUploadId(uploadId, userId, SecLogTime.now());
        accessSummaryRepository.softDeleteByUploadId(uploadId, userId, SecLogTime.now());
        accessDailyRepository.softDeleteByUploadId(uploadId, userId, SecLogTime.now());
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
        byte[] content = analysisText(upload.getOriginalFileName(), gunzip(file.getFileData()));
        Long uploadId = upload.getUploadId();

        detectionRepository.softDeleteByUploadId(uploadId, userId, SecLogTime.now());
        accessSummaryRepository.softDeleteByUploadId(uploadId, userId, SecLogTime.now());
        accessDailyRepository.softDeleteByUploadId(uploadId, userId, SecLogTime.now());
        // clearAutomatically 로 detach 되었으므로 재조회 후 반영
        SecLogUpload managed = findUpload(uploadId);
        runAnalysis(managed, content, userId);
        return managed;
    }

    private void runAnalysis(SecLogUpload upload, byte[] content, Long updatedBy) {
        List<SecLogRule> rules = ruleService.getActiveRules(upload.getSystemId(), upload.getLogType());
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
        saveAccessSummaries(upload.getUploadId(), content, toCharset(upload.getFileCharset()), creator);
        java.util.Map<Integer, Integer> detected = new java.util.HashMap<>();
        detections.forEach(d -> detected.merge(d.getLineNumber(), 1, Integer::sum));
        saveDaily(upload, content, detected, creator);
        upload.applyAnalysisResult(result.getTotalLineCount(), critical, high, medium, low, updatedBy);
    }

    /** 접속 행위 분석 결과 조회 (계정별 로그인 성공/실패/시간외/외부IP) */
    public AccessReportResponse getAccessReport(Long uploadId) {
        findUpload(uploadId);
        List<AccessSummaryResponse> list = accessSummaryRepository
                .findByUploadIdAndDeletedYnOrderByFailCountDescSuccessCountDesc(uploadId, SecLogConstants.DELETED_N)
                .stream().map(AccessSummaryResponse::from)
                .sorted(java.util.Comparator.comparingInt((AccessSummaryResponse a) -> a.getRiskFlags().isEmpty() ? 1 : 0)
                        .thenComparing(java.util.Comparator.comparingInt(AccessSummaryResponse::getFailCount).reversed()))
                .toList();
        return AccessReportResponse.builder()
                .uploadId(uploadId)
                .accountCount(list.size())
                .riskAccountCount((int) list.stream().filter(a -> !a.getRiskFlags().isEmpty()).count())
                .successTotal(list.stream().mapToLong(AccessSummaryResponse::getSuccessCount).sum())
                .failTotal(list.stream().mapToLong(AccessSummaryResponse::getFailCount).sum())
                .accounts(list)
                .build();
    }

    private void saveAccessSummaries(Long uploadId, byte[] content, Charset charset, Long creator) {
        AccessLogAnalyzer.Result r = accessLogAnalyzer.analyze(content, charset, internalIpService.internalMatcher());
        accessSummaryRepository.saveAll(r.getAccounts().stream().map(a -> SecLogAccessSummary.builder()
                .uploadId(uploadId).accountId(a.getAccountId()).successCount(a.getSuccessCount())
                .failCount(a.getFailCount()).offHourCount(a.getOffHourCount()).externalIpCount(a.getExternalIpCount())
                .ipCount(a.getIpCount()).ipList(a.getIpList()).firstSeen(a.getFirstSeen()).lastSeen(a.getLastSeen())
                .riskFlags(a.getRiskFlags()).createdBy(creator).build()).toList());
    }

    /** 일자별 집계 저장 (대시보드 일별 수집 현황·추이, 월 점검 증적) */
    private void saveDaily(SecLogUpload upload, byte[] content, java.util.Map<Integer, Integer> detectedLines, Long creator) {
        int year = Integer.parseInt(upload.getTargetYm().substring(0, 4));
        int month = Integer.parseInt(upload.getTargetYm().substring(4));
        AccessLogAnalyzer.DailyResult result =
                accessLogAnalyzer.analyzeDaily(content, toCharset(upload.getFileCharset()), year, month, detectedLines);
        java.util.Map<java.time.LocalDate, AccessLogAnalyzer.DayStat> days = result.days();
        int outOfMonth = days.entrySet().stream()
                .filter(e -> e.getKey().getYear() != year || e.getKey().getMonthValue() != month)
                .mapToInt(e -> e.getValue().lines).sum();
        upload.applyDateCheck(result.dateUnknownLines(), outOfMonth);
        accessDailyRepository.saveAll(days.entrySet().stream().map(e -> SecLogAccessDaily.builder()
                .uploadId(upload.getUploadId()).systemId(upload.getSystemId()).logDate(e.getKey())
                .lineCount(e.getValue().lines).loginSuccessCount(e.getValue().success).loginFailCount(e.getValue().fail)
                .offHourCount(e.getValue().offHour).privLoginCount(e.getValue().priv).detectionCount(e.getValue().detections)
                .createdBy(creator).build()).toList());
    }

    /**
     * 일별 집계가 없는 업로드(06 스크립트 적용 전 등록분)의 일별 집계를 원본에서 다시 만든다.
     * 탐지 결과·검토 상태는 바꾸지 않는다. 검토완료 건도 대상 (집계만 추가).
     * @return 새로 만든 업로드 수
     */
    @Transactional
    public int rebuildDailyIfMissing(String targetYm, Long userId) {
        validateYm(targetYm);
        // 일별 집계가 없거나 로그 일자 점검(08)이 안 된 업로드
        List<Long> ids = uploadRepository.findByTargetYmAndDeletedYn(targetYm, SecLogConstants.DELETED_N).stream()
                .filter(u -> u.getDateUnknownCount() == null).map(SecLogUpload::getUploadId).toList();
        int n = 0;
        for (Long id : ids) {
            SecLogUploadFile f = uploadFileRepository.findByUploadIdAndDeletedYn(id, SecLogConstants.DELETED_N).orElse(null);
            if (f == null) {
                continue;
            }
            byte[] content = analysisText(findUpload(id).getOriginalFileName(), gunzip(f.getFileData()));
            java.util.Map<Integer, Integer> detected = new java.util.HashMap<>();
            detectionRepository.findLineNumbersByUploadId(id).forEach(ln -> detected.merge(ln, 1, Integer::sum));
            accessDailyRepository.softDeleteByUploadId(id, userId, SecLogTime.now());
            // clearAutomatically 로 detach 되었으므로 재조회
            saveDaily(findUpload(id), content, detected, userId);
            n++;
        }
        return n;
    }

    /** 시스템을 선택했으면 시스템 마스터 이름을, 아니면 입력한 이름을 사용 */
    private String resolveSystemName(Long systemId, String inputName) {
        if (systemId != null) {
            SecLogSystem sys = systemService.findSystem(systemId);
            return sys.getSystemName();
        }
        if (!StringUtils.hasText(inputName)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "시스템을 선택하거나 시스템명을 입력하세요.");
        }
        return inputName.trim();
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

    /** 엑셀이면 분석용 텍스트(TSV, UTF-8)로 변환한다. 원본은 그대로 보관한다. */
    private byte[] analysisText(String fileName, byte[] content) {
        return ExcelTextConverter.isExcel(fileName) ? excelTextConverter.toTsv(content) : content;
    }

    private static byte[] readBytes(MultipartFile file, boolean excel) {
        try {
            byte[] bytes = file.getBytes();
            if (excel) {
                return bytes; // 엑셀은 바이너리(zip) 이므로 NUL 검사 제외, 변환 단계에서 검증
            }
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

    /**
     * 룰별 탐지 건수에 '0건 룰' 추가 (업로드 상세)
     * - 이 업로드의 시스템·로그 유형에 지금 적용되는 활성 룰(공통 + 시스템 전용) 중 탐지가 없는 룰을 hitCount 0 으로 붙임
     * - 순서: 탐지된 룰(건수 많은 순) → 0건 룰(위험도 높은 순, 정렬 순서)
     * - 0건에는 임계 건수 미만으로 매칭된 경우도 포함 (thresholdCount 로 안내)
     */
    private List<RuleHitCount> withZeroHitRules(SecLogUpload upload, List<RuleHitCount> hits) {
        List<SecLogRule> active = ruleService.getActiveRules(upload.getSystemId(), upload.getLogType());
        Map<String, SecLogRule> byCode = new LinkedHashMap<>();
        active.forEach(r -> byCode.putIfAbsent(r.getRuleCode(), r));

        List<RuleHitCount> result = new ArrayList<>();
        java.util.Set<String> hitCodes = new java.util.HashSet<>();
        for (RuleHitCount h : hits) {
            hitCodes.add(h.getRuleCode());
            SecLogRule r = byCode.get(h.getRuleCode());
            result.add(r == null ? h : h.withRule(r));
        }
        byCode.values().stream()
                .filter(r -> !hitCodes.contains(r.getRuleCode()))
                .sorted(java.util.Comparator
                        .comparingInt((SecLogRule r) -> -r.getSeverity().getLevel())
                        .thenComparing(r -> r.getSortOrder() == null ? Integer.MAX_VALUE : r.getSortOrder())
                        .thenComparing(SecLogRule::getRuleCode))
                .forEach(r -> result.add(RuleHitCount.zero(r)));
        return result;
    }

    /** 룰별 검토 상태 건수 채움 (업로드 상세) */
    private List<RuleHitCount> withRuleReviewCounts(Long uploadId, List<RuleHitCount> hits) {
        java.util.Map<String, java.util.Map<ReviewStatus, Long>> byRule = new java.util.HashMap<>();
        for (Object[] r : detectionRepository.countByRuleAndReview(uploadId)) {
            byRule.computeIfAbsent((String) r[0], k -> new java.util.EnumMap<>(ReviewStatus.class))
                    .merge((ReviewStatus) r[1], (Long) r[2], Long::sum);
        }
        return hits.stream()
                .map(h -> h.withReviewCounts(byRule.getOrDefault(h.getRuleCode(), java.util.Map.of())))
                .toList();
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
