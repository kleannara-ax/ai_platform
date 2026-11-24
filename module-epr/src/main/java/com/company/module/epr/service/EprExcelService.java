package com.company.module.epr.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.epr.dto.MasterExcelRow;
import com.company.module.epr.dto.ParsedSheet;
import com.company.module.epr.dto.PartSummary;
import com.company.module.epr.dto.SalesCalculationResult;
import com.company.module.epr.dto.SalesExcelRow;
import com.company.module.epr.dto.SkuCalculation;
import com.company.module.epr.dto.UploadIssue;
import com.company.module.epr.dto.WeightExcelRow;
import com.company.module.epr.entity.EprProductMaster;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * EPR 엑셀 양식 만들기 · 올린 엑셀 해석 · 결과 리포트.
 *
 * <p>해석기는 상단 빈 행과 좌측 빈 열을 건너뛰고 "제품코드" 헤더가 있는 행을 찾는다.
 * 헤더는 공백·대소문자·"(g)" 를 무시하고 비교한다. 한 행의 값이 잘못되면 그 행만 확인 필요로 남기고 계속 읽는다.
 */
@Service
public class EprExcelService {

    public static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

    private static final List<String> MASTER_HEADERS = List.of("제품코드", "제품명", "EA/BAG", "BAG/BOX", "EA/BOX",
            "BAG/PLT", "BOX/PLT", "BAG 포장중량(g)", "BOX 포장중량(g)", "구분(자사/타사)");
    /** 구분 열이 없는 예전 마스터 엑셀도 받는다 (구분은 자사로 본다). */
    private static final List<String> MASTER_REQUIRED_HEADERS = MASTER_HEADERS.subList(0, MASTER_HEADERS.size() - 1);
    private static final List<String> WEIGHT_HEADERS = List.of("제품코드", "제품명", "BAG 포장중량(g)", "BOX 포장중량(g)");
    private static final List<String> SALES_HEADERS = List.of("제품코드", "제품명", "EA");

    private final DataFormatter formatter = new DataFormatter(Locale.KOREA);

    /** 업로드 파일 기본 검사: 비어 있지 않은 .xlsx, 20MB 이하 */
    public void requireXlsx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 엑셀 파일을 선택하세요.");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, ".xlsx 형식의 파일만 업로드할 수 있습니다.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일 크기는 20MB 이하여야 합니다.");
        }
    }

    // ───────────────────────── 양식 ─────────────────────────

    public byte[] createMasterTemplate(boolean includeData, List<EprProductMaster> products) {
        Workbook workbook = includeData ? new SXSSFWorkbook(200) : new XSSFWorkbook();
        if (workbook instanceof SXSSFWorkbook streaming) streaming.setCompressTempFiles(true);
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("제품 마스터");
            trackForAutoSize(sheet, MASTER_HEADERS.size());
            writeHeader(sheet, workbook, MASTER_HEADERS);
            int rowIndex = 1;
            if (includeData) {
                for (EprProductMaster product : products) writeMasterRow(sheet.createRow(rowIndex++), product);
            } else {
                writeValues(sheet.createRow(rowIndex), List.of("FRT-EXAMPLE01", "예시 제품명", 12, 10, 120, 100, 10,
                        new BigDecimal("125.0000"), new BigDecimal("350.0000"), EprProductMaster.OWN_COMPANY));
            }
            autosize(sheet, MASTER_HEADERS.size());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "마스터 양식을 만들지 못했습니다.");
        }
    }

    public byte[] createWeightTemplate(List<EprProductMaster> products) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("포장중량 업데이트");
            trackForAutoSize(sheet, WEIGHT_HEADERS.size());
            writeHeader(sheet, workbook, WEIGHT_HEADERS);
            int rowIndex = 1;
            for (EprProductMaster product : products) {
                writeValues(sheet.createRow(rowIndex++), List.of(product.getProductCode(), product.getProductName(),
                        decimalOrZero(product.getBagWeight()), decimalOrZero(product.getBoxWeight())));
            }
            autosize(sheet, WEIGHT_HEADERS.size());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "포장중량 양식을 만들지 못했습니다.");
        }
    }

    public byte[] createSalesTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("판매수량");
            writeHeader(sheet, workbook, SALES_HEADERS);
            writeValues(sheet.createRow(1), List.of("FRT-EXAMPLE01", "예시 제품명", 1000));
            autosize(sheet, SALES_HEADERS.size());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "판매수량 양식을 만들지 못했습니다.");
        }
    }

    // ───────────────────────── 해석 ─────────────────────────

    public ParsedSheet<MasterExcelRow> parseMaster(InputStream input) {
        return parse(input, "마스터", MASTER_REQUIRED_HEADERS, (row, columns, rowNumber) -> {
            String code = cell(row, columns, "제품코드");
            String name = cell(row, columns, "제품명");
            require(code, "제품코드");
            require(name, "제품명");
            return new MasterExcelRow(rowNumber, code.trim(), name.trim(),
                    integer(row, columns, "EA/BAG"), integer(row, columns, "BAG/BOX"),
                    integer(row, columns, "EA/BOX"), integer(row, columns, "BAG/PLT"),
                    integer(row, columns, "BOX/PLT"), decimal(row, columns, "BAG 포장중량"),
                    decimal(row, columns, "BOX 포장중량"),
                    EprProductMaster.normalizeCompanyType(cell(row, columns, "구분(자사/타사)")));
        });
    }

    public ParsedSheet<WeightExcelRow> parseWeight(InputStream input) {
        return parse(input, "중량", WEIGHT_HEADERS, (row, columns, rowNumber) -> {
            String code = cell(row, columns, "제품코드");
            require(code, "제품코드");
            return new WeightExcelRow(rowNumber, code.trim(), cell(row, columns, "제품명").trim(),
                    decimal(row, columns, "BAG 포장중량"), decimal(row, columns, "BOX 포장중량"));
        });
    }

    public ParsedSheet<SalesExcelRow> parseSales(InputStream input) {
        return parse(input, "판매수량", SALES_HEADERS, (row, columns, rowNumber) -> {
            String code = cell(row, columns, "제품코드");
            require(code, "제품코드");
            return new SalesExcelRow(rowNumber, code.trim(), cell(row, columns, "제품명").trim(),
                    salesQuantity(row, columns, "EA"));
        });
    }

    private <T> ParsedSheet<T> parse(InputStream input, String sheetLabel, List<String> requiredHeaders,
                                     RowParser<T> parser) {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 시트가 없습니다.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Header header = findHeader(sheet);
            if (header == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "제품코드 헤더를 찾지 못했습니다. " + sheetLabel + " 양식을 확인하세요.");
            }
            List<String> missingHeaders = requiredHeaders.stream()
                    .filter(label -> !header.columns().containsKey(canonical(label))).toList();
            if (!missingHeaders.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "필수 헤더가 누락되었습니다: " + String.join(", ", missingHeaders));
            }
            List<T> rows = new ArrayList<>();
            List<UploadIssue> issues = new ArrayList<>();
            for (int i = header.rowIndex() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (isBlank(row)) continue;
                int displayRow = i + 1;
                try {
                    rows.add(parser.parse(row, header.columns(), displayRow));
                } catch (RuntimeException exception) {
                    issues.add(new UploadIssue(displayRow, safeCell(row, header.columns().get("제품코드")),
                            exception.getMessage() == null ? "행 데이터를 확인하세요." : exception.getMessage()));
                }
            }
            return new ParsedSheet<>(rows, issues);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일을 읽을 수 없습니다. .xlsx 파일인지 확인하세요.");
        }
    }

    private Header findHeader(Sheet sheet) {
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : row) {
                String label = canonical(formatter.formatCellValue(cell).trim());
                if (!label.isBlank()) columns.putIfAbsent(label, cell.getColumnIndex());
            }
            if (columns.containsKey("제품코드")) return new Header(rowIndex, columns);
        }
        return null;
    }

    /** 글자 칸(제품코드·제품명·구분). 수식 칸은 엑셀이 저장해 둔 결과값을 쓴다. */
    private String cell(Row row, Map<String, Integer> columns, String header) {
        Integer column = columns.get(canonical(header));
        if (column == null || row.getCell(column) == null) return "";
        return text(row.getCell(column));
    }

    private String text(Cell cell) {
        if (cell.getCellType() == CellType.FORMULA) {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> plainNumber(cell.getNumericCellValue());
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> "";
            };
        }
        return formatter.formatCellValue(cell).trim();
    }

    /**
     * 숫자 칸(입수량·중량·EA). 엑셀 표시 형식(소수 자릿수, 천 단위, 회계 서식의 "-")을 거치지 않고 저장된 값 그대로 읽는다.
     * 수식 칸은 저장된 결과값, 오류 칸(#N/A 등)은 숫자가 아닌 값으로 돌려줘 그 행을 확인 필요로 남긴다.
     */
    private String number(Row row, Map<String, Integer> columns, String header) {
        Integer column = columns.get(canonical(header));
        if (column == null || row.getCell(column) == null) return "";
        Cell cell = row.getCell(column);
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> plainNumber(cell.getNumericCellValue());
            case STRING -> cell.getStringCellValue().trim().replace(",", "");
            case BLANK -> "";
            default -> "#" + type;
        };
    }

    /** double → 소수 6자리에서 반올림한 일반 표기 (0.1+0.2 같은 부동소수 꼬리 제거) */
    private static String plainNumber(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** 빈 칸은 0 */
    private Integer integer(Row row, Map<String, Integer> columns, String header) {
        String raw = number(row, columns, header);
        if (raw.isBlank()) return 0;
        try {
            return new BigDecimal(raw).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, header + " 값은 정수여야 합니다.");
        }
    }

    private BigDecimal salesQuantity(Row row, Map<String, Integer> columns, String header) {
        String raw = number(row, columns, header);
        if (raw.isBlank()) throw new BusinessException(ErrorCode.INVALID_INPUT, header + " 값이 비어 있습니다.");
        BigDecimal value;
        try {
            value = new BigDecimal(raw);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, header + " 값은 숫자여야 합니다.");
        }
        if (value.signum() < 0) throw new BusinessException(ErrorCode.INVALID_INPUT, header + " 값은 0 이상이어야 합니다.");
        return value;
    }

    /** 빈 칸은 0, 소수점 4자리 */
    private BigDecimal decimal(Row row, Map<String, Integer> columns, String header) {
        String raw = number(row, columns, header);
        if (raw.isBlank()) return BigDecimal.ZERO;
        BigDecimal value;
        try {
            value = new BigDecimal(raw);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, header + " 값은 숫자여야 합니다.");
        }
        if (value.signum() < 0) throw new BusinessException(ErrorCode.INVALID_INPUT, header + " 값은 0 이상이어야 합니다.");
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private void require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, label + " 값이 비어 있습니다.");
        }
    }

    private String safeCell(Row row, Integer column) {
        return column == null || row.getCell(column) == null ? "" : text(row.getCell(column));
    }

    /** 값이 하나도 없는 행 (수식이 빈 글자를 돌려주는 행도 빈 행으로 본다) */
    private boolean isBlank(Row row) {
        if (row == null) return true;
        for (Cell cell : row) if (!text(cell).isBlank()) return false;
        return true;
    }

    private String canonical(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "")
                .replace("(G)", "");
    }

    // ───────────────────────── 결과 리포트 ─────────────────────────

    /** 두 시트: 파트별 중량 집계 · SKU별 상세 계산 내역 */
    public byte[] createReport(SalesCalculationResult result) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            CellStyle numberStyle = numberStyle(workbook);
            CellStyle kilogramStyle = kilogramStyle(workbook);
            Sheet summary = workbook.createSheet("파트별 중량 집계");
            List<String> summaryHeaders = List.of("파트코드(앞 3자리)", "파트명", "집계 SKU 수", "총 판매수량(EA)",
                    "총 환산 BAG 수량", "총 BAG 신고중량(kg)", "총 환산 BOX 수량", "총 BOX 신고중량(kg)", "파트 총 신고중량 합계(kg)");
            trackForAutoSize(summary, summaryHeaders.size());
            writeHeader(summary, workbook, summaryHeaders);
            int index = 1;
            for (PartSummary part : result.partSummaries()) {
                Row row = summary.createRow(index++);
                writeValues(row, List.of(part.partPrefix(), part.partName(), part.skuCount(), part.totalSalesEa(),
                        part.totalConvertedBag(), part.totalBagReportWeightKg(), part.totalConvertedBox(),
                        part.totalBoxReportWeightKg(), part.totalReportWeightKg()));
                formatNumericCells(row, 2, 8, numberStyle);
                formatNumericCells(row, 5, 5, kilogramStyle);
                formatNumericCells(row, 7, 8, kilogramStyle);
            }
            List<SkuCalculation> registered = result.skuDetails().stream()
                    .filter(item -> !EprCalculationService.STATUS_UNREGISTERED.equals(item.status())).toList();
            Row total = summary.createRow(index);
            total.createCell(0).setCellValue("전체 합계");
            writeValuesFrom(total, 2, List.of(registered.size(),
                    registered.stream().map(SkuCalculation::salesEa).reduce(BigDecimal.ZERO, BigDecimal::add),
                    result.partSummaries().stream().map(PartSummary::totalConvertedBag).reduce(BigDecimal.ZERO, BigDecimal::add),
                    result.partSummaries().stream().map(PartSummary::totalBagReportWeightKg).reduce(BigDecimal.ZERO, BigDecimal::add),
                    result.partSummaries().stream().map(PartSummary::totalConvertedBox).reduce(BigDecimal.ZERO, BigDecimal::add),
                    result.partSummaries().stream().map(PartSummary::totalBoxReportWeightKg).reduce(BigDecimal.ZERO, BigDecimal::add),
                    result.grandTotalWeightKg()));
            formatNumericCells(total, 2, 8, numberStyle);
            formatNumericCells(total, 5, 5, kilogramStyle);
            formatNumericCells(total, 7, 8, kilogramStyle);
            autosize(summary, summaryHeaders.size());

            Sheet details = workbook.createSheet("SKU별 상세 계산 내역");
            List<String> detailHeaders = List.of("파트코드", "파트명", "제품코드", "제품명(마스터)", "구분(마스터)",
                    "업로드 제품명", "판매수량(EA)", "EA/BAG", "BAG 포장중량(g)", "BAG 신고중량(kg)", "EA/BOX",
                    "BOX 포장중량(g)", "BOX 신고중량(kg)", "SKU 총 신고중량(kg)", "상태(정상/미등록코드/중량미입력)");
            trackForAutoSize(details, detailHeaders.size());
            writeHeader(details, workbook, detailHeaders);
            index = 1;
            for (SkuCalculation item : result.skuDetails()) {
                Row row = details.createRow(index++);
                writeValues(row, List.of(item.partPrefix(), item.partName(), item.productCode(), item.masterProductName(),
                        item.companyType() == null ? "" : item.companyType(), item.uploadedProductName(), item.salesEa(),
                        item.eaPerBag(), item.bagWeightG(), item.bagReportWeightKg(), item.eaPerBox(), item.boxWeightG(),
                        item.boxReportWeightKg(), item.totalReportWeightKg(), item.status()));
                for (int c = 6; c <= 13; c++) row.getCell(c).setCellStyle(numberStyle);
                for (int c : List.of(9, 12, 13)) row.getCell(c).setCellStyle(kilogramStyle);
            }
            autosize(details, detailHeaders.size());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "결과 리포트를 만들지 못했습니다.");
        }
    }

    // ───────────────────────── 공용 ─────────────────────────

    private void writeMasterRow(Row row, EprProductMaster product) {
        writeValues(row, List.of(product.getProductCode(), product.getProductName(), product.getEaPerBag(),
                product.getBagPerBox(), product.getEaPerBox(), product.getBagPerPlt(), product.getBoxPerPlt(),
                decimalOrZero(product.getBagWeight()), decimalOrZero(product.getBoxWeight()), product.getCompanyType()));
    }

    private BigDecimal decimalOrZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private void writeHeader(Sheet sheet, Workbook workbook, List<String> headers) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(24);
        CellStyle style = headerStyle(workbook);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.size() - 1));
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        return style;
    }

    private CellStyle numberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.000"));
        return style;
    }

    private CellStyle kilogramStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private void writeValues(Row row, List<?> values) {
        for (int i = 0; i < values.size(); i++) writeCell(row.createCell(i), values.get(i));
    }

    private void writeValuesFrom(Row row, int startColumn, List<?> values) {
        for (int i = 0; i < values.size(); i++) writeCell(row.createCell(startColumn + i), values.get(i));
    }

    private void writeCell(Cell cell, Object value) {
        if (value == null) return;
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(String.valueOf(value));
    }

    private void formatNumericCells(Row row, int first, int last, CellStyle style) {
        for (int c = first; c <= last; c++) if (row.getCell(c) != null) row.getCell(c).setCellStyle(style);
    }

    private void trackForAutoSize(Sheet sheet, int columns) {
        if (sheet instanceof SXSSFSheet streaming) {
            for (int i = 0; i < columns; i++) streaming.trackColumnForAutoSizing(i);
        }
    }

    private void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, 15000));
        }
    }

    @FunctionalInterface
    private interface RowParser<T> {
        T parse(Row row, Map<String, Integer> columns, int rowNumber);
    }

    private record Header(int rowIndex, Map<String, Integer> columns) { }
}
