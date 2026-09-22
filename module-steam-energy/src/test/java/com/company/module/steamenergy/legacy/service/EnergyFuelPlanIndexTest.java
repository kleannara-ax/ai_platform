package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.SteamPoiConfig;
import com.company.module.steamenergy.legacy.service.EnergyFuelPlanWorkbookService.TableMap;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * "연료비 사업계획(안)" 시트의 행 라벨 찾기 검증.
 *
 * <p>표 제목 없이 항목이 한 줄로 이어지고, "스팀생산량 → LNG보일러" 같은 이름이 두 번 나온다.
 * 매핑표 순서대로 직전 행 다음부터 찾아 같은 이름이 섞이지 않는지 확인한다.
 */
class EnergyFuelPlanIndexTest {

    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    @Test
    @DisplayName("원본에서 10개 표의 모든 행이 기존 좌표와 같게 해석된다")
    void resolvesDeclaredRowsOnOriginal() throws Exception {
        Path workbookPath = findWorkbook();
        Assumptions.assumeTrue(workbookPath != null, "에너지 회계비용 원본 엑셀이 없어 건너뜀");

        List<String> problems = new ArrayList<>();
        try (InputStream in = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = findSheet(workbook);
            Assumptions.assumeTrue(sheet != null, "사업계획 시트가 없어 건너뜀");

            Map<String, Integer> rows = new EnergyFuelPlanWorkbookService(null).resolveRows(sheet);

            for (TableMap map : EnergyFuelPlanWorkbookService.TABLE_MAPS) {
                for (int htmlRow = 1; htmlRow <= map.htmlRowCount(); htmlRow += 1) {
                    int expected = map.firstExcelRow() + (htmlRow - 1);
                    Integer found = rows.get(map.tableId() + "|" + htmlRow);
                    if (found == null) {
                        problems.add(map.tableId() + " r" + htmlRow + " '"
                                + map.labels().get(htmlRow - 1) + "': 못 찾음");
                    } else if (found != expected) {
                        problems.add(map.tableId() + " r" + htmlRow + " '"
                                + map.labels().get(htmlRow - 1) + "': " + expected + "행 ≠ " + found + "행");
                    }
                }
            }
        }
        assertEquals(List.of(), problems, "원본 해석 결과가 기존 좌표와 달랐다");
    }

    private static Sheet findSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
            Sheet sheet = workbook.getSheetAt(i);
            String name = sheet.getSheetName();
            if (name != null && name.contains("연료비") && name.contains("사업계획")) return sheet;
        }
        return null;
    }

    private static Path findWorkbook() throws Exception {
        Path dir = Path.of("원본");
        if (!Files.isDirectory(dir)) dir = Path.of("..", "원본");
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
