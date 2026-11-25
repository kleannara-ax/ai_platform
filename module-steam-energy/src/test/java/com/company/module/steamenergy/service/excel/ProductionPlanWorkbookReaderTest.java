package com.company.module.steamenergy.service.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 생산계획 엑셀 읽기 — 실제 예시 파일("생산계획 예시.xlsx")의 배치를 그대로 만든 워크북으로 확인한다. */
class ProductionPlanWorkbookReaderTest {

    /** 예시 파일 배치: B=묶음, C=항목, D="25년 12월", E~P="1월"~"12월", Q="27년 1월". 값은 10월(N)~27년 1월(Q). */
    private static Workbook exampleWorkbook() {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("생산계획");
        Row head = s.createRow(0);
        head.createCell(1).setCellValue("구분");
        head.createCell(3).setCellValue("25년 12월");
        for (int m = 1; m <= 12; m += 1) head.createCell(3 + m).setCellValue(m + "월");
        head.createCell(16).setCellValue("27년 1월");
        Row kind = s.createRow(1);
        for (int c = 3; c <= 16; c += 1) kind.createCell(c).setCellValue(c == 12 || c == 13 ? "실적" : "예상");

        group(s, 2, "제지2", "점보롤 (톤)", new double[]{4563.1976, 4349.0095, 5044.0245, 5199.3169}, new double[]{348.4833, 333, 385, 389});
        group(s, 5, "제지3", "생산량 (톤)", new double[]{28308.8543, 26989.8073, 27599.6287, 28459.0424}, new double[]{685.7167, 675, 695, 699});
        group(s, 8, "제지\n계\n\n(11/3 송부)", "생산량 (톤)", new double[]{32872.05, 31338.82, 32643.65, 33658.36}, new double[]{1034.2, 1008, 1080, 1088});
        group(s, 11, "화장지3", "생산량 (톤)", new double[]{541.08, 656, 407, 422}, new double[]{238, 305, 185, 185});
        group(s, 14, "화장지4", "생산량 (톤)", new double[]{990.226, 914, 1188, 1248}, new double[]{426.5, 401, 545, 545});
        group(s, 17, "화장지5", "생산량 (톤)", new double[]{2133.186, 2401, 2483, 2491}, new double[]{618.5, 700, 724, 724});
        group(s, 20, "화)초지\n계", "생산량 (kg, EA)", new double[]{3664.492, 3971, 4078, 4161}, new double[]{1283, 1406, 1454, 1454});
        group(s, 23, "가공3", "생산량 (천개)", new double[]{5530.978, 3885, 4164, 6225}, new double[]{529.2, 429, 451, 666});
        return wb;
    }

    private static void group(Sheet s, int firstRow, String name, String productionLabel, double[] production, double[] hours) {
        Row p = s.createRow(firstRow);
        p.createCell(1).setCellValue(name);
        p.createCell(2).setCellValue(productionLabel);
        Row h = s.createRow(firstRow + 1);
        h.createCell(2).setCellValue("가동시간 (Hr)");
        Row y = s.createRow(firstRow + 2);
        y.createCell(2).setCellValue("생산성 (생산량/일)");
        for (int i = 0; i < 4; i += 1) {
            p.createCell(13 + i).setCellValue(production[i]);
            h.createCell(13 + i).setCellValue(hours[i]);
            y.createCell(13 + i).setCellFormula("N" + (firstRow + 1) + "/N" + (firstRow + 2) + "*24");
        }
    }

    private static ProductionPlanWorkbookReader.PlanRow find(List<ProductionPlanWorkbookReader.PlanRow> rows, int year, int month, String machine) {
        return rows.stream().filter(r -> r.year() == year && r.month() == month && r.machineCode().equals(machine))
                .findFirst().orElse(null);
    }

    @Test
    void 예시_배치를_읽는다() throws Exception {
        try (Workbook wb = exampleWorkbook()) {
            ProductionPlanWorkbookReader.Result result = ProductionPlanWorkbookReader.read(wb, 2020);

            assertThat(result.sheetName()).isEqualTo("생산계획");
            // 5개 호기 × 4개 달 (10월, 11월, 12월, 27년 1월). 계·가공 묶음은 읽지 않는다.
            assertThat(result.rows()).hasSize(20);
            assertThat(result.rows()).extracting(ProductionPlanWorkbookReader.PlanRow::machineCode)
                    .containsOnly("PM-2", "PM-3", "TM-3", "TM-4", "TM-5");

            ProductionPlanWorkbookReader.PlanRow nov = find(result.rows(), 2026, 11, "PM-2");
            assertThat(nov.productionTon()).isEqualByComparingTo(new BigDecimal("4349.0095"));
            assertThat(nov.runtimeHours()).isEqualByComparingTo(new BigDecimal("333"));
            assertThat(nov.planKind()).isEqualTo("예상");

            assertThat(find(result.rows(), 2026, 10, "PM-3").planKind()).isEqualTo("실적");
            assertThat(find(result.rows(), 2026, 12, "TM-5").productionTon()).isEqualByComparingTo(new BigDecimal("2483"));
            // "27년 1월" 은 연도가 적힌 칸이다.
            assertThat(find(result.rows(), 2027, 1, "TM-4").runtimeHours()).isEqualByComparingTo(new BigDecimal("545"));
            // 값이 없는 달(1~9월)은 줄을 만들지 않는다.
            assertThat(find(result.rows(), 2026, 9, "PM-3")).isNull();
        }
    }

    @Test
    void 연도가_적힌_칸이_없으면_화면_연도를_쓴다() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("26년 생산계획(안)");
            Row head = s.createRow(2);
            for (int m = 1; m <= 12; m += 1) head.createCell(4 + m).setCellValue(m + " 월");
            // 순서를 바꾼 배치: 화장지가 먼저, 가동시간 줄이 생산량 줄보다 위
            Row h = s.createRow(4);
            h.createCell(0).setCellValue("화장지 5");
            h.createCell(1).setCellValue("가동시간(Hr)");
            h.createCell(15).setCellValue(700);
            Row p = s.createRow(5);
            p.createCell(1).setCellValue("생산량(톤)");
            p.createCell(15).setCellValue(2401);
            Row p3 = s.createRow(6);
            p3.createCell(0).setCellValue("제지 3");
            p3.createCell(1).setCellValue("생산량(톤)");
            p3.createCell(15).setCellValue(26990);

            ProductionPlanWorkbookReader.Result result = ProductionPlanWorkbookReader.read(wb, 2026);

            assertThat(result.rows()).hasSize(2);
            ProductionPlanWorkbookReader.PlanRow tm5 = find(result.rows(), 2026, 11, "TM-5");
            assertThat(tm5.productionTon()).isEqualByComparingTo(new BigDecimal("2401"));
            assertThat(tm5.runtimeHours()).isEqualByComparingTo(new BigDecimal("700"));
            assertThat(tm5.planKind()).isNull();
            assertThat(find(result.rows(), 2026, 11, "PM-3").runtimeHours()).isNull();
        }
    }

    @Test
    void 월_머리글이_없으면_알려준다() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("메모").createRow(0).createCell(0).setCellValue("내용 없음");
            assertThatThrownBy(() -> ProductionPlanWorkbookReader.read(wb, 2026))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("생산계획 시트");
        }
    }
}
