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

    public byte[] toTsv(byte[] content) {
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

            // 1차: 셀 값 추출 + 앞쪽 빈 열 계산
            List<List<String>> rows = new ArrayList<>();
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
            }
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "엑셀에 데이터가 없습니다.");
            }

            // 2차: TSV 작성
            StringBuilder sb = new StringBuilder(content.length * 2);
            for (List<String> vals : rows) {
                int end = vals.size();
                while (end > minCol && vals.get(end - 1).isEmpty()) {
                    end--;
                }
                for (int c = minCol; c < end; c++) {
                    if (c > minCol) {
                        sb.append('\t');
                    }
                    sb.append(vals.get(c));
                }
                sb.append('\n');
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        } catch (EncryptedDocumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "암호가 걸린 엑셀은 업로드할 수 없습니다. 암호를 해제한 뒤 올려 주세요.");
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "엑셀 파일을 읽을 수 없습니다. 파일이 손상되지 않았는지 확인해 주세요.");
        }
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

    private static String cellText(Cell cell, DataFormatter fmt, FormulaEvaluator ev) {
        if (cell == null) {
            return "";
        }
        String v;
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime dt = cell.getLocalDateTimeCellValue();
            v = dt == null ? "" : dt.format(DT);
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
