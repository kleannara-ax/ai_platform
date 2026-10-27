package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.personalinfo.entity.ChecklistSheetType;
import com.company.module.personalinfo.entity.InspectionResult;
import com.company.module.personalinfo.entity.InspectionType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "개인정보 처리 협력사 점검 체크 리스트" 엑셀 파서.
 *
 * <p>보안팀 양식(개인정보 위수탁 협력사 점검 리스트_v1.xlsx)의 시트를 읽는다.
 * <ul>
 *   <li>1. 관리체계 (정기 점검)</li>
 *   <li>2. 개인정보처리시스템 (정기 점검, 보유여부 "미 보유" 이면 제외)</li>
 *   <li>3. 계약변경 만료시 체크 (계약변경·만료 점검)</li>
 * </ul>
 *
 * <p>행/열 번호를 고정하지 않고 "평가 항목" 머리글을 찾아 열 위치를 정하므로,
 * 협력사가 행을 추가/삭제해도 읽을 수 있다. 병합 셀(구분 등)은 좌상단 값을 이어 쓴다.
 */
@Component
public class ChecklistExcelParser {

    private static final int HEADER_SEARCH_ROWS = 30;
    private static final int MAX_ITEMS_PER_SHEET = 200;

    public ParsedChecklist parse(InputStream in, InspectionType type) {
        try (Workbook wb = WorkbookFactory.create(in)) {
            return parse(wb, type);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "엑셀 파일을 읽을 수 없습니다. 보안팀 점검 체크리스트(.xlsx) 양식인지 확인하세요.");
        }
    }

    ParsedChecklist parse(Workbook wb, InspectionType type) {
        Map<ChecklistSheetType, Sheet> sheets = findSheets(wb);
        List<String> warnings = new ArrayList<>();
        List<ParsedItem> items = new ArrayList<>();
        ParsedChecklist.ParsedChecklistBuilder meta = new ParsedChecklist.ParsedChecklistBuilder();

        if (type == InspectionType.TERMINATION) {
            Sheet s3 = sheets.get(ChecklistSheetType.TERMINATION);
            if (s3 == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "'3.계약변경 만료시 체크' 시트를 찾을 수 없습니다. 계약변경·만료 점검은 해당 시트를 작성해 업로드하세요.");
            }
            items.addAll(parseItems(s3, ChecklistSheetType.TERMINATION, warnings));
        } else {
            Sheet s1 = sheets.get(ChecklistSheetType.MANAGEMENT);
            Sheet s2 = sheets.get(ChecklistSheetType.SYSTEM);
            if (s1 == null && s2 == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "'1. 관리체계' / '2. 개인정보처리시스템' 시트를 찾을 수 없습니다. 보안팀 점검 체크리스트 양식인지 확인하세요.");
            }
            if (s1 != null) {
                meta.companyName(labelValue(s1, "회사명"));
                meta.taskName(labelValue(s1, "수행(수탁)업무"));
                meta.evaluator(labelValue(s1, "평가자"));
                meta.evaluationDate(labelDate(s1, "평가일"));
                items.addAll(parseItems(s1, ChecklistSheetType.MANAGEMENT, warnings));
            } else {
                warnings.add("'1. 관리체계' 시트가 없어 관리체계 항목은 집계하지 않았습니다.");
            }
            if (s2 != null) {
                String owned = labelValue(s2, "보유여부");
                String ownedYn = normalizeOwned(owned);
                meta.systemOwnedYn(ownedYn);
                meta.systemName(labelValue(s2, "시스템명"));
                if ("N".equals(ownedYn)) {
                    warnings.add("개인정보처리시스템 '미 보유'로 작성되어 '2. 개인정보처리시스템' 항목은 집계에서 제외했습니다.");
                } else {
                    if (ownedYn == null) {
                        warnings.add("'2. 개인정보처리시스템' 시트의 보유여부가 비어 있습니다. (보유/미 보유 중 선택 필요)");
                    }
                    items.addAll(parseItems(s2, ChecklistSheetType.SYSTEM, warnings));
                }
                if (meta.evaluationDate == null) {
                    meta.evaluationDate(labelDate(s2, "평가일"));
                }
                if (isBlankOrPlaceholder(meta.evaluator)) {
                    meta.evaluator(labelValue(s2, "평가자"));
                }
            }
        }

        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "체크리스트에서 평가 항목을 찾지 못했습니다. 양식의 '평가 항목' 머리글을 지우지 않았는지 확인하세요.");
        }

        int y = 0, n = 0, na = 0, blank = 0;
        for (ParsedItem it : items) {
            if (it.evaluation() == null) {
                blank++;
            } else {
                switch (it.evaluation()) {
                    case "Y" -> y++;
                    case "N" -> n++;
                    default -> na++;
                }
            }
        }
        InspectionResult result = n > 0 ? InspectionResult.NEED_ACTION
                : (blank > 0 ? InspectionResult.INCOMPLETE : InspectionResult.PASS);
        if (blank > 0) {
            warnings.add("평가(Y/N/NA)가 비어 있는 항목이 " + blank + "건 있습니다.");
        }
        long unknown = items.stream().filter(i -> i.evaluation() == null && !i.evaluationRaw().isEmpty()).count();
        if (unknown > 0) {
            warnings.add("평가값을 해석할 수 없는 항목이 " + unknown + "건 있습니다. (Y/N/NA 로 작성 필요)");
        }

        return meta.items(items).warnings(warnings)
                .counts(items.size(), y, n, na, blank).result(result).build();
    }

    // ─────────────────────────────────────────────────────────────
    //  시트 탐색
    // ─────────────────────────────────────────────────────────────

    private Map<ChecklistSheetType, Sheet> findSheets(Workbook wb) {
        Map<ChecklistSheetType, Sheet> map = new EnumMap<>(ChecklistSheetType.class);
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet sheet = wb.getSheetAt(i);
            String name = PiExcelSupport.compact(sheet.getSheetName());
            if (name.startsWith("1.") || name.contains("관리체계")) {
                map.putIfAbsent(ChecklistSheetType.MANAGEMENT, sheet);
            } else if (name.startsWith("2.") || name.contains("개인정보처리시스템")) {
                map.putIfAbsent(ChecklistSheetType.SYSTEM, sheet);
            } else if (name.startsWith("3.") || name.contains("만료")) {
                map.putIfAbsent(ChecklistSheetType.TERMINATION, sheet);
            }
        }
        return map;
    }

    // ─────────────────────────────────────────────────────────────
    //  항목 파싱
    // ─────────────────────────────────────────────────────────────

    private List<ParsedItem> parseItems(Sheet sheet, ChecklistSheetType type, List<String> warnings) {
        Columns cols = findColumns(sheet);
        if (cols == null) {
            warnings.add("'" + sheet.getSheetName() + "' 시트에서 '평가 항목' 머리글을 찾지 못했습니다.");
            return List.of();
        }
        List<ParsedItem> list = new ArrayList<>();
        String lastCategory = "";
        int order = 0;
        for (int r = cols.dataStartRow; r <= sheet.getLastRowNum() && list.size() < MAX_ITEMS_PER_SHEET; r++) {
            String category = cols.category >= 0 ? PiExcelSupport.text(sheet, r, cols.category) : "";
            String question = PiExcelSupport.text(sheet, r, cols.question);
            if (question.isEmpty() && cols.question + 1 != cols.method) {
                question = PiExcelSupport.text(sheet, r, cols.question + 1);
            }
            String method = cols.method >= 0 ? PiExcelSupport.rawText(sheet, r, cols.method) : "";
            if (question.isEmpty() && method.isEmpty()) {
                continue;
            }
            if (!category.isEmpty()) {
                lastCategory = category;
            }
            String rawEval = cols.evaluation >= 0 ? PiExcelSupport.rawText(sheet, r, cols.evaluation) : "";
            list.add(new ParsedItem(
                    type, r + 1, ++order,
                    PiExcelSupport.truncate(lastCategory.replaceAll("\\s+", " "), 100),
                    cols.important >= 0 && PiExcelSupport.rawText(sheet, r, cols.important).contains("●"),
                    PiExcelSupport.truncate(question.isEmpty() ? method : question, 1000),
                    PiExcelSupport.truncate(method, 2000),
                    PiExcelSupport.truncate(cols.status >= 0 ? PiExcelSupport.rawText(sheet, r, cols.status) : "", 4000),
                    normalizeEvaluation(rawEval),
                    rawEval,
                    PiExcelSupport.truncate(cols.actionPlan >= 0 ? PiExcelSupport.rawText(sheet, r, cols.actionPlan) : "", 2000),
                    PiExcelSupport.truncate(cols.schedule >= 0 ? PiExcelSupport.rawText(sheet, r, cols.schedule) : "", 200),
                    cols.confirmDate >= 0 ? PiExcelSupport.date(sheet, r, cols.confirmDate) : null,
                    PiExcelSupport.truncate(cols.confirmer >= 0 ? PiExcelSupport.rawText(sheet, r, cols.confirmer) : "", 100),
                    PiExcelSupport.truncate(cols.remark >= 0 ? PiExcelSupport.rawText(sheet, r, cols.remark) : "", 1000)
            ));
        }
        return list;
    }

    /** "평가 항목" 머리글 행과 그 아래 보조 머리글 행에서 열 위치를 찾는다 */
    private Columns findColumns(Sheet sheet) {
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int questionCol = -1;
            for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                if (PiExcelSupport.compact(PiExcelSupport.rawText(sheet, r, c)).equals("평가항목")) {
                    questionCol = c;
                    break;
                }
            }
            if (questionCol < 0) {
                continue;
            }
            Columns cols = new Columns();
            cols.question = questionCol;
            int lastCol = Math.max(row.getLastCellNum(), questionCol + 1) + 4;
            for (int hr = r; hr <= r + 1; hr++) {
                for (int c = 0; c < lastCol; c++) {
                    String h = PiExcelSupport.compact(PiExcelSupport.rawText(sheet, hr, c));
                    if (h.isEmpty()) {
                        continue;
                    }
                    if (h.equals("구분")) cols.category = c;
                    else if (h.equals("중요")) cols.important = c;
                    else if (h.startsWith("점검방법")) cols.method = c;
                    else if (h.startsWith("현황")) cols.status = c;
                    else if (h.startsWith("평가") && !h.equals("평가항목")) cols.evaluation = c;
                    else if (h.equals("확인여부")) cols.evaluation = c;
                    else if (h.equals("조치계획")) cols.actionPlan = c;
                    else if (h.startsWith("조치계획및일정") && cols.actionPlan < 0) cols.actionPlan = c;
                    else if (h.equals("일정")) cols.schedule = c;
                    else if (h.equals("확인일자")) cols.confirmDate = c;
                    else if (h.equals("담당자")) cols.confirmer = c;
                    else if (h.equals("비고")) cols.remark = c;
                }
            }
            cols.dataStartRow = r + 2;
            return cols;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────
    //  머리 영역(회사명/평가일 등)
    // ─────────────────────────────────────────────────────────────

    /** B열 등에서 라벨(공백 무시)을 찾아 오른쪽 첫 값 셀을 돌려준다 */
    private String labelValue(Sheet sheet, String label) {
        int[] pos = findLabel(sheet, label);
        if (pos == null) {
            return null;
        }
        for (int c = pos[1] + 1; c <= pos[1] + 4; c++) {
            String v = PiExcelSupport.rawText(sheet, pos[0], c);
            if (!v.isEmpty()) {
                return PiExcelSupport.truncate(v, 500);
            }
        }
        return null;
    }

    private LocalDate labelDate(Sheet sheet, String label) {
        int[] pos = findLabel(sheet, label);
        if (pos == null) {
            return null;
        }
        for (int c = pos[1] + 1; c <= pos[1] + 4; c++) {
            LocalDate d = PiExcelSupport.date(sheet, pos[0], c);
            if (d != null && d.getYear() > 2000) {
                return d;
            }
        }
        return null;
    }

    private int[] findLabel(Sheet sheet, String label) {
        String target = PiExcelSupport.compact(label);
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 12); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                if (PiExcelSupport.compact(PiExcelSupport.rawText(sheet, r, c)).equals(target)) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────
    //  값 정규화
    // ─────────────────────────────────────────────────────────────

    /** 평가값을 Y / N / NA 로 맞춘다. 해석 불가하거나 비어 있으면 null */
    static String normalizeEvaluation(String raw) {
        String v = PiExcelSupport.compact(raw).toUpperCase(Locale.ROOT);
        if (v.isEmpty()) {
            return null;
        }
        if (v.startsWith("NA") || v.startsWith("N/A") || v.contains("해당없음") || v.contains("대상아님") || v.equals("-")) {
            return "NA";
        }
        if (v.startsWith("Y") || v.equals("O") || v.equals("○") || v.equals("◯") || v.equals("V") || v.equals("✓")
                || v.startsWith("충족") || v.startsWith("확인") || v.startsWith("완료") || v.startsWith("예")) {
            return "Y";
        }
        if (v.startsWith("N") || v.equals("X") || v.equals("×") || v.startsWith("미충족") || v.startsWith("미확인")
                || v.startsWith("미완료") || v.startsWith("아니오")) {
            return "N";
        }
        return null;
    }

    private static String normalizeOwned(String raw) {
        String v = PiExcelSupport.compact(raw);
        if (v.isEmpty()) {
            return null;
        }
        if (v.startsWith("미") || v.equalsIgnoreCase("N")) {
            return "N";
        }
        return v.startsWith("보유") || v.equalsIgnoreCase("Y") ? "Y" : null;
    }

    private static boolean isBlankOrPlaceholder(String s) {
        return s == null || s.isBlank() || s.contains("OOO") || s.contains("ooo");
    }

    // ─────────────────────────────────────────────────────────────
    //  결과 타입
    // ─────────────────────────────────────────────────────────────

    private static final class Columns {
        int category = -1, important = -1, question = -1, method = -1, status = -1, evaluation = -1;
        int actionPlan = -1, schedule = -1, confirmDate = -1, confirmer = -1, remark = -1;
        int dataStartRow;
    }

    public record ParsedItem(ChecklistSheetType sheetType, int rowNo, int sortOrder, String category,
                             boolean important, String question, String checkMethod, String currentStatus,
                             String evaluation, String evaluationRaw, String actionPlan, String actionSchedule,
                             LocalDate confirmDate, String confirmerName, String itemRemark) {
    }

    public record ParsedChecklist(String companyName, String taskName, String evaluator, LocalDate evaluationDate,
                                  String systemOwnedYn, String systemName,
                                  int total, int yCount, int nCount, int naCount, int blankCount,
                                  InspectionResult result, List<ParsedItem> items, List<String> warnings) {

        static final class ParsedChecklistBuilder {
            private String companyName, taskName, evaluator, systemOwnedYn, systemName;
            private LocalDate evaluationDate;
            private int total, y, n, na, blank;
            private InspectionResult result;
            private List<ParsedItem> items;
            private List<String> warnings;

            ParsedChecklistBuilder companyName(String v) { this.companyName = v; return this; }
            ParsedChecklistBuilder taskName(String v) { this.taskName = v; return this; }
            ParsedChecklistBuilder evaluator(String v) { this.evaluator = v; return this; }
            ParsedChecklistBuilder evaluationDate(LocalDate v) { this.evaluationDate = v; return this; }
            ParsedChecklistBuilder systemOwnedYn(String v) { this.systemOwnedYn = v; return this; }
            ParsedChecklistBuilder systemName(String v) { this.systemName = v; return this; }
            ParsedChecklistBuilder items(List<ParsedItem> v) { this.items = v; return this; }
            ParsedChecklistBuilder warnings(List<String> v) { this.warnings = v; return this; }
            ParsedChecklistBuilder result(InspectionResult v) { this.result = v; return this; }
            ParsedChecklistBuilder counts(int total, int y, int n, int na, int blank) {
                this.total = total; this.y = y; this.n = n; this.na = na; this.blank = blank; return this;
            }

            ParsedChecklist build() {
                return new ParsedChecklist(companyName, taskName, evaluator, evaluationDate, systemOwnedYn, systemName,
                        total, y, n, na, blank, result, items, warnings);
            }
        }
    }
}
