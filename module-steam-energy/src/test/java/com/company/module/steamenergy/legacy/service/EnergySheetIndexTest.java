package com.company.module.steamenergy.legacy.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.company.module.steamenergy.legacy.SteamPoiConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 에너지 회계비용 워크북의 라벨 기반 행/열 찾기({@link EnergySheetIndex}) 검증.
 *
 * <p>업로드 후 값이 한 줄씩 밀리던 원인이 {@code excelRow = htmlRow + 고정 offset} 이었다.
 * 이제 항목 이름으로 행을, 머리글로 월 열을 찾는다.
 */
class EnergySheetIndexTest {

    /** 회계비용 계획 시트: 화면 r01~r32 → 원본 3~34행 (라벨은 B열, 소계 행은 A열) */
    private static final String[] ROW_LABELS = {
            "복합보일러 스팀구매", "제스코 스팀구매", "LNG 구매", "폐합성소각로", "유동상소각로",
            "연료비 월별", "제스코 상계비 (용수사용 등)", "제스코 페널티", "제스코 선급금 상환",
            "복합 LNG, 용수사용량", "복합 투자비 감가상각", "복합보일러 처분수익 연료비 차감분",
            "토지 지상권 지료", "차감비 소계", "연료비(회계비용) 실적", "제지 연료비(스팀사용비율)",
            "생활 연료비(스팀사용비율)", "연료비(회계비용) 계획금액", "회계비용 대비 증감", "전월대비 증감",
            "생산량", "제지 제품생산량 (24년 반영)", "화장지 제품생산량 (24년 반영)",
            "총 원단위 (연료비/생산량)", "제지 원단위(연료비/생산량)", "생활 원단위(연료비/생산량)",
            "전월대비 손익증감", "제지손익", "생활손익", "손익합계(제지+생활)",
            "스팀생산량", "LNG 직버너 사용 비용"
    };

    private static final int[] LABEL_COLS = {1, 2};
    private static final int MONTH_HEADER_ROW = 2;
    private static final int FIRST_DATA_ROW = 3;

    /** 원본 워크북은 엔트리가 많아 POI 기본 한도(1000개)에 걸린다 — 앱과 같은 한도를 적용한다. */
    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    @Test
    @DisplayName("원본 시트에서 32개 항목이 모두 기존 좌표와 같은 행으로 해석된다")
    void resolvesAllRowsOnOriginalWorkbook() throws Exception {
        Path workbookPath = findWorkbook();
        Assumptions.assumeTrue(workbookPath != null, "에너지 회계비용 원본 엑셀이 없어 건너뜀");

        List<String> problems = new ArrayList<>();
        try (InputStream in = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheet("25년 회계비용 계획");
            Assumptions.assumeTrue(sheet != null, "'25년 회계비용 계획' 시트가 없어 건너뜀");

            EnergySheetIndex index = EnergySheetIndex.build(sheet, LABEL_COLS, FIRST_DATA_ROW, MONTH_HEADER_ROW);
            assertTrue(index.hasMonthHeader(), "월 머리글을 못 읽었다");

            for (int htmlRow = 1; htmlRow <= ROW_LABELS.length; htmlRow += 1) {
                int declared = htmlRow + 2;
                EnergySheetIndex.Resolved resolved =
                        index.resolveRow(declared, ROW_LABELS[htmlRow - 1], ROW_LABELS[htmlRow - 1]);
                if (!resolved.byLabel()) {
                    problems.add("r" + htmlRow + " '" + ROW_LABELS[htmlRow - 1] + "': 이름을 못 찾음");
                } else if (resolved.row() != declared) {
                    problems.add("r" + htmlRow + " '" + ROW_LABELS[htmlRow - 1] + "': "
                            + declared + "행 ≠ " + resolved.row() + "행");
                }
            }

            // 월 열: 화면 1 = 전년 12월(C열=3), 2~13 = 1~12월(D~O열=4~15)
            assertEquals(3, index.monthColumn(0, -1), "전년 12월은 C열이어야 한다");
            for (int month = 1; month <= 12; month += 1) {
                assertEquals(month + 3, index.monthColumn(month, -1), month + "월 열이 다르다");
            }
        }
        assertEquals(List.of(), problems, "원본 시트 해석 결과가 기존 좌표와 달랐다");
    }

    @Test
    @DisplayName("행이 끼어들어도 항목 이름을 따라간다 (줄 밀림 방지)")
    void followsLabelWhenRowInserted() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("25년 회계비용 계획");
            write(sheet, 2, 2, "구분");
            write(sheet, 2, 3, "20. 12월");
            for (int month = 1; month <= 12; month += 1) write(sheet, 2, month + 3, month + "월");
            write(sheet, 3, 1, "연료비");
            write(sheet, 3, 2, "복합보일러 스팀구매");
            write(sheet, 4, 2, "제스코 스팀구매");
            write(sheet, 5, 2, "LNG 구매");

            EnergySheetIndex before = EnergySheetIndex.build(sheet, LABEL_COLS, FIRST_DATA_ROW, MONTH_HEADER_ROW);
            assertEquals(5, before.resolveRow(5, "LNG 구매", "LNG 구매").row());

            // 복합보일러 위에 행이 하나 추가되면 LNG 구매는 6행으로 내려간다
            sheet.shiftRows(2, sheet.getLastRowNum(), 1);
            write(sheet, 3, 2, "※ 신규 항목");

            EnergySheetIndex after = EnergySheetIndex.build(sheet, LABEL_COLS, FIRST_DATA_ROW, MONTH_HEADER_ROW);
            EnergySheetIndex.Resolved resolved = after.resolveRow(5, "LNG 구매", "LNG 구매");

            assertEquals(6, resolved.row(), "이름을 따라 6행을 찾아야 한다");
            assertTrue(resolved.byLabel(), "좌표 폴백이 아니라 이름으로 찾아야 한다");
            assertTrue(resolved.moved(), "양식이 바뀌었다고 표시해야 한다");
            assertTrue(after.moved().stream().anyMatch(m -> m.contains("LNG 구매")));
        }
    }

    @Test
    @DisplayName("월 열이 밀려도 머리글을 따라간다")
    void followsMonthHeaderWhenColumnInserted() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("s");
            // 원본보다 한 칸 오른쪽으로 밀린 배치 (앞에 열이 하나 추가된 상황)
            write(sheet, 2, 4, "20. 12월");
            for (int month = 1; month <= 12; month += 1) write(sheet, 2, month + 4, month + "월");
            write(sheet, 3, 2, "복합보일러 스팀구매");

            EnergySheetIndex index = EnergySheetIndex.build(sheet, LABEL_COLS, FIRST_DATA_ROW, MONTH_HEADER_ROW);
            assertEquals(4, index.monthColumn(0, -1), "전년 12월이 한 칸 밀린 D열이어야 한다");
            assertEquals(5, index.monthColumn(1, -1), "1월이 E열이어야 한다");
            assertEquals(16, index.monthColumn(12, -1), "12월이 P열이어야 한다");
        }
    }

    @Test
    @DisplayName("이름을 못 찾으면 좌표로 폴백하고 경고를 남긴다")
    void fallsBackAndWarns() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("s");
            write(sheet, 3, 2, "복합보일러 스팀구매");

            EnergySheetIndex index = EnergySheetIndex.build(sheet, LABEL_COLS, FIRST_DATA_ROW, MONTH_HEADER_ROW);
            EnergySheetIndex.Resolved resolved = index.resolveRow(9, "없는 항목", "없는 항목");

            assertEquals(9, resolved.row(), "기존 좌표를 써야 한다");
            assertTrue(!resolved.byLabel());
            assertTrue(index.notFound().contains("없는 항목"));
        }
    }

    private static void write(Sheet sheet, int rowNumber, int colNumber, String value) {
        Row row = sheet.getRow(rowNumber - 1);
        if (row == null) row = sheet.createRow(rowNumber - 1);
        Cell cell = row.getCell(colNumber - 1);
        if (cell == null) cell = row.createCell(colNumber - 1);
        cell.setCellValue(value);
    }

    /** 저장소의 {@code 원본} 폴더에서 에너지 회계비용 워크북을 찾는다. 없으면 null. */
    private static Path findWorkbook() throws Exception {
        Path dir = Path.of("원본");                                  // 모듈 폴더에서 실행할 때
        if (!Files.isDirectory(dir)) dir = Path.of("..", "module-steam-energy", "원본");
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> found = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("6."))
                    .filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .filter(p -> !p.getFileName().toString().startsWith("~$"))
                    .findFirst();
            return found.orElse(null);
        }
    }
}
