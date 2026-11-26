package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.DlpLogRowResponse;
import com.company.module.security_log.dto.DlpReportResponse;
import com.company.module.security_log.entity.ReviewStatus;
import com.company.module.security_log.entity.SecLogDetection;
import com.company.module.security_log.entity.SecLogRule;
import com.company.module.security_log.entity.SecLogSystem;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.entity.SecLogUploadFile;
import com.company.module.security_log.entity.Severity;
import com.company.module.security_log.entity.MatchType;
import com.company.module.security_log.entity.SecLogInternalIp;
import com.company.module.security_log.repository.SecLogDetectionRepository;
import com.company.module.security_log.repository.SecLogInternalIpRepository;
import com.company.module.security_log.repository.SecLogRuleRepository;
import com.company.module.security_log.repository.SecLogSystemRepository;
import com.company.module.security_log.repository.SecLogUploadFileRepository;
import com.company.module.security_log.repository.SecLogUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * DLP 관리자 감사 로그 점검 보고서 (탐지 룰셋 기반)
 *
 * <p>[관리 › 탐지 룰셋 편집]에서 이 시스템에 적용된 룰로 탐지한 결과(sec_log_detection)를
 * '룰 × 계정' 단위로 묶어 보고서로 보여 준다. 판정 기준은 룰셋 하나뿐이다.
 * <ul>
 *   <li>위험도 = 룰에 지정한 위험도 (심각·높음 → 🔴 높음, 보통 → 🟡, 낮음 → 🟢)</li>
 *   <li>항목 검토 = 그 항목에 묶인 탐지 건 검토 (최종 검토완료 조건 '미검토 탐지 0건'과 같은 기록)</li>
 *   <li>근거 로그는 원본 파일을 다시 읽어 엑셀 행 번호(텍스트는 줄 번호)와 계정·시각을 붙인다.</li>
 *   <li>로그 안의 지시문처럼 보이는 문장은 따르지 않고 "로그 내 의심 문구" 로만 보고한다.</li>
 * </ul>
 * 보고서는 DB 에 따로 저장하지 않는다 (탐지 결과 + 원본 파일로 매번 계산).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogDlpReportService {

    private static final String LV_HIGH = "HIGH";
    private static final String LV_MEDIUM = "MEDIUM";
    private static final String LV_LOW = "LOW";
    private static final int MAX_EVIDENCE = 200;
    private static final int MAX_VIEW_ROWS = 200;
    private static final String NO_ACCOUNT = "-";

    /** 로그 안 지시문처럼 보이는 문장 (엄격한 패턴만) */
    private static final List<Pattern> SUSPICIOUS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(the\\s+)?(previous|above|prior)\\s+(instructions?|prompts?)"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(previous|above|prior)"),
            Pattern.compile("(?i)system\\s+prompt"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+"),
            Pattern.compile("시스템\\s*프롬프트"),
            Pattern.compile("(이전|위의?|앞의?)\\s*(지시|명령|지침)[을를]?\\s*(모두\\s*)?무시"),
            Pattern.compile("무시하(고|세요|라|십시오)"),
            Pattern.compile("\\bAI\\s*(에게|는|야|여|님)"),
            Pattern.compile("(보고서|결과)에서\\s*(제외|삭제|빼)"),
            Pattern.compile("(정상|이상\\s*없음)으로\\s*(판정|처리|보고|표시)하"));

    private static final DateTimeFormatter[] TIME_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")};
    /** 날짜 + 시각 (시·분·초 1~2자리, 초·밀리초 생략 가능) */
    private static final Pattern LOOSE_TIME = Pattern.compile(
            "(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})[ T](\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?(?:\\.\\d+)?");
    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUT_M = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter MDHM = DateTimeFormatter.ofPattern("M/d HH:mm");

    private final SecLogUploadRepository uploadRepository;
    private final SecLogUploadFileRepository uploadFileRepository;
    private final SecLogSystemRepository systemRepository;
    private final SecLogDetectionRepository detectionRepository;
    private final SecLogRuleRepository ruleRepository;
    private final SecLogRuleService ruleService;
    private final ExcelTextConverter excelTextConverter;
    private final SecLogInternalIpRepository internalIpRepository;

    // ================================================================== API

    /** 업로드 1건의 DLP 점검 보고서 */
    public DlpReportResponse getReport(Long uploadId) {
        SecLogUpload upload = findUpload(uploadId);
        DlpReportResponse.DlpReportResponseBuilder base = DlpReportResponse.builder()
                .uploadId(uploadId)
                .systemName(upload.getSystemName())
                .targetYm(upload.getTargetYm())
                .fileName(upload.getOriginalFileName());

        if (!isDlpSystem(upload)) {
            return base.applicable(false).reason("DLP 시스템 로그가 아니어서 점검 보고서를 만들지 않습니다.").build();
        }
        Parsed p = parse(upload);
        if (p == null) {
            return base.applicable(false)
                    .reason("DLP 관리자 감사 로그 형식(발생 일시·아이디·유형·내용 열)이 아니어서 점검 보고서를 만들 수 없습니다.")
                    .build();
        }
        if (p.events.isEmpty()) {
            return base.applicable(false).reason("발생 일시를 읽을 수 있는 로그가 없습니다.").build();
        }
        List<SecLogDetection> dets = detectionRepository
                .findByUploadIdAndDeletedYnOrderByLineNumberAsc(uploadId, SecLogConstants.DELETED_N);
        List<SecLogRule> active = ruleService.getActiveRules(upload.getSystemId(), upload.getLogType());
        Map<Long, SecLogRule> ruleById = new HashMap<>();
        active.forEach(r -> ruleById.put(r.getRuleId(), r));
        Set<Long> missing = dets.stream().map(SecLogDetection::getRuleId)
                .filter(Objects::nonNull).filter(id -> !ruleById.containsKey(id)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            ruleRepository.findAllById(missing).forEach(r -> ruleById.put(r.getRuleId(), r));
        }
        List<SecLogInternalIp> ips = internalIpRepository.findByDeletedYnOrderBySortOrderAscStartIpNumAsc(SecLogConstants.DELETED_N);
        return new Report(p, upload, dets, active, ruleById, ips).build(base);
    }

    /** 근거 로그 원문 보기 (원본 행 번호 기준, 최대 200줄) */
    public List<DlpLogRowResponse> getRows(Long uploadId, Collection<Integer> rows) {
        SecLogUpload upload = findUpload(uploadId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (rows.size() > MAX_VIEW_ROWS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "근거 로그는 한 번에 200줄까지 볼 수 있습니다.");
        }
        Parsed p = isDlpSystem(upload) ? parse(upload) : null;
        if (p == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "DLP 관리자 감사 로그가 아니어서 근거 로그를 볼 수 없습니다.");
        }
        Set<Integer> want = new HashSet<>(rows);
        Map<Integer, Ev> byRow = new LinkedHashMap<>();
        for (Ev e : p.events) {
            if (want.contains(e.row)) {
                byRow.put(e.row, e);
            }
        }
        List<DlpLogRowResponse> out = new ArrayList<>();
        for (Integer r : new LinkedHashSet<>(rows)) {
            Ev e = byRow.get(r);
            if (e != null) {
                out.add(DlpLogRowResponse.builder()
                        .row(e.row).time(e.t.format(OUT)).account(e.acc).name(e.name).ip(e.ip)
                        .type(e.type).content(e.content).detail(e.detail).build());
            }
        }
        return out;
    }

    /**
     * 탐지 줄 번호 → 원본 로그 행 (룰셋 분석 근거 화면용)
     * - DLP 관리자 감사 로그가 아니거나 읽을 수 있는 행이 없으면 null (= 점검 보고서 미적용과 같은 기준)
     */
    public DlpRows rowsByEngineLine(Long uploadId) {
        SecLogUpload upload = findUpload(uploadId);
        if (!isDlpSystem(upload)) {
            return null;
        }
        Parsed p = parse(upload);
        if (p == null || p.events.isEmpty()) {
            return null;
        }
        Map<Integer, DlpLogRowResponse> m = new HashMap<>();
        for (Ev e : p.events) {
            m.put(e.line, DlpLogRowResponse.builder()
                    .row(e.row).time(e.t.format(OUT)).account(e.acc).name(e.name).ip(e.ip)
                    .type(e.type).content(e.content).detail(e.detail).build());
        }
        return new DlpRows(m, p.excel ? "원본 엑셀 행" : "원본 파일 줄");
    }

    /** 탐지 줄 번호별 원본 행 + 행 번호 이름 */
    public record DlpRows(Map<Integer, DlpLogRowResponse> byLine, String rowLabel) {
    }

    // ================================================================== 파일 읽기

    /** row = 원본 행 번호(엑셀 행 / 텍스트 줄), line = 탐지 엔진 줄 번호(엑셀은 변환된 TSV 줄) */
    private record Ev(int row, int line, LocalDateTime t, String acc, String name, String ip, String type, String content,
                      String detail) {
    }

    private record Parsed(List<Ev> events, boolean excel, boolean hasDetail) {
    }

    private SecLogUpload findUpload(Long uploadId) {
        return uploadRepository.findByUploadIdAndDeletedYn(uploadId, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private boolean isDlpSystem(SecLogUpload upload) {
        String code = upload.getSystemId() == null ? null : systemRepository
                .findBySystemIdAndDeletedYn(upload.getSystemId(), SecLogConstants.DELETED_N)
                .map(SecLogSystem::getSystemCode).orElse(null);
        return isDlp(code, upload.getSystemName());
    }

    /** DLP 시스템 여부 (시스템 코드 또는 이름에 'DLP') — 점검 보고서 화면 대상 */
    public static boolean isDlp(String systemCode, String systemName) {
        return (systemCode != null && systemCode.toUpperCase(Locale.ROOT).contains("DLP"))
                || (systemName != null && systemName.toUpperCase(Locale.ROOT).contains("DLP"));
    }

    /** 원본 파일 → 이벤트 목록 (머리글이 DLP 관리자 감사 로그 형식이 아니면 null) */
    private Parsed parse(SecLogUpload upload) {
        SecLogUploadFile file = uploadFileRepository
                .findByUploadIdAndDeletedYn(upload.getUploadId(), SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        byte[] content = gunzip(file.getFileData());
        boolean excel = ExcelTextConverter.isExcel(upload.getOriginalFileName());

        List<ExcelTextConverter.SheetRow> rows;
        if (excel) {
            rows = excelTextConverter.readRows(content);
        } else {
            rows = textRows(content, toCharset(upload.getFileCharset()));
        }

        // 머리글 찾기 (앞쪽 30행 안)
        int hIdx = -1;
        Map<String, Integer> col = null;
        for (int i = 0; i < Math.min(30, rows.size()); i++) {
            Map<String, Integer> c = headerColumns(rows.get(i).cells());
            if (c != null) {
                hIdx = i;
                col = c;
                break;
            }
        }
        if (col == null) {
            return null;
        }
        List<Ev> events = new ArrayList<>();
        for (int i = hIdx + 1; i < rows.size(); i++) {
            List<String> cells = rows.get(i).cells();
            LocalDateTime t = parseTime(cell(cells, col.get("time")));
            if (t == null) {
                continue;
            }
            events.add(new Ev(rows.get(i).rowNo(), excel ? i + 1 : rows.get(i).rowNo(), t,
                    cell(cells, col.get("acc")), cell(cells, col.get("name")), cell(cells, col.get("ip")),
                    cell(cells, col.get("type")), unescape(cell(cells, col.get("content"))),
                    unescape(cell(cells, col.get("detail")))));
        }
        events.sort(Comparator.comparing(Ev::t).thenComparingInt(Ev::row));
        return new Parsed(events, excel, col.containsKey("detail"));
    }

    private static List<ExcelTextConverter.SheetRow> textRows(byte[] content, Charset cs) {
        String text = new String(content, cs);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        String[] lines = text.split("\r?\n", -1);
        char sep = 0;
        List<ExcelTextConverter.SheetRow> out = new ArrayList<>();
        for (int i = 0; i < lines.length && out.size() < SecLogConstants.MAX_LINE_COUNT; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            if (sep == 0) {
                sep = CsvSplit.detectSeparator(line);
            }
            out.add(new ExcelTextConverter.SheetRow(i + 1, CsvSplit.split(line, sep)));
        }
        return out;
    }

    private static Map<String, Integer> headerColumns(List<String> cells) {
        Map<String, Integer> c = new LinkedHashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            String h = cells.get(i).replace(" ", "");
            if (h.equals("발생일시") || h.equals("일시")) {
                c.putIfAbsent("time", i);
            } else if (h.contains("아이디")) {
                c.putIfAbsent("acc", i);
            } else if (h.contains("이름")) {
                c.putIfAbsent("name", i);
            } else if (h.equalsIgnoreCase("IP") || h.equalsIgnoreCase("IP주소")) {
                c.putIfAbsent("ip", i);
            } else if (h.equals("유형")) {
                c.putIfAbsent("type", i);
            } else if (h.equals("내용")) {
                c.putIfAbsent("content", i);
            } else if (h.equals("상세정보")) {
                c.putIfAbsent("detail", i);
            }
        }
        boolean ok = c.containsKey("time") && c.containsKey("acc") && c.containsKey("type") && c.containsKey("content");
        return ok ? c : null;
    }

    private static String cell(List<String> cells, Integer idx) {
        if (idx == null || idx >= cells.size()) {
            return "";
        }
        String v = cells.get(idx);
        return v == null ? "" : v.trim();
    }

    private static String unescape(String s) {
        return s.replace("&gt;", ">").replace("&lt;", "<").replace("&quot;", "\"").replace("&amp;", "&");
    }

    private static LocalDateTime parseTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.trim();
        java.util.regex.Matcher lm = LOOSE_TIME.matcher(v);
        if (lm.matches()) {
            // 시·분·초가 한 자리로 적힌 경우도 읽음 (예: 2026-10-12 14:10:9)
            try {
                return LocalDateTime.of(Integer.parseInt(lm.group(1)), Integer.parseInt(lm.group(2)), Integer.parseInt(lm.group(3)),
                        Integer.parseInt(lm.group(4)), Integer.parseInt(lm.group(5)), lm.group(6) == null ? 0 : Integer.parseInt(lm.group(6)));
            } catch (java.time.DateTimeException ignore) {
                return null;
            }
        }
        if (v.length() > 19 && v.charAt(19) == '.') {
            v = v.substring(0, 19);
        }
        for (DateTimeFormatter f : TIME_FORMATS) {
            try {
                return LocalDateTime.parse(v, f);
            } catch (DateTimeParseException ignore) {
                // 다음 형식
            }
        }
        return null;
    }

    private static Charset toCharset(String name) {
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static byte[] gunzip(byte[] compressed) {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // ================================================================== 보고서 만들기

    static String levelOf(Severity s) {
        if (s == Severity.CRITICAL || s == Severity.HIGH) {
            return LV_HIGH;
        }
        return s == Severity.MEDIUM ? LV_MEDIUM : LV_LOW;
    }

    static String icon(String level) {
        return switch (level) {
            case LV_HIGH -> "🔴";
            case LV_MEDIUM -> "🟡";
            default -> "🟢";
        };
    }

    static String lines(String... ls) {
        return java.util.Arrays.stream(ls).filter(Objects::nonNull).collect(Collectors.joining("\n"));
    }

    static String md(LocalDateTime t) {
        return t.getMonthValue() + "/" + t.getDayOfMonth();
    }

    /** 보고서 항목 1개 = 룰 1개 × 계정 1개 */
    private static final class Group {
        final SecLogRule rule;
        final String ruleCode;
        final String ruleName;
        final Severity severity;
        final String acc;
        final List<SecLogDetection> dets = new ArrayList<>();
        final List<Ev> evs = new ArrayList<>();

        Group(SecLogRule rule, SecLogDetection d, String acc) {
            this.rule = rule;
            this.ruleCode = d.getRuleCode();
            this.ruleName = d.getRuleName();
            this.severity = d.getSeverity();
            this.acc = acc;
        }

        String level() {
            return levelOf(severity);
        }

        long pending() {
            return dets.stream().filter(x -> x.getReviewStatus() == ReviewStatus.PENDING).count();
        }
    }

    private final class Report {
        private final Parsed p;
        private final SecLogUpload upload;
        private final List<SecLogDetection> dets;
        private final List<SecLogRule> active;
        private final Map<Long, SecLogRule> ruleById;
        private final Map<String, List<Ev>> byAcc = new LinkedHashMap<>();
        private final Map<Integer, Ev> byLine = new HashMap<>();
        private final List<Group> groups = new ArrayList<>();
        private final List<SecLogInternalIp> internalIps;
        private int unmapped;

        Report(Parsed p, SecLogUpload upload, List<SecLogDetection> dets, List<SecLogRule> active, Map<Long, SecLogRule> ruleById,
               List<SecLogInternalIp> internalIps) {
            this.p = p;
            this.internalIps = internalIps;
            this.upload = upload;
            this.dets = dets;
            this.active = active;
            this.ruleById = ruleById;
            for (Ev e : p.events) {
                byAcc.computeIfAbsent(e.acc.isEmpty() ? NO_ACCOUNT : e.acc, k -> new ArrayList<>()).add(e);
                byLine.put(e.line, e);
            }
        }

        DlpReportResponse build(DlpReportResponse.DlpReportResponseBuilder b) {
            Map<String, Group> m = new LinkedHashMap<>();
            for (SecLogDetection d : dets) {
                Ev e = d.getLineNumber() == null ? null : byLine.get(d.getLineNumber());
                String acc = e == null || e.acc.isEmpty() ? NO_ACCOUNT : e.acc;
                if (e == null) {
                    unmapped++;
                }
                Group g = m.computeIfAbsent(d.getRuleCode() + "|" + acc, k -> new Group(ruleById.get(d.getRuleId()), d, acc));
                g.dets.add(d);
                if (e != null) {
                    g.evs.add(e);
                }
            }
            groups.addAll(m.values());
            Map<String, Integer> order = Map.of(LV_HIGH, 0, LV_MEDIUM, 1, LV_LOW, 2);
            groups.sort(Comparator.<Group>comparingInt(g -> order.get(g.level()))
                    .thenComparingInt(g -> -g.severity.getLevel())
                    .thenComparingInt(g -> -g.dets.size())
                    .thenComparing(g -> g.acc));

            List<DlpReportResponse.Item> items = new ArrayList<>();
            int[] reviewed = new int[3];
            int no = 0;
            for (Group g : groups) {
                no++;
                if (g.pending() == 0) {
                    reviewed[order.get(g.level())]++;
                }
                items.add(item(no, g));
            }
            int high = count(LV_HIGH);
            int medium = count(LV_MEDIUM);
            int low = count(LV_LOW);
            int pendingAll = (int) dets.stream().filter(x -> x.getReviewStatus() == ReviewStatus.PENDING).count();
            List<String> suspicious = suspiciousRows();
            List<Ev> ev = p.events;

            return b.applicable(true)
                    .rowLabel(p.excel ? "원본 엑셀 행 번호" : "원본 파일 줄 번호")
                    .totalRows(ev.size())
                    .accountCount(byAcc.size())
                    .periodFrom(ev.get(0).t.format(OUT))
                    .periodTo(ev.get(ev.size() - 1).t.format(OUT))
                    .highCount(high).mediumCount(medium).lowCount(low)
                    .detectionTotal(dets.size()).detectionPending(pendingAll)
                    .highReviewed(reviewed[0]).mediumReviewed(reviewed[1]).lowReviewed(reviewed[2])
                    .reviewLocked(upload.isReviewed())
                    .summaryLines(summary(high, pendingAll))
                    .items(items)
                    .rules(ruleResults())
                    .accounts(accountSummaries())
                    .limits(limits())
                    .suspiciousNote(suspicious.isEmpty()
                            ? "로그 내 의심 문구(지시문처럼 보이는 문장): 없음"
                            : "로그 내 의심 문구 " + suspicious.size() + "건 (" + String.join(", ", suspicious.stream().limit(10).toList())
                            + (suspicious.size() > 10 ? " 등" : "") + ") — 지시로 따르지 않고 그대로 보고 · 원문 직접 확인 필요")
                    .build();
        }

        DlpReportResponse.Item item(int no, Group g) {
            int n = g.dets.size();
            List<Ev> evs = g.evs;
            Ev first = evs.isEmpty() ? null : evs.stream().min(Comparator.comparing(Ev::t)).orElseThrow();
            Ev last = evs.isEmpty() ? null : evs.stream().max(Comparator.comparing(Ev::t)).orElseThrow();
            Set<LocalDate> days = evs.stream().map(e -> e.t.toLocalDate()).collect(Collectors.toCollection(TreeSet::new));
            List<String> ips = evs.stream().map(Ev::ip).filter(s -> !s.isEmpty()).distinct().toList();
            Map<String, Integer> names = new LinkedHashMap<>();
            evs.stream().map(Ev::name).filter(s -> !s.isEmpty()).forEach(s -> names.merge(s, 1, Integer::sum));
            String name = names.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");

            String period = first == null ? "" : first.t.format(MDHM) + (n > 1 && !first.t.equals(last.t) ? " ~ " + last.t.format(MDHM) : "");
            String content = g.ruleName + " " + n + "회" + (days.isEmpty() ? "" : " · " + days.size() + "일 (" + period + ")");
            String title = g.acc + ", " + g.ruleName + " " + n + "회" + (period.isEmpty() ? "" : " (" + period + ")");
            String what = lines(
                    "룰: " + g.ruleName + " (위험도 " + g.severity.getLabel() + ")",
                    "횟수: " + n + "회" + (days.isEmpty() ? "" : " · " + days.size() + "일"),
                    period.isEmpty() ? null : "기간: " + period,
                    ips.isEmpty() ? null : "IP: " + String.join(", ", ips.stream().limit(3).toList()) + (ips.size() > 3 ? " 외 " + (ips.size() - 3) + "개" : ""),
                    first == null ? null : "첫 로그: " + first.type + " · " + first.content + " (" + first.row + (p.excel ? "행)" : "줄)"));

            List<Integer> rows = evs.stream().sorted(Comparator.comparing(Ev::t).thenComparingInt(Ev::row))
                    .map(Ev::row).distinct().limit(MAX_EVIDENCE).toList();

            // 검토 요약 (탐지 건 검토 결과)
            long pending = g.pending();
            List<SecLogDetection> done = g.dets.stream().filter(x -> x.getReviewStatus() != ReviewStatus.PENDING)
                    .sorted(Comparator.comparing(SecLogDetection::getReviewedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            String st = null;
            String stLabel = null;
            if (!done.isEmpty()) {
                Set<ReviewStatus> kinds = done.stream().map(SecLogDetection::getReviewStatus).collect(Collectors.toSet());
                if (pending > 0) {
                    st = "PARTIAL";
                    stLabel = "일부 검토 (" + (n - pending) + "/" + n + ")";
                } else if (kinds.size() == 1) {
                    ReviewStatus rs = kinds.iterator().next();
                    st = rs.name();
                    stLabel = rs.getLabel();
                } else {
                    st = "MIXED";
                    stLabel = "검토 완료 (결과 혼합)";
                }
            }
            SecLogDetection latest = done.isEmpty() ? null : done.get(0);
            return DlpReportResponse.Item.builder()
                    .no(no).itemKey(g.ruleCode + "|" + g.acc).level(g.level())
                    .severity(g.severity.name()).severityLabel(g.severity.getLabel())
                    .ruleCode(g.ruleCode).ruleName(g.ruleName)
                    .ruleDescription(g.rule == null ? null : g.rule.getDescription())
                    .account(g.acc).name(name)
                    .title(title).content(content).count(n).countText(n + "회").what(what)
                    .evidenceRows(rows).evidenceTotal(evs.size())
                    .detectionIds(g.dets.stream().map(SecLogDetection::getDetectionId).toList())
                    .pendingCount((int) pending)
                    .reviewStatus(st).reviewStatusLabel(stLabel)
                    .reviewComment(latest == null ? null : latest.getActionContent())
                    .reviewedAt(latest == null || latest.getReviewedAt() == null ? null : latest.getReviewedAt().format(OUT))
                    .reviewedBy(latest == null ? null : latest.getReviewedBy())
                    .build();
        }

        int count(String level) {
            return (int) groups.stream().filter(g -> g.level().equals(level)).count();
        }

        List<String> summary(int high, int pendingAll) {
            List<String> out = new ArrayList<>();
            if (high > 0) {
                String hs = groups.stream().filter(g -> g.level().equals(LV_HIGH))
                        .map(g -> g.acc + " " + g.ruleName).limit(3).collect(Collectors.joining(", "));
                out.add("🔴 먼저 확인할 것 " + high + "건: " + hs + (high > 3 ? " 등" : ""));
            } else {
                out.add("🔴 높음 해당 없음");
            }
            Map<String, List<Group>> mk = new LinkedHashMap<>();
            groups.stream().filter(g -> g.level().equals(LV_MEDIUM)).forEach(g -> mk.computeIfAbsent(g.ruleName, k -> new ArrayList<>()).add(g));
            mk.entrySet().stream()
                    .max(Comparator.comparingInt((Map.Entry<String, List<Group>> x) -> x.getValue().stream().mapToInt(g -> g.dets.size()).sum()))
                    .ifPresent(x -> out.add("🟡 최다 항목: " + x.getKey() + " — " + x.getValue().size() + "개 계정, 총 "
                            + x.getValue().stream().mapToInt(g -> g.dets.size()).sum() + "회"));
            out.add(dets.isEmpty() ? "탐지 0건 — 적용 룰에 일치하는 로그 없음"
                    : "탐지 " + dets.size() + "건 · 미검토 " + pendingAll + "건" + (pendingAll == 0 ? " (모두 검토)" : ""));
            return out;
        }

        List<DlpReportResponse.RuleResult> ruleResults() {
            Map<String, List<SecLogDetection>> byRule = new LinkedHashMap<>();
            dets.forEach(d -> byRule.computeIfAbsent(d.getRuleCode(), k -> new ArrayList<>()).add(d));
            List<DlpReportResponse.RuleResult> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            List<SecLogRule> rs = new ArrayList<>(active);
            rs.sort(Comparator.comparingInt((SecLogRule r) -> -r.getSeverity().getLevel())
                    .thenComparingInt(r -> r.getSortOrder() == null ? 0 : r.getSortOrder()));
            for (SecLogRule r : rs) {
                if (seen.add(r.getRuleCode())) {
                    out.add(ruleResult(r.getRuleCode(), r.getRuleName(), r.getSeverity(),
                            r.getSystemId() == null ? "공통" : "전용", r.getThresholdCount(), byRule.get(r.getRuleCode())));
                }
            }
            // 분석 당시에는 적용됐지만 지금은 꺼졌거나 삭제된 룰
            for (Map.Entry<String, List<SecLogDetection>> en : byRule.entrySet()) {
                if (seen.add(en.getKey())) {
                    SecLogDetection d = en.getValue().get(0);
                    out.add(ruleResult(d.getRuleCode(), d.getRuleName(), d.getSeverity(), "분석 당시 룰 (지금 미적용)", 0, en.getValue()));
                }
            }
            return out;
        }

        DlpReportResponse.RuleResult ruleResult(String code, String name, Severity sev, String scope, Integer th,
                                                List<SecLogDetection> ds) {
            List<SecLogDetection> list = ds == null ? List.of() : ds;
            int accounts = (int) groups.stream().filter(g -> g.ruleCode.equals(code)).count();
            return DlpReportResponse.RuleResult.builder()
                    .ruleCode(code).ruleName(name).severity(sev.name()).severityLabel(sev.getLabel()).scope(scope)
                    .thresholdCount(th == null ? 1 : th).hitCount(list.size()).accountCount(accounts)
                    .pendingCount((int) list.stream().filter(x -> x.getReviewStatus() == ReviewStatus.PENDING).count())
                    .build();
        }

        List<DlpReportResponse.Account> accountSummaries() {
            List<DlpReportResponse.Account> out = new ArrayList<>();
            Map<String, Integer> order = Map.of(LV_HIGH, 0, LV_MEDIUM, 1, LV_LOW, 2);
            for (Map.Entry<String, List<Ev>> en : byAcc.entrySet()) {
                String a = en.getKey();
                List<Ev> es = en.getValue();
                Map<String, Integer> nameCnt = new LinkedHashMap<>();
                es.stream().map(Ev::name).filter(s -> !s.isEmpty()).forEach(s -> nameCnt.merge(s, 1, Integer::sum));
                String name = nameCnt.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
                List<Group> mine = groups.stream().filter(g -> g.acc.equals(a))
                        .sorted(Comparator.comparingInt(g -> order.get(g.level()))).toList();
                String summary = mine.isEmpty() ? "탐지 없음"
                        : mine.stream().limit(3).map(g -> icon(g.level()) + " " + g.ruleName + " " + g.dets.size() + "회")
                        .collect(Collectors.joining(", ")) + (mine.size() > 3 ? " 외 " + (mine.size() - 3) + "건" : "");
                out.add(DlpReportResponse.Account.builder()
                        .account(a).name(name).activity(es.size())
                        .high((int) mine.stream().filter(g -> g.level().equals(LV_HIGH)).count())
                        .medium((int) mine.stream().filter(g -> g.level().equals(LV_MEDIUM)).count())
                        .low((int) mine.stream().filter(g -> g.level().equals(LV_LOW)).count())
                        .summary(summary).build());
            }
            out.sort(Comparator.comparingInt((DlpReportResponse.Account x) -> -x.getHigh())
                    .thenComparingInt(x -> -x.getMedium()).thenComparingInt(x -> -x.getActivity()));
            return out;
        }

        List<String> limits() {
            List<String> out = new ArrayList<>();
            long common = active.stream().filter(r -> r.getSystemId() == null).count();
            out.add("판정 기준: [관리 › 탐지 룰셋 편집]의 DLP 적용 룰 " + active.size() + "개 (공통 " + common + " + 전용 "
                    + (active.size() - common) + ") · 로그 한 줄씩 패턴 일치 여부만 판단 (여러 줄의 순서·시간 흐름은 판단하지 않음)");
            LocalDateTime analyzed = upload.getAnalyzedAt();
            if (analyzed != null) {
                long changed = active.stream().filter(r -> {
                    LocalDateTime t = r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt();
                    return t != null && t.isAfter(analyzed);
                }).count();
                Set<String> activeCodes = active.stream().map(SecLogRule::getRuleCode).collect(Collectors.toSet());
                long gone = dets.stream().map(SecLogDetection::getRuleCode).distinct().filter(c -> !activeCodes.contains(c)).count();
                if (changed + gone > 0) {
                    out.add("마지막 분석(" + analyzed.format(OUT_M) + ") 이후 바뀐 룰 " + (changed + gone)
                            + "개 → 상단 [재분석] 시 반영 (재분석하면 이 로그의 검토 기록 초기화)");
                }
            }
            if (active.stream().anyMatch(r -> r.getMatchType() == MatchType.EXTERNAL_IP)) {
                List<String> on = internalIps.stream().filter(x -> "Y".equals(x.getUseYn()))
                        .map(x -> x.getIpName() + " " + x.getIpRange()).toList();
                out.add("내부 IP 기준: [관리 › 내부 IP 설정] 사용 중 대역 " + on.size() + "개"
                        + (on.isEmpty() ? " (없음 → IP 가 있는 모든 줄이 '내부 IP 외')" : " (" + String.join(", ", on) + ")")
                        + " · 줄에서 처음 나오는 IP 기준");
                if (analyzed != null) {
                    boolean ipChanged = internalIps.stream().anyMatch(x -> {
                        LocalDateTime t = x.getDeletedAt() != null ? x.getDeletedAt()
                                : x.getUpdatedAt() != null ? x.getUpdatedAt() : x.getCreatedAt();
                        return t != null && t.isAfter(analyzed);
                    });
                    if (ipChanged) {
                        out.add("마지막 분석 이후 내부 IP 설정 변경 → 상단 [재분석] 시 '내부 IP 외 접속' 결과 반영");
                    }
                }
            }
            List<String> capped = dets.stream().collect(Collectors.groupingBy(SecLogDetection::getRuleName, LinkedHashMap::new, Collectors.counting()))
                    .entrySet().stream().filter(x -> x.getValue() >= SecLogConstants.MAX_DETECTION_PER_RULE).map(Map.Entry::getKey).toList();
            if (!capped.isEmpty()) {
                out.add("룰당 최대 " + SecLogConstants.MAX_DETECTION_PER_RULE + "건까지 저장 → " + String.join(", ", capped) + " 은(는) 앞부분만 표시");
            }
            if (active.stream().anyMatch(r -> r.getThresholdCount() != null && r.getThresholdCount() > 1)) {
                out.add("임계 건수가 있는 룰은 파일 안 일치 건수가 임계 미만이면 탐지 0건 (5. 룰별 결과의 '임계' 참고)");
            }
            if (unmapped > 0) {
                out.add("계정·시각을 읽을 수 없는 줄의 탐지 " + unmapped + "건 → 계정 '-' 로 표시");
            }
            if (!p.hasDetail) {
                out.add("'상세 정보' 열 없음 → 변경 전·후 값·조회 조건 확인 불가");
            }
            out.add("정상·위반 판단은 담당자 몫 · 변경 요청서와의 대조는 로그로 확인 불가");
            LocalDateTime to = p.events.get(p.events.size() - 1).t;
            LocalDate monthEnd = to.toLocalDate().withDayOfMonth(to.toLocalDate().lengthOfMonth());
            if (to.toLocalDate().isBefore(monthEnd)) {
                out.add("로그 범위: " + md(to) + " " + to.format(DateTimeFormatter.ofPattern("HH:mm")) + "까지 → 이후 활동 미포함");
            }
            return out;
        }

        List<String> suspiciousRows() {
            List<String> out = new ArrayList<>();
            for (Ev e : p.events) {
                String s = e.content + " " + e.detail;
                for (Pattern pt : SUSPICIOUS) {
                    if (pt.matcher(s).find()) {
                        out.add(e.row + (p.excel ? "행" : "줄"));
                        break;
                    }
                }
            }
            return out;
        }
    }
}
