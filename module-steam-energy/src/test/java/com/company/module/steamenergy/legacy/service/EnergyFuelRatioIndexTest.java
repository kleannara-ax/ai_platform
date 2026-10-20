package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.SteamPoiConfig;
import com.company.module.steamenergy.legacy.service.EnergyFuelRatioWorkbookService.TableMap;
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
 * "연료비 배분비율" 시트의 표 제목 + 행 라벨 찾기 검증.
 *
 * <p>이 시트는 작은 표 17개가 한 장에 쌓여 있고 같은 행 이름(제지2, 화장지3 …)이 표마다 반복된다.
 * 예전에는 {@code firstExcelRow + (htmlRow-1)} 로만 읽어 행이 하나 끼면 아래가 전부 밀렸다.
 */
class EnergyFuelRatioIndexTest {

    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    @Test
    @DisplayName("원본에서 17개 표의 모든 행이 기존 좌표와 같게 해석된다")
    void resolvesDeclaredRowsOnOriginal() throws Exception {
        Path workbookPath = findWorkbook();
        Assumptions.assumeTrue(workbookPath != null, "에너지 회계비용 원본 엑셀이 없어 건너뜀");

        List<String> problems = new ArrayList<>();
        try (InputStream in = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = findSheet(workbook);
            Assumptions.assumeTrue(sheet != null, "배분비율 시트가 없어 건너뜀");

            Map<String, Integer> rows = new EnergyFuelRatioWorkbookService(null).resolveRows(sheet);

            for (TableMap map : EnergyFuelRatioWorkbookService.TABLE_MAPS) {
                for (int htmlRow = 1; htmlRow <= map.htmlRowCount(); htmlRow += 1) {
                    int expected = declaredRow(map, htmlRow);
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

    /** 예전 계산식 — t11 만 4번째 행부터 헤더 한 줄을 건너뛰었다. */
    private static int declaredRow(TableMap map, int htmlRow) {
        if ("t11".equals(map.tableId()) && htmlRow >= 4) {
            return map.firstExcelRow() + (htmlRow - 1) + 1;
        }
        return map.firstExcelRow() + (htmlRow - 1);
    }

    private static Sheet findSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
            Sheet sheet = workbook.getSheetAt(i);
            String name = sheet.getSheetName();
            if (name != null && name.matches(".*\\d{4}\\s*연료비\\s*배분비율.*")) return sheet;
        }
        return null;
    }

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
