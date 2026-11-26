package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.PiiDownloadResponse;
import com.company.module.security_log.dto.PiiDownloadReviewRequest;
import com.company.module.security_log.dto.PiiDownloadReviewResponse;
import com.company.module.security_log.entity.PiiReviewStatus;
import com.company.module.security_log.entity.SecLogPiiDownload;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.repository.SecLogPiiDownloadRepository;
import com.company.module.security_log.repository.SecLogSystemRepository;
import com.company.module.security_log.repository.SecLogUploadRepository;
import com.company.module.security_log.support.SecLogTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 개인정보 다운로드 검토 (자사몰 SVC_MALL · 임직원몰 SVC_EMP_MALL 만 해당)
 * <ul>
 *   <li>업로드한 엑셀에서 '개인정보 다운로드' 탭을 찾아 다운로드 1건 = 1행으로 저장</li>
 *   <li>탭 찾기: 탭 이름 또는 앞쪽 3행에 '개인정보'와 '다운로드'가 모두 있는 탭</li>
 *   <li>머리글: 앞쪽 10행 중 '일시' 열과 '운영자ID(아이디)' 열이 있는 행</li>
 *   <li>검토자: ADMIN, MANAGER / 미검토 건이 남아 있으면 최종 검토완료 불가</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogPiiDownloadService {

    /**
     * 개인정보 다운로드 이력과 연동하는 룰 (시스템 코드 → 룰 코드)
     * - 룰셋별 분석 결과에서 이 룰의 검토 대상·검토 완료에 다운로드 이력 건수를 더하고, 룰을 누르면 다운로드 이력 표시
     */
    public static final Map<String, String> LINKED_RULE = Map.of(
            "SVC_MALL", "MALL_MEMBER_EXPORT",       // 자사몰: 회원 개인정보 엑셀 다운로드
            "SVC_EMP_MALL", "EMALL_MEMBER_EXPORT"); // 임직원몰: 개인정보 엑셀 다운로드
    /** 개인정보 다운로드 검토 대상 시스템 코드 */
    public static final Set<String> TARGET_CODES = LINKED_RULE.keySet();
    /** 연동 룰이 없을 때 룰셋별 분석 결과에 표시할 이름 */
    public static final String FALLBACK_RULE_NAME = "개인정보 다운로드 이력";
    public static final String SHEET_FOUND = "FOUND";
    public static final String SHEET_NOT_FOUND = "NOT_FOUND";
    private static final int MAX_ROWS = 20_000;
    private static final int HEADER_SCAN_ROWS = 10;
    private static final Pattern DATE_TIME = Pattern.compile(
            "(\\d{4})[./\\-년]\\s*(\\d{1,2})[./\\-월]\\s*(\\d{1,2})(?:\\D{0,6}?(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?");

    private final SecLogPiiDownloadRepository piiRepository;
    private final SecLogUploadRepository uploadRepository;
    private final SecLogSystemRepository systemRepository;
    private final ExcelTextConverter excelTextConverter;

    // ------------------------------------------------------------------ 대상 판정

    /** 대상 시스템 여부 (시스템 코드 SVC_MALL / SVC_EMP_MALL) */
    public boolean isTarget(Long systemId) {
        if (systemId == null) {
            return false;
        }
        return systemRepository.findById(systemId)
                .map(s -> s.getSystemCode() != null && TARGET_CODES.contains(s.getSystemCode().trim().toUpperCase(Locale.ROOT)))
                .orElse(false);
    }

    /** 시스템의 연동 룰 코드 (대상 시스템이 아니면 null) */
    public String linkedRuleCode(Long systemId) {
        if (systemId == null) {
            return null;
        }
        return systemRepository.findById(systemId)
                .map(s -> s.getSystemCode() == null ? null : LINKED_RULE.get(s.getSystemCode().trim().toUpperCase(Locale.ROOT)))
                .orElse(null);
    }

    /** 시스템 코드 → 연동 룰 코드 (대상 아니면 null) */
    public static String linkedRuleCodeOf(String systemCode) {
        return systemCode == null ? null : LINKED_RULE.get(systemCode.trim().toUpperCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ 추출 (업로드·재분석)

    /**
     * 원본 파일에서 개인정보 다운로드 내역을 읽어 저장 (업로드·재분석 시 호출, 기존 행은 호출 전에 소프트 삭제)
     * - 대상 시스템이 아니면 탭 읽기 결과를 비움
     * - 읽기 실패는 로그 분석을 막지 않음 (탭 없음으로 기록)
     */
    @Transactional
    public void extract(SecLogUpload upload, String originalName, byte[] original, Long userId) {
        if (!isTarget(upload.getSystemId())) {
            upload.applyPiiSheet(null, null);
            return;
        }
        if (!ExcelTextConverter.isExcel(originalName)) {
            upload.applyPiiSheet(SHEET_NOT_FOUND, null);
            return;
        }
        List<ExcelTextConverter.NamedSheet> sheets;
        try {
            sheets = excelTextConverter.readAllSheets(original);
        } catch (BusinessException e) {
            log.warn("[security_log] 개인정보 다운로드 탭 읽기 실패 uploadId={} : {}", upload.getUploadId(), e.getMessage());
            upload.applyPiiSheet(SHEET_NOT_FOUND, null);
            return;
        }
        ExcelTextConverter.NamedSheet sheet = findPiiSheet(sheets);
        if (sheet == null) {
            upload.applyPiiSheet(SHEET_NOT_FOUND, null);
            return;
        }
        String sheetName = cut(sheet.name(), 100);
        upload.applyPiiSheet(SHEET_FOUND, sheetName);

        List<ExcelTextConverter.SheetRow> rows = sheet.rows();
        int headerIdx = -1;
        Columns col = null;
        for (int i = 0; i < Math.min(HEADER_SCAN_ROWS, rows.size()); i++) {
            Columns c = Columns.of(rows.get(i).cells());
            if (c.date >= 0 && c.admin >= 0) {
                headerIdx = i;
                col = c;
                break;
            }
        }
        if (col == null) {
            log.warn("[security_log] 개인정보 다운로드 탭 머리글(일시·운영자ID)을 찾지 못함 uploadId={}", upload.getUploadId());
            return;
        }
        Long creator = userId != null ? userId : upload.getCreatedBy();
        int saved = 0;
        for (int i = headerIdx + 1; i < rows.size() && saved < MAX_ROWS; i++) {
            ExcelTextConverter.SheetRow r = rows.get(i);
            List<String> cells = r.cells();
            String dateText = cell(cells, col.date);
            String admin = cell(cells, col.admin);
            if (dateText == null && admin == null) {
                continue; // 합계·안내 문구 등
            }
            piiRepository.save(SecLogPiiDownload.builder()
                    .uploadId(upload.getUploadId())
                    .sheetName(sheetName)
                    .rowNo(r.rowNo())
                    .downloadAt(parseDateTime(dateText))
                    .downloadAtText(cut(dateText, 50))
                    .adminId(cut(admin, 200))
                    .accessIp(cut(cell(cells, col.ip), 100))
                    .accessPage(cut(cell(cells, col.page), 500))
                    .taskName(cut(cell(cells, col.task), 500))
                    .fileTarget(cut(cell(cells, col.target), 500))
                    .formName(cut(cell(cells, col.form), 500))
                    .downloadReason(cut(cell(cells, col.reason), 1000))
                    .rawContent(cut(String.join("\t", cells), 2000))
                    .createdBy(creator)
                    .build());
            saved++;
        }
    }

    /** 탭 이름 또는 앞쪽 3행에 '개인정보' + '다운로드' 가 모두 있는 탭 */
    static ExcelTextConverter.NamedSheet findPiiSheet(List<ExcelTextConverter.NamedSheet> sheets) {
        for (ExcelTextConverter.NamedSheet s : sheets) {
            if (isPiiText(s.name())) {
                return s;
            }
        }
        for (ExcelTextConverter.NamedSheet s : sheets) {
            StringBuilder sb = new StringBuilder();
            s.rows().stream().limit(3).forEach(r -> sb.append(String.join(" ", r.cells())).append(' '));
            if (isPiiText(sb.toString())) {
                return s;
            }
        }
        return null;
    }

    private static boolean isPiiText(String text) {
        if (text == null) {
            return false;
        }
        String t = text.replaceAll("\\s", "");
        return t.contains("개인정보") && t.contains("다운로드");
    }

    /** 머리글 열 위치 (없으면 -1) */
    private static final class Columns {
        int date = -1, admin = -1, ip = -1, page = -1, task = -1, target = -1, form = -1, reason = -1;

        static Columns of(List<String> header) {
            Columns c = new Columns();
            for (int i = 0; i < header.size(); i++) {
                String h = header.get(i) == null ? "" : header.get(i).replaceAll("\\s", "").toLowerCase(Locale.ROOT);
                if (h.isEmpty()) {
                    continue;
                }
                if (h.contains("사유") || h.contains("목적")) {
                    c.reason = first(c.reason, i);
                } else if (h.contains("양식") || h.contains("파일명")) {
                    c.form = first(c.form, i);
                } else if (h.contains("대상")) {
                    c.target = first(c.target, i);
                } else if (h.contains("업무") || h.contains("행위")) {
                    c.task = first(c.task, i);
                } else if (h.contains("페이지") || h.contains("메뉴") || h.contains("url")) {
                    c.page = first(c.page, i);
                } else if (h.contains("ip")) {
                    c.ip = first(c.ip, i);
                } else if (h.contains("일시") || h.contains("일자") || h.contains("시간") || h.contains("date")) {
                    c.date = first(c.date, i);
                } else if (h.contains("운영자") || h.contains("관리자") || h.contains("아이디") || h.contains("계정")
                        || h.contains("사용자") || h.equals("id") || h.endsWith("id")) {
                    c.admin = first(c.admin, i);
                }
            }
            return c;
        }

        private static int first(int cur, int i) {
            return cur >= 0 ? cur : i;
        }
    }

    /** '2026/10/30 (15:34)', '2026-10-30 15:34:00', '2026.10.30' 등 → 일시 (못 읽으면 null) */
    static LocalDateTime parseDateTime(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = DATE_TIME.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            int y = Integer.parseInt(m.group(1));
            int mo = Integer.parseInt(m.group(2));
            int d = Integer.parseInt(m.group(3));
            int hh = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
            int mi = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
            int ss = m.group(6) == null ? 0 : Integer.parseInt(m.group(6));
            return LocalDateTime.of(y, mo, d, hh, mi, ss);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String cell(List<String> cells, int idx) {
        if (idx < 0 || idx >= cells.size()) {
            return null;
        }
        String v = cells.get(idx);
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private static String cut(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }

    // ------------------------------------------------------------------ 조회 · 검토

    public PiiDownloadResponse list(Long uploadId) {
        SecLogUpload upload = findUpload(uploadId);
        boolean target = isTarget(upload.getSystemId());
        List<SecLogPiiDownload> rows = target
                ? piiRepository.findByUploadIdAndDeletedYnOrderByRowNoAsc(uploadId, SecLogConstants.DELETED_N)
                : List.of();
        Map<PiiReviewStatus, Long> cnt = new HashMap<>();
        rows.forEach(p -> cnt.merge(p.getReviewStatus(), 1L, Long::sum));
        return PiiDownloadResponse.builder()
                .uploadId(uploadId).target(target).ruleCode(target ? linkedRuleCode(upload.getSystemId()) : null)
                .sheetStatus(upload.getPiiSheetStatus()).sheetName(upload.getPiiSheetName())
                .locked(upload.isReviewed())
                .totalCount(rows.size())
                .pendingCount(cnt.getOrDefault(PiiReviewStatus.PENDING, 0L))
                .normalCount(cnt.getOrDefault(PiiReviewStatus.NORMAL, 0L))
                .confirmedCount(cnt.getOrDefault(PiiReviewStatus.CONFIRMED, 0L))
                .resolvedCount(cnt.getOrDefault(PiiReviewStatus.RESOLVED, 0L))
                .items(rows.stream().map(PiiDownloadResponse.Item::from).toList())
                .build();
    }

    /** 선택한 다운로드 건 일괄 검토 (PENDING 이면 미검토로 되돌림) */
    @Transactional
    public PiiDownloadReviewResponse review(Long uploadId, PiiDownloadReviewRequest request, Long userId) {
        SecLogUpload upload = findUpload(uploadId);
        if (upload.isReviewed()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "검토완료된 건은 변경할 수 없습니다. 검토취소 후 진행하세요.");
        }
        if (!isTarget(upload.getSystemId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "개인정보 다운로드 검토는 자사몰·임직원몰 로그만 해당합니다.");
        }
        PiiReviewStatus status;
        try {
            status = PiiReviewStatus.valueOf(request.getReviewStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "검토 상태는 NORMAL(정상 업무), CONFIRMED(사유 불명확·조치 필요), RESOLVED(조치완료), PENDING(미검토로 되돌리기) 중 하나입니다.");
        }
        String comment = StringUtils.hasText(request.getReviewComment()) ? request.getReviewComment().trim() : null;
        if (status.commentRequired() && comment == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "'" + status.getLabel() + "'은(는) 다운로드 사유·확인 내용을 입력해야 합니다.");
        }
        Set<Long> ids = new LinkedHashSet<>();
        request.getPiiDownloadIds().stream().filter(java.util.Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "검토할 다운로드 건을 선택하세요.");
        }
        Map<Long, SecLogPiiDownload> current = new HashMap<>();
        piiRepository.findByUploadIdAndDeletedYnOrderByRowNoAsc(uploadId, SecLogConstants.DELETED_N)
                .forEach(p -> current.put(p.getPiiDownloadId(), p));
        List<Long> unknown = ids.stream().filter(id -> !current.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "이 로그의 개인정보 다운로드 건이 아닙니다: " + unknown.subList(0, Math.min(5, unknown.size()))
                            + (unknown.size() > 5 ? " 외 " + (unknown.size() - 5) + "건" : ""));
        }
        ids.forEach(id -> current.get(id).review(status, comment, userId));
        piiRepository.flush();
        return PiiDownloadReviewResponse.builder().uploadId(uploadId).updatedCount(ids.size())
                .pendingCount(pendingCount(uploadId)).build();
    }

    /** 업로드의 미검토 다운로드 건수 */
    public long pendingCount(Long uploadId) {
        return piiRepository.countByUploadIdAndReviewStatusAndDeletedYn(uploadId, PiiReviewStatus.PENDING, SecLogConstants.DELETED_N);
    }

    /** 업로드별 [전체 건수, 미검토 건수] (행이 없는 업로드는 생략) */
    public Map<Long, long[]> countsByUploads(Collection<Long> uploadIds) {
        Map<Long, long[]> m = new HashMap<>();
        if (uploadIds == null || uploadIds.isEmpty()) {
            return m;
        }
        for (Object[] r : piiRepository.countByUploads(uploadIds)) {
            m.put((Long) r[0], new long[]{((Number) r[1]).longValue(), r[2] == null ? 0L : ((Number) r[2]).longValue()});
        }
        return m;
    }

    /** 재분석·업로드 삭제 시 그 업로드의 다운로드 내역을 소프트 삭제 */
    @Transactional
    public void softDeleteByUpload(Long uploadId, Long userId) {
        piiRepository.softDeleteByUploadId(uploadId, userId, SecLogTime.now());
    }

    private SecLogUpload findUpload(Long uploadId) {
        return uploadRepository.findByUploadIdAndDeletedYn(uploadId, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
