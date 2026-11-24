package com.company.module.epr.service;

import com.company.core.common.exception.BusinessException;
import com.company.module.epr.dto.MasterExcelRow;
import com.company.module.epr.dto.PartSummary;
import com.company.module.epr.dto.SalesCalculationResult;
import com.company.module.epr.dto.SkuCalculation;
import com.company.module.epr.entity.EprProductMaster;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 원래 프로젝트 통합 테스트 중 엑셀 관련 항목을 DB 없이 옮긴 것 */
class EprExcelServiceTest {

    private final EprExcelService excelService = new EprExcelService();

    private static EprProductMaster product(String code, String companyType) {
        return EprProductMaster.builder().productCode(code).productName("제품 " + code)
                .eaPerBag(12).bagPerBox(10).eaPerBox(120).bagPerPlt(100).boxPerPlt(10)
                .bagWeight(new BigDecimal("125.5")).boxWeight(new BigDecimal("350")).companyType(companyType).build();
    }

    @Test
    void masterTemplateHasHeaderAndExampleRowAndParsesBack() throws IOException {
        byte[] bytes = excelService.createMasterTemplate(false, List.of());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("제품코드");
            assertThat(header.getCell(9).getStringCellValue()).isEqualTo("구분(자사/타사)");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("FRT-EXAMPLE01");
        }
        var parsed = excelService.parseMaster(new ByteArrayInputStream(bytes));
        assertThat(parsed.issues()).isEmpty();
        assertThat(parsed.rows()).singleElement().satisfies(row -> {
            assertThat(row.eaPerBox()).isEqualTo(120);
            assertThat(row.boxWeight()).isEqualByComparingTo("350");
            assertThat(row.companyType()).isEqualTo("자사");
        });
    }

    @Test
    void dataTemplateExportsSavedCompanyType() {
        byte[] bytes = excelService.createMasterTemplate(true, List.of(product("FRT-A1", "자사"), product("FKT-B2", "타사")));
        var parsed = excelService.parseMaster(new ByteArrayInputStream(bytes));
        assertThat(parsed.rows()).extracting(MasterExcelRow::productCode, MasterExcelRow::companyType)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FRT-A1", "자사"),
                        org.assertj.core.groups.Tuple.tuple("FKT-B2", "타사"));
    }

    @Test
    void parserFindsHeaderAtB2TrimsCodeAndAcceptsLegacyMasterWithoutCompanyType() throws IOException {
        byte[] bytes = workbook(sheet -> {
            Row header = sheet.createRow(1);
            String[] labels = {"제품코드", "제품명", "EA/BAG", "BAG/BOX", "EA/BOX", "BAG/PLT", "BOX/PLT", "BAG 포장중량(g)", "BOX 포장중량(g)"};
            for (int i = 0; i < labels.length; i++) header.createCell(i + 1).setCellValue(labels[i]);
            Row data = sheet.createRow(2);
            data.createCell(1).setCellValue("  frt-legacy01 ");
            data.createCell(2).setCellValue("예전 양식 제품");
            data.createCell(3).setCellValue(6);
            data.createCell(5).setCellValue(60);
            data.createCell(8).setCellValue("12.5");
            Row bad = sheet.createRow(3);
            bad.createCell(1).setCellValue("FRT-BAD01");
            bad.createCell(2).setCellValue("정수가 아닌 입수량");
            bad.createCell(3).setCellValue("1.5");
        });
        var parsed = excelService.parseMaster(new ByteArrayInputStream(bytes));
        assertThat(parsed.rows()).singleElement().satisfies(row -> {
            assertThat(row.productCode()).isEqualTo("frt-legacy01");
            assertThat(row.rowNumber()).isEqualTo(3);
            assertThat(row.eaPerBag()).isEqualTo(6);
            assertThat(row.bagPerBox()).isZero();
            assertThat(row.bagWeight()).isEqualByComparingTo("12.5");
            assertThat(row.companyType()).isEqualTo("자사");
        });
        assertThat(parsed.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.rowNumber()).isEqualTo(4);
            assertThat(issue.message()).contains("EA/BAG");
        });
    }

    @Test
    void numbersAreReadAsStoredNotAsDisplayedAndFormulaCellsUseCachedResults() throws IOException {
        byte[] bytes = workbook(sheet -> {
            Workbook book = sheet.getWorkbook();
            org.apache.poi.ss.usermodel.CellStyle oneDecimal = book.createCellStyle();
            oneDecimal.setDataFormat(book.createDataFormat().getFormat("0.0"));
            org.apache.poi.ss.usermodel.CellStyle thousands = book.createCellStyle();
            thousands.setDataFormat(book.createDataFormat().getFormat("#,##0"));
            org.apache.poi.ss.usermodel.CellStyle accounting = book.createCellStyle();
            accounting.setDataFormat(book.createDataFormat().getFormat("_-* #,##0_-;-* #,##0_-;_-* \"-\"_-;_-@_-"));

            Row header = sheet.createRow(0);
            String[] labels = {"제품코드", "제품명", "BAG 포장중량(g)", "BOX 포장중량(g)"};
            for (int i = 0; i < labels.length; i++) header.createCell(i).setCellValue(labels[i]);
            Row data = sheet.createRow(1);
            data.createCell(0).setCellFormula("\"FRT-\"&\"F001\"");
            data.createCell(1).setCellValue("수식 코드");
            org.apache.poi.ss.usermodel.Cell bag = data.createCell(2);
            bag.setCellValue(12.3456);                 // 화면에는 12.3 으로 보이는 칸
            bag.setCellStyle(oneDecimal);
            org.apache.poi.ss.usermodel.Cell box = data.createCell(3);
            box.setCellFormula("100+20.25");           // 수식 (화면에는 120)
            box.setCellStyle(thousands);
            Row zero = sheet.createRow(2);
            zero.createCell(0).setCellValue("FRT-Z001");
            org.apache.poi.ss.usermodel.Cell dash = zero.createCell(2);
            dash.setCellValue(0);                      // 회계 서식에서 "-" 로 보이는 0
            dash.setCellStyle(accounting);
            Row emptyFormulaRow = sheet.createRow(3);
            emptyFormulaRow.createCell(0).setCellFormula("\"\"");   // 수식이 빈 글자를 돌려주는 행 = 빈 행
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
        });
        var parsed = excelService.parseWeight(new ByteArrayInputStream(bytes));
        assertThat(parsed.issues()).isEmpty();
        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.rows().get(0).productCode()).isEqualTo("FRT-F001");
        assertThat(parsed.rows().get(0).bagWeight()).isEqualByComparingTo("12.3456");
        assertThat(parsed.rows().get(0).boxWeight()).isEqualByComparingTo("120.25");
        assertThat(parsed.rows().get(1).bagWeight()).isEqualByComparingTo("0");
    }

    @Test
    void salesQuantityKeepsDecimalsHiddenByCellFormat() throws IOException {
        byte[] bytes = workbook(sheet -> {
            Workbook book = sheet.getWorkbook();
            org.apache.poi.ss.usermodel.CellStyle thousands = book.createCellStyle();
            thousands.setDataFormat(book.createDataFormat().getFormat("#,##0"));
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("제품코드");
            header.createCell(1).setCellValue("제품명");
            header.createCell(2).setCellValue("EA");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("FRT-S001");
            org.apache.poi.ss.usermodel.Cell ea = data.createCell(2);
            ea.setCellValue(1234.5);
            ea.setCellStyle(thousands);
            Row error = sheet.createRow(2);
            error.createCell(0).setCellValue("FRT-S002");
            error.createCell(2).setCellFormula("1/0");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
        });
        var parsed = excelService.parseSales(new ByteArrayInputStream(bytes));
        assertThat(parsed.rows()).singleElement().satisfies(row -> assertThat(row.salesEa()).isEqualByComparingTo("1234.5"));
        assertThat(parsed.issues()).singleElement().satisfies(issue -> assertThat(issue.rowNumber()).isEqualTo(3));
    }

    @Test
    void missingRequiredHeaderIsRejected() throws IOException {
        byte[] bytes = workbook(sheet -> {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("제품코드");
            header.createCell(1).setCellValue("제품명");
        });
        assertThatThrownBy(() -> excelService.parseSales(new ByteArrayInputStream(bytes)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("EA");
    }

    @Test
    void nonExcelFileIsRejectedWithReadableMessage() {
        assertThatThrownBy(() -> excelService.parseSales(new ByteArrayInputStream("not excel".getBytes())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("엑셀 파일을 읽을 수 없습니다");
        assertThatThrownBy(() -> excelService.requireXlsx(new MockMultipartFile("file", "a.csv", "text/csv", "x".getBytes())))
                .isInstanceOf(BusinessException.class).hasMessageContaining(".xlsx");
    }

    @Test
    void reportHasTwoSheets() throws IOException {
        SkuCalculation sku = new SkuCalculation("FRT", "화장지 제품", "FRT-A1", "제품", "자사", "업로드", BigDecimal.TEN,
                10, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.01"), 0, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.01"), "정상", 1);
        PartSummary part = new PartSummary("FRT", "화장지 제품", 1, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("0.01"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.01"));
        SalesCalculationResult result = new SalesCalculationResult(2026, LocalDateTime.now(), 1, BigDecimal.TEN, 1, 0, 0,
                new BigDecimal("0.01"), List.of(part), List.of(sku), List.of(), List.of());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(excelService.createReport(result)))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetName(0)).isEqualTo("파트별 중량 집계");
            assertThat(workbook.getSheetName(1)).isEqualTo("SKU별 상세 계산 내역");
            assertThat(workbook.getSheetAt(0).getRow(2).getCell(0).getStringCellValue()).isEqualTo("전체 합계");
            assertThat(workbook.getSheetAt(1).getRow(1).getCell(2).getStringCellValue()).isEqualTo("FRT-A1");
        }
    }

    private interface SheetWriter {
        void write(Sheet sheet);
    }

    private static byte[] workbook(SheetWriter writer) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writer.write(workbook.createSheet("시트1"));
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
