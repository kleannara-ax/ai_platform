package com.company.module.safety.support;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 안전작업 매뉴얼 엑셀(.xlsx) 파서.
 *
 * <p>실제 현장 파일(안전_작업방식 메뉴얼 양식) 구조 분석 결과를 반영한다:
 * <ul>
 *   <li>워크북의 시트 1개 = 매뉴얼 1개 (단, "공정단계" 헤더를 쓰는 개요/범례 시트는 제외)</li>
 *   <li>헤더(1행): No. / 공정명 / 공정 순서(사진) / 공정 순서(설명) / 위험요인 / 안전 보호구 / 비고(개선사항)</li>
 *   <li>공정명(B열)은 시트 전체(2행~마지막행)에 걸쳐 병합되어 매뉴얼 제목 역할을 한다</li>
 *   <li>사진은 C열(공정 순서 사진)에 도형(드로잉)으로 삽입되어 있으며, 앵커의 행 위치로
 *       어느 단계(행)에 속하는지 판별한다. 한 행에 여러 장이 있을 수 있다.</li>
 * </ul>
 *
 * <p>이 클래스는 순수 파싱만 담당하고(디스크/DB 접근 없음), 결과는 {@link ParsedSheet} 로 반환한다.
 */
public class SafetyExcelParser {

    /** 매뉴얼 시트로 인식하려면 헤더에 이 문구가 있어야 한다. */
    private static final String HEADER_MARK_STEP = "공정 순서";
    /** 개요/범례 시트(예: "초지" 시트)의 특징적 헤더 문구 — 이게 있으면 매뉴얼이 아니라 제외한다. */
    private static final String HEADER_MARK_OVERVIEW = "공정단계";

    private static final int COL_NO = 0;
    private static final int COL_TITLE = 1;
    private static final int COL_PHOTO = 2;
    private static final int COL_DESC = 3;
    private static final int COL_HAZARD = 4;
    private static final int COL_EQUIPMENT = 5;
    private static final int COL_REMARK = 6;

    public List<ParsedSheet> parse(InputStream excelStream) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(excelStream)) {
            List<ParsedSheet> result = new ArrayList<>();
            int sheetCount = workbook.getNumberOfSheets();
            for (int i = 0; i < sheetCount; i++) {
                XSSFSheet sheet = workbook.getSheetAt(i);
                result.add(parseSheet(sheet));
            }
            return result;
        } catch (IOException e) {
            // 표준상 RuntimeException/IllegalArgumentException 을 직접 던지지 않고 core 예외를 쓴다.
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "엑셀 파일을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    private ParsedSheet parseSheet(XSSFSheet sheet) {
        String sheetName = sheet.getSheetName();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return ParsedSheet.rejected(sheetName, "빈 시트입니다.");
        }

        String headerTitleCol = cellText(headerRow.getCell(COL_TITLE));
        String headerPhotoOrDescCol = cellText(headerRow.getCell(COL_PHOTO)) + " " + cellText(headerRow.getCell(COL_DESC));

        if (headerTitleCol.contains(HEADER_MARK_OVERVIEW)) {
            return ParsedSheet.rejected(sheetName, "개요/범례 시트로 추정되어 매뉴얼 대상에서 제외됩니다.");
        }
        if (!headerPhotoOrDescCol.contains(HEADER_MARK_STEP)) {
            return ParsedSheet.rejected(sheetName, "지원하는 매뉴얼 형식과 헤더가 일치하지 않습니다.");
        }

        String title = extractTitle(sheet);
        List<ParsedStep> steps = extractSteps(sheet);

        if (steps.isEmpty()) {
            return ParsedSheet.rejected(sheetName, "인식 가능한 단계(행)가 없습니다.");
        }

        return ParsedSheet.accepted(sheetName, title, steps);
    }

    /** B열(공정명) 병합영역에서 매뉴얼 제목을 추출한다. 병합이 없으면 첫 데이터 행의 값을 쓴다. */
    private String extractTitle(XSSFSheet sheet) {
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
        return cleanTitle(raw);
    }

    /** 줄바꿈/연속 공백을 단일 공백으로 정리한다 (원본 엑셀의 세로쓰기/여백용 공백 보정). */
    private String cleanTitle(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", " ").trim();
    }

    private List<ParsedStep> extractSteps(XSSFSheet sheet) {
        Map<Integer, List<ParsedPhoto>> photosByRow = extractPhotosByRow(sheet);

        List<ParsedStep> steps = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        int order = 1;
        for (int rowIdx = 1; rowIdx <= lastRow; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;

            Integer stepNo = cellInt(row.getCell(COL_NO));
            String description = cellText(row.getCell(COL_DESC));
            String hazard = cellText(row.getCell(COL_HAZARD));
            String equipment = cellText(row.getCell(COL_EQUIPMENT));
            String remark = cellText(row.getCell(COL_REMARK));

            boolean hasAnyContent = stepNo != null
                    || !description.isBlank() || !hazard.isBlank()
                    || !equipment.isBlank() || !remark.isBlank()
                    || photosByRow.containsKey(rowIdx);
            if (!hasAnyContent) continue;

            steps.add(new ParsedStep(
                    stepNo != null ? stepNo : order,
                    description, hazard, equipment, remark, order,
                    photosByRow.getOrDefault(rowIdx, List.of())));
            order++;
        }
        return steps;
    }

    /** 시트에 삽입된 그림을 앵커의 행 번호(0-based) 기준으로 그룹핑한다. */
    private Map<Integer, List<ParsedPhoto>> extractPhotosByRow(XSSFSheet sheet) {
        Map<Integer, List<ParsedPhoto>> result = new LinkedHashMap<>();
        XSSFDrawing drawing = sheet.getDrawingPatriarch();
        if (drawing == null) return result;

        int seq = 0;
        for (XSSFShape shape : drawing.getShapes()) {
            if (!(shape instanceof XSSFPicture picture)) continue;
            var anchor = picture.getClientAnchor();
            if (anchor == null) continue;
            int rowIdx = anchor.getRow1();

            var pictureData = picture.getPictureData();
            String ext = pictureData.suggestFileExtension();
            // WMF/EMF 등 웹에서 바로 표시 불가능한 포맷은 건너뛴다 (openpyxl 분석에서도 경고 확인됨).
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
        private final String title;
        private final List<ParsedStep> steps;

        private ParsedSheet(String sheetName, boolean recognized, String reason, String title, List<ParsedStep> steps) {
            this.sheetName = sheetName;
            this.recognized = recognized;
            this.reason = reason;
            this.title = title;
            this.steps = steps;
        }

        static ParsedSheet accepted(String sheetName, String title, List<ParsedStep> steps) {
            return new ParsedSheet(sheetName, true, null, title, steps);
        }

        static ParsedSheet rejected(String sheetName, String reason) {
            return new ParsedSheet(sheetName, false, reason, null, List.of());
        }

        public String getSheetName() { return sheetName; }
        public boolean isRecognized() { return recognized; }
        public String getReason() { return reason; }
        public String getTitle() { return title; }
        public List<ParsedStep> getSteps() { return steps; }

        public int getPhotoCount() {
            return steps.stream().mapToInt(s -> s.photos().size()).sum();
        }
    }

    /** 단계(행) 1개의 파싱 결과 */
    public record ParsedStep(
            int stepNo,
            String description,
            String hazard,
            String safetyEquipment,
            String remark,
            int sortOrder,
            List<ParsedPhoto> photos
    ) {
    }

    /** 사진 1장의 원본 바이트 (아직 디스크에 저장되지 않은 상태) */
    public record ParsedPhoto(String fileName, String contentType, byte[] data) {
    }
}
