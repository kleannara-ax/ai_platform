package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.dto.ContractDocAnalyzeResponse;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 개인정보처리 위탁계약서 파일에서 계약일자(체결일)를 찾는다.
 * <ul>
 *   <li>파일은 저장하지 않는다. 글자만 읽어 날짜 후보를 돌려준다(저장은 계약 저장 시 첨부 업로드로 따로 처리).</li>
 *   <li>읽는 형식: pdf, docx, doc, hwp, hwpx, xlsx, xls. 이미지(jpg·png)·zip·스캔 PDF·암호 파일은 글자를 읽을 수 없어 직접 입력 안내.</li>
 *   <li>날짜 판단: '체결일·계약일·작성일' 등 표기 옆 날짜 &gt; 문서 안 날짜(뒤쪽 서명란일수록 우선) &gt; 계약기간(부터·까지·~) 날짜.</li>
 *   <li>개인정보처리 위탁계약은 원계약과 따로 체결될 수 있으므로 원계약·오늘 날짜와 비교하지 않고, 계약서에 적힌 계약일자를 그대로 돌려준다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PiContractDocAnalyzer {

    static final Set<String> TEXT_EXTENSIONS = Set.of("pdf", "docx", "doc", "hwp", "hwpx", "xlsx", "xls");

    private static final int MAX_TEXT_CHARS = 300_000;
    private static final int MAX_PDF_PAGES = 60;
    private static final int MAX_SHEET_ROWS = 2_000;
    private static final long MAX_ZIP_ENTRY_BYTES = 20L * 1024 * 1024;
    private static final int MAX_CANDIDATES = 5;
    private static final DateTimeFormatter DOT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /** 2026년 3월 1일 / 2026 년 03 월 01 일 */
    private static final Pattern KOREAN_DATE =
            Pattern.compile("(?<!\\d)((?:19|20)\\d{2})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    /** 2026.03.01 / 2026. 3. 1. / 2026-03-01 / 2026/3/1 */
    private static final Pattern NUMERIC_DATE =
            Pattern.compile("(?<!\\d)((?:19|20)\\d{2})\\s*([./\\-])\\s*(\\d{1,2})\\s*\\2\\s*(\\d{1,2})(?!\\d)");

    private static final List<String> SIGNED_KEYWORDS =
            List.of("체결일", "계약일", "계약 일자", "계약체결", "계약 체결", "작성일", "서명일", "날인일", "협약일");
    private static final List<String> PERIOD_KEYWORDS =
            List.of("기간", "시작", "종료", "만료", "개시", "유효", "부터", "까지");

    private final PiFileStorage storage;

    public ContractDocAnalyzeResponse analyze(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "분석할 계약서 파일이 없습니다.");
        }
        if (file.getSize() > storage.getMaxContractFileBytes()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "파일 크기는 " + (storage.getMaxContractFileBytes() / 1024 / 1024) + "MB 이하만 분석할 수 있습니다.");
        }
        String name = PiFileStorage.sanitizeOriginalName(file.getOriginalFilename());
        String ext = PiFileStorage.extensionOf(name);
        if (!PiFileStorage.CONTRACT_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "허용되지 않는 파일 형식입니다. (허용: " + String.join(", ", PiFileStorage.CONTRACT_EXTENSIONS) + ")");
        }
        int maxYear = LocalDate.now().getYear() + 5; // 오타·문서번호 등 비정상 연도 제외용
        ContractDocAnalyzeResponse.ContractDocAnalyzeResponseBuilder res = ContractDocAnalyzeResponse.builder()
                .fileName(name).candidates(List.of());

        if (!TEXT_EXTENSIONS.contains(ext)) {
            return res.textReadable(false)
                    .message("이 파일 형식(" + ext + ")은 글자를 읽을 수 없습니다. 체결일과 작성 여부를 직접 입력하세요.")
                    .build();
        }
        String text;
        try {
            text = extractText(file.getBytes(), ext);
        } catch (Exception e) {
            log.info("[personalinfo] 계약서 글자 읽기 실패 ({}): {}", ext, e.toString());
            text = null;
        }
        if (text == null || text.isBlank()) {
            return res.textReadable(false)
                    .message("파일에서 글자를 읽지 못했습니다(스캔본·암호 파일·손상 파일일 수 있음). 체결일과 작성 여부를 직접 입력하세요.")
                    .build();
        }

        List<Scored> found = findDates(normalize(text), maxYear);
        List<ContractDocAnalyzeResponse.Candidate> candidates = found.stream().limit(MAX_CANDIDATES)
                .map(Scored::toCandidate).toList();
        Scored best = found.stream().filter(s -> !"PERIOD".equals(s.kind)).findFirst().orElse(null);
        res.textReadable(true).candidates(candidates);

        if (best == null) {
            return res.message(found.isEmpty()
                            ? "계약서에서 계약일자를 찾지 못했습니다. 체결일을 직접 입력하세요."
                            : "계약기간 날짜만 있고 체결일은 찾지 못했습니다. 아래 후보에서 고르거나 직접 입력하세요.")
                    .build();
        }
        return res.detectedDate(best.date)
                .detectedReason(best.reason())
                .message("계약서에서 계약일자 " + best.date.format(DOT) + " 을(를) 찾았습니다.")
                .build();
    }

    // ───────────── 날짜 찾기 ─────────────

    static List<Scored> findDates(String text, int maxYear) {
        Map<LocalDate, Scored> best = new LinkedHashMap<>();
        collect(text, KOREAN_DATE.matcher(text), 1, 2, 3, best, maxYear);
        collect(text, NUMERIC_DATE.matcher(text), 1, 3, 4, best, maxYear);
        List<Scored> list = new ArrayList<>(best.values());
        list.sort(Comparator.comparingInt((Scored s) -> s.score).reversed());
        return list;
    }

    private static void collect(String text, Matcher m, int gy, int gm, int gd, Map<LocalDate, Scored> best, int maxYear) {
        while (m.find()) {
            LocalDate date;
            try {
                date = LocalDate.of(Integer.parseInt(m.group(gy)), Integer.parseInt(m.group(gm)), Integer.parseInt(m.group(gd)));
            } catch (DateTimeException | NumberFormatException e) {
                continue;
            }
            if (date.getYear() < 1990 || date.getYear() > maxYear) continue;
            String before = text.substring(Math.max(0, m.start() - 40), m.start());
            String after = text.substring(m.end(), Math.min(text.length(), m.end() + 15));
            String kind = classify(before, after);
            int position = text.isEmpty() ? 0 : (int) (100L * m.start() / text.length());
            int score = switch (kind) {
                case "SIGNED" -> 1000 + position;
                case "DOC" -> 500 + position;     // 서명란 날짜는 보통 문서 끝에 있으므로 뒤쪽일수록 우선
                default -> 100 + position / 10;
            };
            String context = oneLine(before.substring(Math.max(0, before.length() - 25)) + m.group() + after);
            Scored prev = best.get(date);
            if (prev == null || prev.score < score) best.put(date, new Scored(date, kind, score, context));
        }
    }

    /** SIGNED: 체결일·계약일 표기 옆 / PERIOD: 계약기간(부터·까지·~) / DOC: 그 밖의 문서 안 날짜 */
    static String classify(String before, String after) {
        String a = after.stripLeading();
        String b = before.stripTrailing();
        if (a.startsWith("부터") || a.startsWith("까지") || a.startsWith("~") || a.startsWith("∼")
                || a.startsWith("～") || b.endsWith("~") || b.endsWith("∼") || b.endsWith("～")) {
            return "PERIOD";
        }
        // 표기는 같은 줄에서만 본다(윗줄 '계약기간' 조항 때문에 아랫줄 서명 날짜가 기간으로 잘못 분류되지 않도록)
        int nl = before.lastIndexOf('\n');
        String line = nl >= 0 ? before.substring(nl + 1) : before;
        String near = line.length() > 30 ? line.substring(line.length() - 30) : line;
        int signedAt = lastIndexOfAny(near, SIGNED_KEYWORDS);
        int periodAt = lastIndexOfAny(near, PERIOD_KEYWORDS);
        if (signedAt >= 0 && signedAt >= periodAt) return "SIGNED";
        if (periodAt >= 0) return "PERIOD";
        // 표 형태: 날짜가 줄 맨 앞이고 바로 윗줄이 '체결일' 같은 제목으로 끝나면 체결일로 본다
        if (line.isBlank() && nl >= 0) {
            String prev = before.substring(0, nl).stripTrailing();
            int pnl = prev.lastIndexOf('\n');
            String prevLine = (pnl >= 0 ? prev.substring(pnl + 1) : prev).replaceAll("[\\s:：]+$", "");
            for (String k : SIGNED_KEYWORDS) {
                if (prevLine.endsWith(k)) return "SIGNED";
            }
        }
        return "DOC";
    }

    private static int lastIndexOfAny(String s, List<String> keywords) {
        int at = -1;
        for (String k : keywords) at = Math.max(at, s.lastIndexOf(k));
        return at;
    }

    private static String oneLine(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    static String normalize(String text) {
        // PDF 글꼴에 따라 공백이 NUL(0x00) 등 제어문자로 나오는 경우가 있어 공백으로 바꾼 뒤 날짜를 찾는다
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F\\u00A0\\u200B-\\u200D\\u2060\\uFEFF]", " ");
        return t.length() > MAX_TEXT_CHARS ? t.substring(0, MAX_TEXT_CHARS) : t;
    }

    // ───────────── 글자 추출 ─────────────

    static String extractText(byte[] data, String ext) throws Exception {
        return switch (ext) {
            case "pdf" -> pdf(data);
            case "docx" -> {
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data));
                     XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                    yield ex.getText();
                }
            }
            case "doc" -> {
                try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(data));
                     WordExtractor ex = new WordExtractor(doc)) {
                    yield ex.getText();
                }
            }
            case "hwp" -> {
                HWPFile hwp = HWPReader.fromInputStream(new ByteArrayInputStream(data));
                yield TextExtractor.extract(hwp, TextExtractMethod.InsertControlTextBetweenParagraphText);
            }
            case "hwpx" -> hwpx(data);
            case "xlsx", "xls" -> sheet(data);
            default -> null;
        };
    }

    private static String pdf(byte[] data) throws IOException {
        try (PDDocument doc = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(Math.min(doc.getNumberOfPages(), MAX_PDF_PAGES));
            return stripper.getText(doc);
        }
    }

    /** hwpx: zip 안 Contents/section*.xml 의 글자만 꺼낸다 (XML 파서를 쓰지 않아 외부 엔티티 처리 위험 없음) */
    private static String hwpx(byte[] data) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null && sb.length() < MAX_TEXT_CHARS) {
                String n = e.getName();
                if (!n.startsWith("Contents/section") || !n.endsWith(".xml")) continue;
                String xml = new String(readLimited(zin), StandardCharsets.UTF_8);
                sb.append(xml.replaceAll("</hp:p>", "\n")
                                .replaceAll("<hp:lineBreak\\s*/>", "\n")
                                .replaceAll("<[^>]+>", "")
                                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                                .replace("&apos;", "'").replace("&amp;", "&"))
                        .append('\n');
            }
        }
        return sb.toString();
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            total += r;
            if (total > MAX_ZIP_ENTRY_BYTES) throw new IOException("hwpx 본문이 너무 큽니다.");
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    /** 엑셀: 날짜 서식 셀은 yyyy-MM-dd 로 바꿔서 읽는다 */
    private static String sheet(byte[] data) throws IOException {
        StringBuilder sb = new StringBuilder();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            for (Sheet s : wb) {
                int rows = 0;
                for (Row row : s) {
                    if (++rows > MAX_SHEET_ROWS || sb.length() > MAX_TEXT_CHARS) break;
                    for (Cell c : row) {
                        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                            sb.append(c.getLocalDateTimeCellValue().toLocalDate()).append(' ');
                        } else {
                            sb.append(fmt.formatCellValue(c)).append(' ');
                        }
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    record Scored(LocalDate date, String kind, int score, String context) {
        String reason() {
            return switch (kind) {
                case "SIGNED" -> "체결일·계약일 표기 옆 날짜";
                case "DOC" -> "문서 안 날짜(서명란 추정)";
                default -> "계약기간 날짜";
            };
        }

        ContractDocAnalyzeResponse.Candidate toCandidate() {
            return ContractDocAnalyzeResponse.Candidate.builder()
                    .date(date).kind(kind).kindLabel(reason()).context(context).build();
        }
    }
}
