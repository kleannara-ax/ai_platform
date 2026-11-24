package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.SteamPoiConfig;
import com.company.module.steamenergy.legacy.service.EnergyMonthCompareWorkbookService.RowMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "회계비용 마감과 실마감 비교" 시트의 라벨 기반 행 찾기 검증.
 *
 * <p>업로드 후 값이 한 줄씩 밀리던 시트다. 원본은 117행, 사용자가 3행을 지운 파일은 114행이라
 * 고정 행번호로 읽으면 지운 자리 아래가 전부 밀린다.
 *
 * <p>같은 이름("제지 제품생산량", "제지손익")이 연료비·전력비 두 섹션에 나오므로
 * <b>매핑표 순서대로 직전 행 다음부터</b> 찾는지도 함께 확인한다.
 */
class EnergyMonthCompareIndexTest {

    private static final DataFormatter FORMATTER = new DataFormatter();
    private static final String SHEET = "회계비용 마감과 실마감 비교(2025)";

    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    @Test
    @DisplayName("원본에서는 매핑표 좌표와 같은 행으로 해석된다")
    void resolvesDeclaredRowsOnOriginal() throws Exception {
        Path workbook = findWorkbook("원본", "6.");
        Assumptions.assumeTrue(workbook != null, "에너지 회계비용 원본 엑셀이 없어 건너뜀");

        Map<String, Integer> resolved = resolveAll(workbook);
        List<String> problems = new ArrayList<>();
        for (RowMap map : EnergyMonthCompareWorkbookService.ROW_MAPS) {
            Integer found = resolved.get(key(map));
            if (found == null) {
                problems.add(map.rowKey() + ": 원본에서 이름을 못 찾음 ('" + map.label() + "')");
            } else if (found != map.excelRowOneBased()) {
                problems.add(map.rowKey() + ": " + map.excelRowOneBased() + "행 ≠ " + found + "행");
            }
        }
        assertEquals(List.of(), problems, "원본 해석 결과가 매핑표 좌표와 달랐다");
    }

    @Test
    @DisplayName("행이 삭제된 파일에서도 각 항목이 자기 제목 줄을 찾는다")
    void resolvesByLabelOnEditedWorkbook() throws Exception {
        Path edited = findEditedWorkbook();
        Assumptions.assumeTrue(edited != null, "수정본 엑셀이 없어 건너뜀");

        Map<String, Integer> resolved = resolveAll(edited);
        List<String> problems = new ArrayList<>();
        int shifted = 0;
        int skipped = 0;

        try (InputStream in = Files.newInputStream(edited);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheet(SHEET);
            Assumptions.assumeTrue(sheet != null, "비교 시트가 없어 건너뜀");

            for (RowMap map : EnergyMonthCompareWorkbookService.ROW_MAPS) {
                Integer found = resolved.get(key(map));
                if (found == null) { skipped += 1; continue; }   // 삭제된 항목은 건너뛴 것이 정상
                String actual = labelAt(sheet, found);
                if (!normalize(actual).equals(normalize(map.label()))) {
                    problems.add(map.rowKey() + ": " + found + "행 제목이 '" + actual
                            + "' 인데 '" + map.label() + "' 를 기대했다");
                }
                if (found != map.excelRowOneBased()) shifted += 1;
            }
        }
        assertEquals(List.of(), problems, "수정본에서 항목이 다른 제목 줄에 붙었다");
        assertTrue(shifted > 0, "수정본은 행이 삭제된 파일이므로 옮겨진 항목이 있어야 한다");
        assertEquals(3, skipped, "삭제된 3개 항목(제스코 스팀구매·상계비·페널티)은 건너뛰어야 한다");
    }

    /** 서비스와 같은 순서·같은 방식으로 행을 해석한다. */
    private static Map<String, Integer> resolveAll(Path workbookPath) throws Exception {
        Map<String, Integer> rows = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheet(SHEET);
            Assumptions.assumeTrue(sheet != null, "비교 시트가 없어 건너뜀");

            EnergySheetIndex index = EnergySheetIndex.build(sheet,
                    EnergyMonthCompareWorkbookService.LABEL_COLS,
                    EnergyMonthCompareWorkbookService.FIRST_DATA_ROW,
                    EnergyMonthCompareWorkbookService.MONTH_HEADER_ROW);

            int cursor = EnergyMonthCompareWorkbookService.FIRST_DATA_ROW;
            for (RowMap map : EnergyMonthCompareWorkbookService.ROW_MAPS) {
                Integer found = index.rowOrNull(cursor, map.excelRowOneBased(), map.label(), map.label());
                if (found == null) continue;          // 원본에서 지워진 항목
                rows.put(key(map), found);
                cursor = found + 1;
            }
        }
        return rows;
    }

    private static String key(RowMap map) {
        return map.section() + "|" + map.rowKey();
    }

    /** 구분(B)이 비어 있으면 대분류(A) — 서비스의 라벨 규칙과 같다. */
    private static String labelAt(Sheet sheet, int rowNumber) {
        String b = text(sheet, rowNumber, 2);
        return b.isBlank() ? text(sheet, rowNumber, 1) : b;
    }

    private static String text(Sheet sheet, int rowNumber, int colNumber) {
        Row row = sheet.getRow(rowNumber - 1);
        if (row == null) return "";
        Cell cell = row.getCell(colNumber - 1);
        return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String value = Normalizer.normalize(text, Normalizer.Form.NFKC);
        value = value.replaceAll("\\([^)]*\\)", "");
        value = value.replaceAll("[\\s\\u00A0]+", "");
        value = value.replaceAll("[#·・\\-_\\[\\]]", "");
        return value.toLowerCase(Locale.ROOT);
    }

    private static Path findWorkbook(String dirName, String prefix) throws Exception {
        Path dir = Path.of(dirName);                                 // 모듈 폴더에서 실행할 때
        if (!Files.isDirectory(dir)) dir = Path.of("..", "module-steam-energy", dirName);
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> found = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .filter(p -> !p.getFileName().toString().startsWith("~$"))
                    .findFirst();
            return found.orElse(null);
        }
    }

    /** 사용자가 행을 지워 보내 준 수정본(바탕화면). 없으면 건너뛴다. */
    private static Path findEditedWorkbook() {
        Path path = Path.of(System.getProperty("user.home"), "Desktop",
                "6. 2025년 에너지 회계비용 실적 수정.xlsx");
        return Files.isRegularFile(path) ? path : null;
    }
}
