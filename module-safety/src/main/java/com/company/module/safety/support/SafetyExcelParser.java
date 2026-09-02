package com.company.module.safety.support;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.safety.entity.SafetyFormType;
import com.company.module.safety.entity.SafetyManualColumn;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 안전 관련 엑셀 파서 — 두 가지 서식을 읽는다.
 *
 * <p><b>1) 안전작업 매뉴얼</b> (기존, .xlsx)
 * <ul>
 *   <li>시트 1개 = 매뉴얼 1개. 헤더(1행)에 "공정 순서" 가 있어야 인식한다.</li>
 *   <li>공정명(B열)은 시트 전체에 병합되어 매뉴얼 제목 역할을 한다.</li>
 *   <li>사진은 C열에 도형으로 삽입되어 있고, 앵커의 행 위치로 어느 단계인지 판별한다.</li>
 * </ul>
 *
 * <p><b>2) 작업 위험성 평가서</b> (신규, .xls)
 * <ul>
 *   <li>워크북에 가이드/첨부/별첨 시트가 섞여 있고, 실제 평가 시트만 골라낸다.</li>
 *   <li>머리말(작업명/부서명/작업인원/목적/개인보호구/중요위험요소 등)을 라벨-값으로 뽑는다.</li>
 *   <li>표 머리글: 작업 순서 | 발생 가능한 위험 | √ | 위험성 평가 대책 | √
 *       — "√" 칸은 체크 열로 만든다.</li>
 *   <li>사진은 없다.</li>
 * </ul>
 *
 * <p>파일 형식(.xls/.xlsx)은 {@link WorkbookFactory} 가 알아서 가른다.
 * 이 클래스는 순수 파싱만 담당하고(디스크/DB 접근 없음), 결과는 {@link ParsedSheet} 로 반환한다.
 */
public class SafetyExcelParser {

    // ── 안전작업 매뉴얼 서식 ──
    /** 매뉴얼 시트로 인식하려면 헤더에 이 문구가 있어야 한다. */
    private static final String HEADER_MARK_STEP = "공정 순서";
    /** 개요/범례 시트(예: "초지" 시트)의 특징적 헤더 문구 — 이게 있으면 매뉴얼이 아니라 제외한다. */
    private static final String HEADER_MARK_OVERVIEW = "공정단계";

    /** 행 번호 칸 (열 정의에는 넣지 않고 stepNo 로 쓴다) */
    private static final String HEADER_NO = "No.";
    /** 매뉴얼 제목이 병합되어 들어 있는 칸 (열 정의에는 넣지 않는다) */
    private static final String HEADER_TITLE = "공정명";
    /** 사진이 들어가는 칸 */
    private static final String HEADER_PHOTO = "사진";

    private static final int COL_NO = 0;
    private static final int COL_TITLE = 1;
    private static final int COL_PHOTO = 2;
    private static final int COL_DESC = 3;

    // ── 작업 위험성 평가서 서식 ──
    /** 표 머리글 첫 칸 문구 */
    private static final String RISK_HEADER_STEP = "작업 순서";
    /** 표 머리글에 함께 있어야 하는 문구 (가이드 시트 오인 방지) */
    private static final String RISK_HEADER_HAZARD = "발생 가능한 위험";
    /** 체크 열 머리글 */
    private static final String RISK_CHECK_MARK = "√";
    /** 머리글을 찾을 최대 행 (이보다 아래에 있으면 평가 시트로 보지 않는다) */
    private static final int RISK_HEADER_SCAN_LIMIT = 20;
    /** 표의 끝을 알리는 문구 */
    private static final String RISK_TABLE_END = "작업자 서명";

    /** 머리말로 뽑을 라벨 (엑셀에 이 문구가 있으면 오른쪽 첫 값 칸을 값으로 본다) */
    private static final List<String> RISK_META_LABELS = List.of(
            "부서명", "작업인원", "작업장소", "작업주기", "작업일",
            "목적", "개인보호구", "중요위험요소");

    public List<ParsedSheet> parse(InputStream excelStream) {
        try (Workbook workbook = WorkbookFactory.create(excelStream)) {
            List<ParsedSheet> result = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                result.add(parseSheet(workbook.getSheetAt(i)));
            }
            return result;
        } catch (IOException e) {
            // 표준상 RuntimeException/IllegalArgumentException 을 직접 던지지 않고 core 예외를 쓴다.
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "엑셀 파일을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    /** 서식을 판별해 해당 파서로 넘긴다. 어느 쪽도 아니면 사유를 담아 제외한다. */
    private ParsedSheet parseSheet(Sheet sheet) {
        String sheetName = sheet.getSheetName();
        if (sheet.getRow(0) == null) {
            return ParsedSheet.rejected(sheetName, "빈 시트입니다.");
        }

        int riskHeaderRow = findRiskHeaderRow(sheet);
        if (riskHeaderRow >= 0) {
            return parseRiskAssessment(sheet, riskHeaderRow);
        }
        return parseWorkMethod(sheet);
    }

    // ================================================================
    // 서식 1 — 안전작업 매뉴얼
    // ================================================================
    private ParsedSheet parseWorkMethod(Sheet sheet) {
        String sheetName = sheet.getSheetName();
        Row headerRow = sheet.getRow(0);

        String headerTitleCol = cellText(headerRow.getCell(COL_TITLE));
        String headerPhotoOrDescCol = cellText(headerRow.getCell(COL_PHOTO)) + " " + cellText(headerRow.getCell(COL_DESC));

        if (headerTitleCol.contains(HEADER_MARK_OVERVIEW)) {
            return ParsedSheet.rejected(sheetName, "개요/범례 시트로 추정되어 매뉴얼 대상에서 제외됩니다.");
        }
        if (!headerPhotoOrDescCol.contains(HEADER_MARK_STEP)) {
            return ParsedSheet.rejected(sheetName, "지원하는 매뉴얼 형식과 헤더가 일치하지 않습니다.");
        }

        // 열은 헤더 행에서 읽는다 — 파일마다 열 구성이 다르다.
        // (예: 어떤 파일은 "안전 보호구" 가 있고 어떤 파일은 없다. 고정 인덱스로 읽으면 값이 밀린다.)
        List<ParsedColumn> columns = new ArrayList<>();
        List<Integer> sourceColumnIndexes = new ArrayList<>();
        int photoColumnIndex = -1;
        for (int colIdx = 0; colIdx <= headerRow.getLastCellNum(); colIdx++) {
            String label = flatten(cellText(headerRow.getCell(colIdx)));
            if (label.isBlank()) continue;
            if (label.startsWith(HEADER_NO) || label.equals(HEADER_TITLE)) continue;   // 번호/제목은 열이 아니다

            if (label.contains(HEADER_PHOTO)) {
                columns.add(new ParsedColumn(label, SafetyManualColumn.TYPE_PHOTO, 150));
                photoColumnIndex = colIdx;
            } else {
                columns.add(new ParsedColumn(label, SafetyManualColumn.TYPE_TEXT, 260));
            }
            sourceColumnIndexes.add(colIdx);
        }
        if (columns.isEmpty()) {
            return ParsedSheet.rejected(sheetName, "표의 열 머리글을 읽을 수 없습니다.");
        }

        Map<Integer, List<ParsedPhoto>> photosByRow = extractPhotosByRow(sheet);
        List<ParsedRow> rows = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        int order = 1;
        for (int rowIdx = 1; rowIdx <= lastRow; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            Integer stepNo = cellInt(row.getCell(COL_NO));
            List<ParsedPhoto> photos = photosByRow.getOrDefault(rowIdx, List.of());

            List<ParsedCell> cells = new ArrayList<>();
            boolean hasContent = !photos.isEmpty();
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).type().equals(SafetyManualColumn.TYPE_PHOTO)) {
                    cells.add(ParsedCell.empty());   // 사진은 값이 아니라 photos 로 들어간다
                    continue;
                }
                String text = cellText(row.getCell(sourceColumnIndexes.get(i))).trim();
                if (!text.isBlank()) hasContent = true;
                cells.add(ParsedCell.text(text));
            }

            // 번호만 찍혀 있고 내용이 하나도 없는 행(빈 양식)은 가져오지 않는다
            if (!hasContent) continue;

            rows.add(new ParsedRow(stepNo != null ? stepNo : order, order, cells, photos));
            order++;
        }

        if (rows.isEmpty()) {
            return ParsedSheet.rejected(sheetName, "내용이 채워진 행이 없습니다. (빈 양식 시트로 보입니다)");
        }
        return ParsedSheet.accepted(sheetName, SafetyFormType.WORK_METHOD,
                extractWorkMethodTitle(sheet), List.of(), columns, rows);
    }

    /** B열(공정명) 병합영역에서 매뉴얼 제목을 추출한다. 병합이 없으면 첫 데이터 행의 값을 쓴다. */
    private String extractWorkMethodTitle(Sheet sheet) {
        String raw = null;
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.getFirstColumn() == COL_TITLE && region.getLastColumn() == COL_TITLE
                    && region.getFirstRow() == 1) {
                Row row = sheet.getRow(region.getFirstRow());
                raw = (row != null) ? cellText(row.getCell(COL_TITLE)) : null;
                break;
            }
        }
        if (raw == null || raw.isBlank()) {
            Row firstDataRow = sheet.getRow(1);
            raw = (firstDataRow != null) ? cellText(firstDataRow.getCell(COL_TITLE)) : "";
        }
        return flatten(raw);
    }

    // ================================================================
    // 서식 2 — 작업 위험성 평가서
    // ================================================================

    /** "작업 순서"와 "발생 가능한 위험"이 같은 행에 있는 표 머리글 행을 찾는다. 없으면 -1. */
    private int findRiskHeaderRow(Sheet sheet) {
        int limit = Math.min(sheet.getLastRowNum(), RISK_HEADER_SCAN_LIMIT);
        for (int rowIdx = 0; rowIdx <= limit; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            String joined = joinRow(row);
            if (joined.contains(RISK_HEADER_STEP) && joined.contains(RISK_HEADER_HAZARD)) {
                return rowIdx;
            }
        }
        return -1;
    }

    private ParsedSheet parseRiskAssessment(Sheet sheet, int headerRowIdx) {
        String sheetName = sheet.getSheetName();
        Row headerRow = sheet.getRow(headerRowIdx);

        // 머리글에서 열 정의와 각 열이 실제로 놓인 엑셀 열 번호를 함께 뽑는다.
        List<ParsedColumn> columns = new ArrayList<>();
        List<Integer> sourceColumnIndexes = new ArrayList<>();
        int checkSeq = 0;
        for (int colIdx = 0; colIdx <= headerRow.getLastCellNum(); colIdx++) {
            String label = flatten(cellText(headerRow.getCell(colIdx)));
            if (label.isBlank()) continue;

            if (RISK_CHECK_MARK.equals(label)) {
                checkSeq++;
                columns.add(new ParsedColumn("확인" + checkSeq, SafetyManualColumn.TYPE_CHECK, 60));
            } else {
                columns.add(new ParsedColumn(label, SafetyManualColumn.TYPE_TEXT, 300));
            }
            sourceColumnIndexes.add(colIdx);
        }
        if (columns.size() < 2) {
            return ParsedSheet.rejected(sheetName, "위험성 평가 표의 머리글을 읽을 수 없습니다.");
        }

        List<ParsedRow> rows = new ArrayList<>();
        int order = 1;
        for (int rowIdx = headerRowIdx + 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            if (joinRow(row).contains(RISK_TABLE_END)) break;

            List<ParsedCell> cells = new ArrayList<>();
            boolean hasText = false;
            for (int i = 0; i < columns.size(); i++) {
                String raw = cellText(row.getCell(sourceColumnIndexes.get(i)));
                if (SafetyManualColumn.TYPE_CHECK.equals(columns.get(i).type())) {
                    cells.add(ParsedCell.check(isChecked(raw)));
                } else {
                    String text = raw.trim();
                    if (!text.isBlank()) hasText = true;
                    cells.add(ParsedCell.text(text));
                }
            }
            if (!hasText) continue;   // 빈 행/병합 잔여 행은 건너뛴다

            rows.add(new ParsedRow(leadingNumber(cells, order), order, cells, List.of()));
            order++;
        }

        if (rows.isEmpty()) {
            return ParsedSheet.rejected(sheetName, "인식 가능한 작업 순서 행이 없습니다.");
        }

        List<ParsedMeta> meta = extractRiskMeta(sheet, headerRowIdx);
        String title = extractRiskTitle(sheet, headerRowIdx, sheetName);
        return ParsedSheet.accepted(sheetName, SafetyFormType.RISK_ASSESSMENT, title, meta, columns, rows);
    }

    /** 머리글 위쪽 행에서 "라벨 → 오른쪽 첫 값" 형태로 머리말 항목을 모은다. */
    private List<ParsedMeta> extractRiskMeta(Sheet sheet, int headerRowIdx) {
        List<ParsedMeta> meta = new ArrayList<>();
        for (int rowIdx = 0; rowIdx < headerRowIdx; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            for (int colIdx = 0; colIdx <= row.getLastCellNum(); colIdx++) {
                String label = flatten(cellText(row.getCell(colIdx)));
                if (label.isBlank()) continue;

                String matched = RISK_META_LABELS.stream()
                        .filter(known -> label.replace(" ", "").startsWith(known.replace(" ", "")))
                        .findFirst().orElse(null);
                if (matched == null) continue;

                String value = firstValueToRight(row, colIdx);
                if (!value.isBlank()) {
                    meta.add(new ParsedMeta(matched, value));
                }
            }
        }
        return meta;
    }

    /** 라벨 칸 오른쪽에서 처음 만나는 값 (병합 때문에 몇 칸 건너뛰어 있을 수 있다) */
    private String firstValueToRight(Row row, int labelColIdx) {
        for (int colIdx = labelColIdx + 1; colIdx <= row.getLastCellNum(); colIdx++) {
            String text = flatten(cellText(row.getCell(colIdx)));
            if (text.isBlank()) continue;
            // 다음 라벨을 값으로 잘못 잡지 않도록 거른다
            boolean isAnotherLabel = RISK_META_LABELS.stream()
                    .anyMatch(known -> text.replace(" ", "").startsWith(known.replace(" ", "")));
            return isAnotherLabel ? "" : text;
        }
        return "";
    }

    /** 제목은 "작업명" 값 우선, 없으면 시트명을 쓴다. */
    private String extractRiskTitle(Sheet sheet, int headerRowIdx, String sheetName) {
        for (int rowIdx = 0; rowIdx < headerRowIdx; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            for (int colIdx = 0; colIdx <= row.getLastCellNum(); colIdx++) {
                if ("작업명".equals(flatten(cellText(row.getCell(colIdx))))) {
                    String value = firstValueToRight(row, colIdx);
                    if (!value.isBlank()) return value;
                }
            }
        }
        return sheetName.trim();
    }

    /** "□" 는 미체크, "■/☑/√/V/O" 계열은 체크로 본다. */
    private boolean isChecked(String raw) {
        if (raw == null) return false;
        String text = raw.trim();
        if (text.isEmpty() || "□".equals(text)) return false;
        return text.contains("■") || text.contains("☑") || text.contains("✔")
                || text.contains("√") || text.equalsIgnoreCase("V") || text.equalsIgnoreCase("O");
    }

    /** 첫 텍스트 칸 앞의 "1." 같은 번호를 단계 번호로 쓴다. 없으면 순번을 쓴다. */
    private int leadingNumber(List<ParsedCell> cells, int fallback) {
        for (ParsedCell cell : cells) {
            String text = (cell.text() != null) ? cell.text().trim() : "";
            if (text.isEmpty()) continue;
            int dot = text.indexOf('.');
            if (dot > 0 && dot <= 3) {
                try {
                    return Integer.parseInt(text.substring(0, dot).trim());
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }
        return fallback;
    }

    // ================================================================
    // 공통 유틸
    // ================================================================

    /**
     * 시트에 삽입된 그림을 앵커의 행 번호(0-based) 기준으로 그룹핑한다.
     * <p>.xls(HSSF)에는 이 서식의 사진이 없으므로 .xlsx(XSSF)일 때만 훑는다.
     */
    private Map<Integer, List<ParsedPhoto>> extractPhotosByRow(Sheet sheet) {
        Map<Integer, List<ParsedPhoto>> result = new LinkedHashMap<>();
        if (!(sheet instanceof XSSFSheet xssfSheet)) {
            return result;
        }
        XSSFDrawing drawing = xssfSheet.getDrawingPatriarch();
        if (drawing == null) return result;

        int seq = 0;
        for (XSSFShape shape : drawing.getShapes()) {
            if (!(shape instanceof XSSFPicture picture)) continue;
            var anchor = picture.getClientAnchor();
            if (anchor == null) continue;
            int rowIdx = anchor.getRow1();

            var pictureData = picture.getPictureData();
            String ext = pictureData.suggestFileExtension();
            // WMF/EMF 등 웹에서 바로 표시 불가능한 포맷은 건너뛴다.
            if ("wmf".equalsIgnoreCase(ext) || "emf".equalsIgnoreCase(ext)) {
                continue;
            }
            String fileName = "sheet_" + sheet.getSheetName().replaceAll("[^a-zA-Z0-9가-힣]", "_")
                    + "_row" + rowIdx + "_" + (seq++) + "." + ext;

            result.computeIfAbsent(rowIdx, k -> new ArrayList<>())
                    .add(new ParsedPhoto(fileName, pictureData.getMimeType(), pictureData.getData()));
        }
        return result;
    }

    private String joinRow(Row row) {
        StringBuilder sb = new StringBuilder();
        for (int colIdx = 0; colIdx <= row.getLastCellNum(); colIdx++) {
            String text = cellText(row.getCell(colIdx));
            if (!text.isBlank()) sb.append(text).append(' ');
        }
        return sb.toString();
    }

    /** 줄바꿈/연속 공백을 단일 공백으로 정리한다. */
    private String flatten(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", " ").trim();
    }

    private String cellText(Cell cell) {
        if (cell == null) return "";
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v)) return String.valueOf((long) v);
                return String.valueOf(v);
            }
            String s = cell.getStringCellValue();
            return (s != null) ? s : "";
        } catch (Exception e) {
            return "";
        }
    }

    private Integer cellInt(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            }
            String s = cell.getStringCellValue();
            if (s == null || s.isBlank()) return null;
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ================================================================
    // 결과 모델 (순수 데이터, DB/디스크 의존 없음)
    // ================================================================

    /** 시트 1개의 파싱 결과 */
    public static final class ParsedSheet {
        private final String sheetName;
        private final boolean recognized;
        private final String reason;
        private final SafetyFormType formType;
        private final String title;
        private final List<ParsedMeta> meta;
        private final List<ParsedColumn> columns;
        private final List<ParsedRow> rows;

        private ParsedSheet(String sheetName, boolean recognized, String reason, SafetyFormType formType,
                            String title, List<ParsedMeta> meta, List<ParsedColumn> columns, List<ParsedRow> rows) {
            this.sheetName = sheetName;
            this.recognized = recognized;
            this.reason = reason;
            this.formType = formType;
            this.title = title;
            this.meta = meta;
            this.columns = columns;
            this.rows = rows;
        }

        static ParsedSheet accepted(String sheetName, SafetyFormType formType, String title,
                                    List<ParsedMeta> meta, List<ParsedColumn> columns, List<ParsedRow> rows) {
            return new ParsedSheet(sheetName, true, null, formType, title, meta, columns, rows);
        }

        static ParsedSheet rejected(String sheetName, String reason) {
            return new ParsedSheet(sheetName, false, reason, null, null, List.of(), List.of(), List.of());
        }

        public String getSheetName() { return sheetName; }
        public boolean isRecognized() { return recognized; }
        public String getReason() { return reason; }
        public SafetyFormType getFormType() { return formType; }
        public String getTitle() { return title; }
        public List<ParsedMeta> getMeta() { return meta; }
        public List<ParsedColumn> getColumns() { return columns; }
        public List<ParsedRow> getRows() { return rows; }

        public int getPhotoCount() {
            return rows.stream().mapToInt(r -> r.photos().size()).sum();
        }

        /** 미리보기에 보여줄 요약 줄 (앞쪽 텍스트 열을 이어 붙인다) */
        public List<String> previewLines(int limit) {
            List<String> lines = new ArrayList<>();
            for (ParsedRow row : rows) {
                if (lines.size() >= limit) break;
                StringBuilder sb = new StringBuilder().append(row.stepNo()).append(". ");
                for (ParsedCell cell : row.cells()) {
                    if (cell.text() != null && !cell.text().isBlank()) {
                        sb.append(cell.text().replaceAll("\\s+", " ").trim());
                        break;
                    }
                }
                String line = sb.toString();
                lines.add(line.length() > 40 ? line.substring(0, 40) + "..." : line);
            }
            return lines;
        }
    }

    /** 표의 열 1개 */
    public record ParsedColumn(String label, String type, int widthWeight) {
    }

    /** 머리말 항목 1개 */
    public record ParsedMeta(String label, String value) {
    }

    /** 행(단계) 1개 — cells 는 columns 와 같은 순서로 1:1 대응한다 */
    public record ParsedRow(int stepNo, int sortOrder, List<ParsedCell> cells, List<ParsedPhoto> photos) {
    }

    /** 셀 1개 — 텍스트 열이면 text, 체크 열이면 checked 를 쓴다 */
    public record ParsedCell(String text, boolean checked) {
        static ParsedCell text(String value) { return new ParsedCell(value, false); }
        static ParsedCell check(boolean value) { return new ParsedCell(null, value); }
        static ParsedCell empty() { return new ParsedCell(null, false); }
    }

    /** 사진 1장의 원본 바이트 (아직 디스크에 저장되지 않은 상태) */
    public record ParsedPhoto(String fileName, String contentType, byte[] data) {
    }
}
