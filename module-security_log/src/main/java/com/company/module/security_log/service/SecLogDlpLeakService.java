package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.DlpLeakMonthlyResponse;
import com.company.module.security_log.dto.DlpLeakUploadRequest;
import com.company.module.security_log.dto.DlpLeakUploadResponse;
import com.company.module.security_log.dto.OriginalFileResponse;
import com.company.module.security_log.entity.SecLogDlpLeakEvent;
import com.company.module.security_log.entity.SecLogDlpLeakFile;
import com.company.module.security_log.entity.SecLogDlpLeakUpload;
import com.company.module.security_log.repository.SecLogDlpLeakEventRepository;
import com.company.module.security_log.repository.SecLogDlpLeakFileRepository;
import com.company.module.security_log.repository.SecLogDlpLeakUploadRepository;
import com.company.module.security_log.repository.SecLogDlpPrivacyiRowRepository;
import com.company.module.security_log.repository.SecLogDlpReportItemRepository;
import com.company.module.security_log.support.SecLogTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * DLP 모니터링 (월 1회)
 * <ol>
 *   <li>DLP 관리시스템에서 내려받은 유출 로그(엑셀/CSV)를 업로드 → 원본 GZIP 보관</li>
 *   <li>머리글(발생 일시·사용자 이름/아이디·채널 등)을 찾아 1행 = 유출 이벤트 1건으로 저장</li>
 *   <li>생성형 AI 서비스 판정(GenAiCatalog) — 룰셋 없음 (룰 기반 점검은 ISMS 로그 점검에서)</li>
 *   <li>보고서 작성 화면 · 카카오톡 사용자 현황 · DLP 모니터링 보고서(PPT) 가 이 이벤트로 계산</li>
 * </ol>
 * <ul>
 *   <li>'... 샘플 데이터' 열(개인정보 원문 일부)은 저장하지 않음 (개수 열만 합계·항목)</li>
 *   <li>같은 달 여러 번 올리면 가장 최근 파일 1개만 집계 (이전 파일은 보관만)</li>
 *   <li>업로드: ADMIN, MANAGER / 삭제: ADMIN / 조회: 전원</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogDlpLeakService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int MAX_EVENTS = 200_000;
    private static final int HEADER_SCAN_ROWS = 15;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PII_TOTAL_LABEL = "개인정보 합계";

    private final SecLogDlpLeakUploadRepository uploadRepository;
    private final SecLogDlpLeakFileRepository fileRepository;
    private final SecLogDlpLeakEventRepository eventRepository;
    private final ExcelTextConverter excelTextConverter;
    private final SecLogDlpReportItemRepository itemRepository;
    private final SecLogDlpPrivacyiRowRepository privacyiRepository;

    // ================================================================== 업로드 · 재분석 · 삭제

    @Transactional
    public DlpLeakUploadResponse upload(DlpLeakUploadRequest request, MultipartFile file, Long userId) {
        String name = validateFile(file);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드 파일을 읽을 수 없습니다.");
        }
        if (content.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파일 내용이 비어 있습니다.");
        }
        String hash = sha256(content);
        if (uploadRepository.existsByTargetYmAndFileHashAndDeletedYn(request.getTargetYm(), hash, SecLogConstants.DELETED_N)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "같은 달에 같은 내용의 DLP 유출 로그 파일이 이미 올라와 있습니다.");
        }
        // 형식 확인을 먼저 (형식이 아니면 저장하지 않음)
        Parsed parsed = parse(name, content);

        SecLogDlpLeakUpload upload = uploadRepository.save(SecLogDlpLeakUpload.builder()
                .targetYm(request.getTargetYm())
                .originalFileName(name)
                .fileSize((long) content.length)
                .fileHash(hash)
                .remark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null)
                .createdBy(userId)
                .build());
        fileRepository.save(SecLogDlpLeakFile.builder()
                .leakUploadId(upload.getLeakUploadId()).fileData(gzip(content)).createdBy(userId).build());
        analyze(upload, parsed, userId);
        return DlpLeakUploadResponse.from(upload);
    }

    /** 소프트 삭제 (업로드 + 원본 + 이벤트) - 관리자 */
    @Transactional
    public void delete(Long leakUploadId, Long userId) {
        findUpload(leakUploadId);
        eventRepository.softDeleteByUploadId(leakUploadId, userId, SecLogTime.now());
        fileRepository.findByLeakUploadIdAndDeletedYn(leakUploadId, SecLogConstants.DELETED_N).ifPresent(f -> f.delete(userId));
        findUpload(leakUploadId).delete(userId);
    }

    public OriginalFileResponse getOriginalFile(Long leakUploadId) {
        SecLogDlpLeakUpload upload = findUpload(leakUploadId);
        SecLogDlpLeakFile file = fileRepository.findByLeakUploadIdAndDeletedYn(leakUploadId, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        byte[] content = gunzip(file.getFileData());
        return OriginalFileResponse.builder().uploadId(leakUploadId).fileName(upload.getOriginalFileName())
                .fileSize(content.length).contentBase64(Base64.getEncoder().encodeToString(content)).build();
    }

    // ================================================================== 조회

    /** 그 달 올린 파일 (최근 순, 첫 번째 = 보고서 기준) */
    public DlpLeakMonthlyResponse getMonthly(String targetYm) {
        if (targetYm == null || !targetYm.matches("^\\d{4}(0[1-9]|1[0-2])$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "대상 연월은 YYYYMM 형식입니다.");
        }
        List<DlpLeakUploadResponse> files = uploadRepository
                .findByTargetYmAndDeletedYnOrderByLeakUploadIdDesc(targetYm, SecLogConstants.DELETED_N)
                .stream().map(DlpLeakUploadResponse::from).toList();
        DlpLeakMonthlyResponse.Agent agent = itemRepository
                .findByTargetYmAndSectionAndItemKey(targetYm, SecLogDlpMonitorReportService.S_AGENT, SecLogDlpMonitorReportService.AGENT_KEY)
                .filter(i -> SecLogConstants.DELETED_N.equals(i.getDeletedYn()))
                .map(i -> DlpLeakMonthlyResponse.Agent.builder()
                        .fileName(i.getValue2())
                        .uploadedAt(i.getUpdatedAt() != null ? i.getUpdatedAt() : i.getCreatedAt())
                        .totalCount(parseIntOrNull(i.getValue1()))
                        .reinstallCount(privacyiRepository.findByTargetYmAndKindAndDeletedYnOrderByRowNoAsc(
                                targetYm, SecLogDlpMonitorReportService.KIND_REINSTALL, SecLogConstants.DELETED_N).size())
                        .offlineCount(privacyiRepository.findByTargetYmAndKindAndDeletedYnOrderByRowNoAsc(
                                targetYm, SecLogDlpMonitorReportService.KIND_OFFLINE, SecLogConstants.DELETED_N).size())
                        .build())
                .orElse(null);
        return DlpLeakMonthlyResponse.builder()
                .targetYm(targetYm).upload(files.isEmpty() ? null : files.get(0)).files(files).agent(agent).build();
    }

    private static Integer parseIntOrNull(String v) {
        try {
            return v == null ? null : Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ================================================================== 분석

    /** 파싱 결과 → 이벤트 저장(생성형 AI 판정 포함) + 업로드 요약 반영 */
    private void analyze(SecLogDlpLeakUpload upload, Parsed p, Long userId) {
        YearMonth ym = YearMonth.parse(upload.getTargetYm(), DateTimeFormatter.ofPattern("yyyyMM"));
        Long creator = userId != null ? userId : upload.getCreatedBy();
        List<SecLogDlpLeakEvent> out = new ArrayList<>(p.rows().size());
        Set<String> users = new HashSet<>();
        int unknown = 0;
        int outside = 0;
        LocalDateTime from = null;
        LocalDateTime to = null;
        for (int i = 0; i < p.rows().size(); i++) {
            Row r = p.rows().get(i);
            LocalDateTime at = r.occurredAt();
            if (at == null) {
                unknown++;
            } else {
                if (!YearMonth.from(at).equals(ym)) {
                    outside++;
                }
                from = from == null || at.isBefore(from) ? at : from;
                to = to == null || at.isAfter(to) ? at : to;
            }
            String uk = r.get(F.USER_ID) != null ? r.get(F.USER_ID) : r.get(F.USER_NAME);
            if (uk != null) {
                users.add(uk);
            }
            out.add(SecLogDlpLeakEvent.builder()
                    .leakUploadId(upload.getLeakUploadId()).rowNo(r.rowNo())
                    .occurredAt(at).occurredAtText(cut(r.get(F.OCCURRED_AT), 50))
                    .actionName(cut(r.get(F.ACTION), 100)).deptName(cut(r.get(F.DEPT), 200))
                    .userName(cut(r.get(F.USER_NAME), 200)).userId(cut(r.get(F.USER_ID), 200))
                    .agentIp(cut(r.get(F.AGENT_IP), 100)).channel(cut(r.get(F.CHANNEL), 100)).subChannel(cut(r.get(F.SUB_CHANNEL), 200))
                    .fileSizeText(cut(r.get(F.FILE_SIZE), 50)).fileList(cut(r.get(F.FILE_LIST), 1000))
                    .content(cut(r.get(F.CONTENT), 2000)).detail(cut(r.get(F.DETAIL), 1000))
                    .policyName(cut(r.get(F.POLICY), 200)).eventStatus(cut(r.get(F.STATUS), 50))
                    .mediaRegistered(cut(r.get(F.MEDIA), 50)).computerName(cut(r.get(F.COMPUTER), 200))
                    .piiTotal(r.piiTotal()).piiDetail(cut(r.piiDetail(), 500))
                    .aiService(GenAiCatalog.detect(r.get(F.AI), r.get(F.DETAIL), r.get(F.CONTENT), r.get(F.SUB_CHANNEL)))
                    .analysisLine(r.analysisLine()).createdBy(creator)
                    .build());
        }
        eventRepository.saveAll(out);
        List<String> notes = new ArrayList<>();
        if (!p.unknownColumns().isEmpty()) {
            notes.add("인식하지 못한 열 " + p.unknownColumns().size() + "개 (분석용 줄에는 포함): "
                    + String.join(", ", p.unknownColumns().subList(0, Math.min(5, p.unknownColumns().size()))));
        }
        // 룰셋 없음 → 해당 건수·적용 룰 수 칸(MATCHED_EVENT_COUNT, APPLIED_RULE_COUNT)은 0 으로 둠 (컬럼은 기존 데이터 보존용)
        upload.applyAnalysis(p.sheetName(), p.headerRow(), out.size(), 0, users.size(), from, to, unknown, outside,
                0, notes.isEmpty() ? null : cut(String.join(" / ", notes), 1000), userId);
    }

    // ================================================================== 파일 읽기

    /** 표준 필드 (원본 머리글 → 필드) */
    enum F { OCCURRED_AT, ACTION, DEPT, USER_NAME, USER_ID, AGENT_IP, CHANNEL, SUB_CHANNEL, FILE_SIZE, FILE_LIST,
        CONTENT, DETAIL, POLICY, STATUS, MEDIA, COMPUTER, AI }

    private static final Map<String, F> ALIAS = new LinkedHashMap<>();

    static {
        alias(F.OCCURRED_AT, "발생일시", "발생시각", "이벤트일시", "탐지일시", "일시");
        alias(F.ACTION, "대응행동", "대응", "처리결과", "조치");
        alias(F.DEPT, "부서이름", "부서명", "부서");
        alias(F.USER_NAME, "사용자이름", "사용자명", "이름", "성명");
        alias(F.USER_ID, "사용자아이디", "사용자id", "아이디", "계정");
        alias(F.AGENT_IP, "에이전트ip", "ip주소", "ip", "단말ip");
        alias(F.CHANNEL, "채널", "유출채널", "반출채널");
        alias(F.SUB_CHANNEL, "서브채널", "세부채널");
        alias(F.FILE_SIZE, "파일크기", "크기");
        alias(F.FILE_LIST, "파일목록", "파일명", "파일이름");
        alias(F.CONTENT, "내용");
        alias(F.DETAIL, "상세내용", "상세");
        alias(F.POLICY, "정책이름", "정책명", "정책");
        alias(F.STATUS, "상태");
        alias(F.MEDIA, "이동식저장매체등록여부", "매체등록여부");
        alias(F.COMPUTER, "컴퓨터이름", "pc이름", "호스트명");
        alias(F.AI, "ai", "ai서비스", "생성형ai", "aiservice");
    }

    private static void alias(F f, String... names) {
        for (String n : names) {
            ALIAS.put(n, f);
        }
    }

    /** 원본 1행 (fields = 표준 필드 값, analysisLine = 기준 적용 대상 한 줄) */
    record Row(int rowNo, Map<F, String> fields, LocalDateTime occurredAt, int piiTotal, String piiDetail, String analysisLine) {
        String get(F f) {
            return fields.get(f);
        }
    }

    record Parsed(String sheetName, int headerRow, List<Row> rows, List<String> unknownColumns) {
    }

    /** 표 형식 (머리글 + 데이터 행) */
    private record Table(String sheetName, List<ExcelTextConverter.SheetRow> rows) {
    }

    Parsed parse(String fileName, byte[] content) {
        List<Table> tables = new ArrayList<>();
        if (ExcelTextConverter.isExcel(fileName)) {
            excelTextConverter.readAllSheets(content).forEach(s -> tables.add(new Table(s.name(), s.rows())));
        } else {
            tables.add(new Table(null, csvRows(content)));
        }
        for (Table t : tables) {
            List<ExcelTextConverter.SheetRow> rows = t.rows();
            for (int i = 0; i < Math.min(HEADER_SCAN_ROWS, rows.size()); i++) {
                List<String> header = rows.get(i).cells();
                Map<Integer, F> col = mapColumns(header);
                boolean hasDate = col.containsValue(F.OCCURRED_AT);
                boolean hasUser = col.containsValue(F.USER_ID) || col.containsValue(F.USER_NAME);
                if (hasDate && hasUser) {
                    return toParsed(t.sheetName(), rows, i, header, col);
                }
            }
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                "DLP 유출 로그 형식이 아닙니다. 머리글에 '발생 일시'와 '사용자 이름' 또는 '사용자 아이디' 열이 있어야 합니다.");
    }

    private static Map<Integer, F> mapColumns(List<String> header) {
        Map<Integer, F> col = new LinkedHashMap<>();
        Set<F> used = new HashSet<>();
        for (int c = 0; c < header.size(); c++) {
            String h = norm(header.get(c));
            F f = ALIAS.get(h);
            if (f != null && used.add(f)) {   // 같은 필드가 두 번 나오면(예: 상세 내용) 첫 번째만
                col.put(c, f);
            }
        }
        return col;
    }

    private Parsed toParsed(String sheetName, List<ExcelTextConverter.SheetRow> rows, int headerIdx, List<String> header, Map<Integer, F> col) {
        int n = header.size();
        boolean[] sample = new boolean[n];
        boolean[] piiCount = new boolean[n];
        String[] label = new String[n];
        Map<String, Integer> seen = new HashMap<>();
        List<String> unknown = new ArrayList<>();
        for (int c = 0; c < n; c++) {
            String h = header.get(c) == null ? "" : header.get(c).trim();
            String nh = norm(h);
            sample[c] = nh.endsWith("샘플데이터");
            piiCount[c] = nh.endsWith("개수") && !nh.equals("패턴개수");
            int k = seen.merge(h, 1, Integer::sum);
            label[c] = h.isEmpty() ? "열" + (c + 1) : k > 1 ? h + "(" + k + ")" : h;
            if (!h.isEmpty() && !sample[c] && !piiCount[c] && !col.containsKey(c) && !KNOWN_EXTRA.contains(nh) && k == 1) {
                unknown.add(h);
            }
        }
        List<Row> out = new ArrayList<>();
        for (int i = headerIdx + 1; i < rows.size(); i++) {
            ExcelTextConverter.SheetRow sr = rows.get(i);
            List<String> cells = sr.cells();
            if (cells.stream().allMatch(v -> v == null || v.isBlank())) {
                continue;
            }
            if (out.size() >= MAX_EVENTS) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "DLP 유출 로그는 한 번에 최대 " + String.format("%,d", MAX_EVENTS) + "건까지 올릴 수 있습니다. 기간을 나눠 올려 주세요.");
            }
            Map<F, String> f = new LinkedHashMap<>();
            col.forEach((c, field) -> {
                String v = c < cells.size() ? cells.get(c) : null;
                if (StringUtils.hasText(v)) {
                    f.put(field, v.trim());
                }
            });
            String dtText = f.get(F.OCCURRED_AT);
            LocalDateTime at = SecLogPiiDownloadService.parseDateTime(dtText);
            int piiTotal = 0;
            List<String> piiParts = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < n; c++) {
                if (sample[c]) {
                    continue;   // 개인정보 원문 일부 - 저장·분석하지 않음
                }
                String v = c < cells.size() && cells.get(c) != null ? cells.get(c).trim() : "";
                if (piiCount[c]) {
                    int cnt = toInt(v);
                    if (cnt > 0) {
                        piiTotal += cnt;
                        piiParts.add(label[c].replaceAll("\\s*개수$", "") + " " + cnt);
                    }
                }
                if (col.get(c) == F.OCCURRED_AT && at != null) {
                    v = at.format(DT);   // 기준(정규식)에서 쓰기 쉽게 yyyy-MM-dd HH:mm:ss 로 맞춤
                }
                if (line.length() > 0) {
                    line.append(" | ");
                }
                line.append(label[c]).append('=').append(v.replace('|', '/'));
            }
            line.append(" | ").append(PII_TOTAL_LABEL).append('=').append(piiTotal);
            out.add(new Row(sr.rowNo(), f, at, piiTotal, piiParts.isEmpty() ? null : String.join(", ", piiParts), line.toString()));
        }
        return new Parsed(sheetName, rows.get(headerIdx).rowNo(), out, unknown);
    }

    /** 표준 필드는 아니지만 알고 있는 열 (인식 못 한 열 안내에서 제외) */
    private static final Set<String> KNOWN_EXTRA = Set.of("미디어아이디", "패턴개수", "패턴정보", "로그저장일시");

    /** CSV/TSV → 행 목록 (UTF-8, 실패하면 MS949) */
    static List<ExcelTextConverter.SheetRow> csvRows(byte[] content) {
        String text = decode(content);
        String[] lines = text.split("\\r?\\n");
        List<ExcelTextConverter.SheetRow> out = new ArrayList<>();
        char sep = 0;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (l.isBlank()) {
                continue;
            }
            if (sep == 0) {
                sep = CsvSplit.detectSeparator(l);
            }
            out.add(new ExcelTextConverter.SheetRow(i + 1, CsvSplit.split(l, sep)));
        }
        if (out.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파일에 데이터가 없습니다.");
        }
        return out;
    }

    private static String decode(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, Charset.forName("MS949"));
        }
    }

    // ================================================================== 공통

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드할 DLP 유출 로그 파일이 없습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파일은 최대 50MB 까지 올릴 수 있습니다.");
        }
        String name = file.getOriginalFilename();
        String cleaned = name == null ? null : StringUtils.getFilename(StringUtils.cleanPath(name).replace('\\', '/'));
        if (!StringUtils.hasText(cleaned) || cleaned.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파일명이 올바르지 않습니다(최대 255자).");
        }
        String ext = StringUtils.getFilenameExtension(cleaned);
        String e = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (!Set.of("xlsx", "xls", "csv", "tsv", "txt").contains(e)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "DLP 유출 로그는 엑셀(.xlsx, .xls) 또는 CSV(.csv, .tsv, .txt) 파일만 올릴 수 있습니다.");
        }
        return cleaned;
    }

    private SecLogDlpLeakUpload findUpload(Long id) {
        return uploadRepository.findByLeakUploadIdAndDeletedYn(id, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static String norm(String h) {
        return h == null ? "" : h.replaceAll("[\\s_\\-]", "").toLowerCase(Locale.ROOT);
    }

    private static int toInt(String v) {
        if (v == null || v.isBlank()) {
            return 0;
        }
        String d = v.replaceAll("[^0-9]", "");
        if (d.isEmpty() || d.length() > 9) {
            return 0;
        }
        return Integer.parseInt(d);
    }

    private static String cut(String v, int max) {
        return v == null ? null : v.length() <= max ? v : v.substring(0, max);
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest(content)) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
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
}
