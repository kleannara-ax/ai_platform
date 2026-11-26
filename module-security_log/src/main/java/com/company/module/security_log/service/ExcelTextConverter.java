package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 엑셀(.xlsx / .xls) → 텍스트(TSV, UTF-8) 변환기
 *
 * <p>DLP, EPP 등 관리 콘솔에서 내려받은 엑셀 로그/계정 목록을 기존 텍스트 분석기로 그대로 분석하기 위해 사용한다.
 * <ul>
 *   <li>데이터가 있는 첫 번째 시트만 변환한다.</li>
 *   <li>앞쪽의 빈 행, 빈 열(제목 여백)은 건너뛴다. 첫 데이터 행이 머리글이 된다.</li>
 *   <li>셀 안 줄바꿈/탭은 " / " 로 바꿔 1행 = 1줄을 유지한다.</li>
 *   <li>날짜 서식 셀은 yyyy-MM-dd HH:mm:ss 로 출력한다.</li>
 * </ul>
 * 원본 엑셀은 그대로 보관(증적)하고, 이 변환 결과는 분석에만 사용한다.
 */
@Component
public class ExcelTextConverter {

    /** 엑셀 업로드 최대 크기 (압축 해제 시 메모리 사용량이 커서 텍스트보다 작게 제한) */
    public static final long MAX_EXCEL_SIZE = 20L * 1024 * 1024;
    private static final int MAX_ROWS = SecLogConstants.MAX_LINE_COUNT;
    private static final int MAX_COLS = 200;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static boolean isExcel(String fileName) {
        String ext = fileName == null ? null : StringUtils.getFilenameExtension(fileName);
        return ext != null && ("xlsx".equalsIgnoreCase(ext) || "xls".equalsIgnoreCase(ext));
    }

    /** 엑셀 한 행 (rowNo = 엑셀 행 번호, 1부터. cells = 앞쪽 빈 열을 뺀 셀 값) */
    public record SheetRow(int rowNo, List<String> cells) {
    }

    /** 엑셀 시트 1개 (name = 탭 이름) */
    public record NamedSheet(String name, List<SheetRow> rows) {
    }

    /**
     * 엑셀 → 모든 시트의 행 목록 (빈 시트 제외). 개인정보 다운로드 내역 탭처럼
     * 첫 번째가 아닌 시트를 읽을 때 사용.
     */
    public List<NamedSheet> readAllSheets(byte[] content) {
        if (content.length > MAX_EXCEL_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "엑셀 파일은 최대 20MB 까지 업로드할 수 있습니다. 더 큰 파일은 CSV 로 저장해 올려 주세요.");
        }
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            DataFormatter fmt = new DataFormatter(Locale.KOREA);
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            List<NamedSheet> out = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sh = wb.getSheetAt(i);
                List<SheetRow> rows = rowsOf(sh, fmt, ev);
                if (!rows.isEmpty()) {
                    out.add(new NamedSheet(sh.getSheetName(), rows));
                }
            }
            return out;
        } catch (EncryptedDocumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "암호가 걸린 엑셀은 업로드할 수 없습니다. 암호를 해제한 뒤 올려 주세요.");
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "엑셀 파일을 읽을 수 없습니다. 파일이 손상되지 않았는지 확인해 주세요.");
        }
    }

    /**
     * 엑셀 → 행 목록 (원본 엑셀 행 번호 포함). 빈 행·앞쪽 빈 열은 건너뛴다.
     * 근거 로그를 엑셀 행 번호로 보여 줄 때 사용 (DLP 점검 보고서).
     */
    public List<SheetRow> readRows(byte[] content) {
        if (content.length > MAX_EXCEL_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "엑셀 파일은 최대 20MB 까지 업로드할 수 있습니다. 더 큰 파일은 CSV 로 저장해 올려 주세요.");
        }
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = firstDataSheet(wb);
            if (sheet == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "엑셀에 데이터가 있는 시트가 없습니다.");
            }
            DataFormatter fmt = new DataFormatter(Locale.KOREA);
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            List<SheetRow> out = rowsOf(sheet, fmt, ev);
            if (out.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "엑셀에 데이터가 없습니다.");
            }
            return out;
        } catch (EncryptedDocumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "암호가 걸린 엑셀은 업로드할 수 없습니다. 암호를 해제한 뒤 올려 주세요.");
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "엑셀 파일을 읽을 수 없습니다. 파일이 손상되지 않았는지 확인해 주세요.");
        }
    }

    public byte[] toTsv(byte[] content) {
        List<SheetRow> rows = readRows(content);
        StringBuilder sb = new StringBuilder(content.length * 2);
        for (SheetRow r : rows) {
            sb.append(String.join("\t", r.cells())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 시트 1개 → 행 목록 (빈 행·앞쪽 빈 열 제거, 데이터가 없으면 빈 목록) */
    private static List<SheetRow> rowsOf(Sheet sheet, DataFormatter fmt, FormulaEvaluator ev) {
        // 1차: 셀 값 추출 + 앞쪽 빈 열 계산
        List<List<String>> rows = new ArrayList<>();
        List<Integer> rowNos = new ArrayList<>();
        int minCol = Integer.MAX_VALUE;
        for (Row row : sheet) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            List<String> vals = new ArrayList<>();
            int first = -1;
            int last = Math.min(row.getLastCellNum(), MAX_COLS);
            for (int c = 0; c < last; c++) {
                String v = cellText(row.getCell(c), fmt, ev);
                vals.add(v);
                if (first < 0 && !v.isEmpty()) {
                    first = c;
                }
            }
            if (first < 0) {
                continue; // 빈 행
            }
            minCol = Math.min(minCol, first);
            rows.add(vals);
            rowNos.add(row.getRowNum() + 1);
        }
        // 2차: 앞쪽 빈 열·뒤쪽 빈 셀 제거
        List<SheetRow> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            List<String> vals = rows.get(i);
            int end = vals.size();
            while (end > minCol && vals.get(end - 1).isEmpty()) {
                end--;
            }
            out.add(new SheetRow(rowNos.get(i), minCol >= end ? List.of() : new ArrayList<>(vals.subList(minCol, end))));
        }
        return out;
    }

    private static Sheet firstDataSheet(Workbook wb) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet s = wb.getSheetAt(i);
            if (s.getPhysicalNumberOfRows() > 0) {
                return s;
            }
        }
        return null;
    }

    /** 수식 셀: 계산 실패(배열 수식 등)하면 파일에 저장된 마지막 계산 값 사용 */
    private static String formulaText(Cell cell, DataFormatter fmt, FormulaEvaluator ev) {
        try {
            return fmt.formatCellValue(cell, ev);
        } catch (RuntimeException e) {
            try {
                return switch (cell.getCachedFormulaResultType()) {
                    case STRING -> cell.getStringCellValue();
                    case NUMERIC -> fmt.formatRawCellContents(cell.getNumericCellValue(), cell.getCellStyle().getDataFormat(),
                            cell.getCellStyle().getDataFormatString());
                    case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                    default -> "";
                };
            } catch (RuntimeException ignore) {
                return "";
            }
        }
    }

    private static String cellText(Cell cell, DataFormatter fmt, FormulaEvaluator ev) {
        if (cell == null) {
            return "";
        }
        String v;
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime dt = cell.getLocalDateTimeCellValue();
            v = dt == null ? "" : dt.format(DT);
        } else if (cell.getCellType() == CellType.FORMULA) {
            v = formulaText(cell, fmt, ev);
        } else {
            v = fmt.formatCellValue(cell, ev);
        }
        if (v == null) {
            return "";
        }
        // 1행 = 1줄 유지
        return v.replace("\r\n", " / ").replace('\r', ' ').replace("\n", " / ").replace('\t', ' ').trim();
    }
}
