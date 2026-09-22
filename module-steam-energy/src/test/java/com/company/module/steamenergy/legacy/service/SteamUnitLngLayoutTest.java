package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.legacy.SteamPoiConfig;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 원단위 일지 LNG 구역의 제목 기반 열/행 찾기 검증.
 *
 * <p>새 양식(복합보일러·신설소각로가 끼어든 뒤)과 옛 양식(그 전) 둘 다 읽혀야 한다.
 * 옛 양식에서 나온 자리는 예전에 코드에 박아 뒀던 좌표와 같아야 한다.
 */
class SteamUnitLngLayoutTest {

    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    @Test
    @DisplayName("새 양식 — 12개 월 시트 모두 복합보일러·신설소각로를 포함해 같은 자리가 나온다")
    void resolvesNewLayout() throws Exception {
        Path workbookPath = findWorkbook(originalDir());
        Assumptions.assumeTrue(workbookPath != null, "원단위 일지 원본이 없어 건너뜀");

        try (InputStream in = Files.newInputStream(workbookPath); Workbook workbook = WorkbookFactory.create(in)) {
            for (int month = 1; month <= 12; month += 1) {
                Sheet sheet = workbook.getSheet(month + "월일지");
                SteamUnitLngLayout layout = SteamUnitLngLayout.of(sheet);
                String name = sheet.getSheetName();

                // B~G 보일러(복합보일러 F=5, 신설소각로 G=6), I 단가
                assertEquals(Map.of(0, 1, 1, 2, 2, 3, 3, 4, 7, 5, 8, 6, 5, 8), layout.columns("lng_m_boiler"), name + " 보일러 열");
                // K~N 버너, Q 단가
                assertEquals(Map.of(0, 10, 1, 11, 2, 12, 3, 13, 6, 16), layout.columns("lng_m_burner"), name + " 버너 열");
                // V·W PM-2·PM-3, Y~AA TM-3~5
                assertEquals(Map.of(0, 21, 1, 22, 3, 24, 4, 25, 5, 26), layout.columns("lng_m_mix"), name + " 복합+외부 열");
                assertDayRows(layout, name);
                assertTrue(layout.notFound().isEmpty(), name + " 못 찾은 칸: " + layout.notFound());
            }
        }
    }

    @Test
    @DisplayName("옛 양식 — 예전에 박아 둔 좌표와 같은 자리가 나오고, 없는 복합보일러·신설소각로는 건너뛴다")
    void resolvesOldLayout() throws Exception {
        Path dir = originalDir() == null ? null : originalDir().resolve("백업");
        Path workbookPath = dir == null || !Files.isDirectory(dir) ? null : findWorkbook(dir);
        Assumptions.assumeTrue(workbookPath != null, "옛 양식 백업이 없어 건너뜀");

        try (InputStream in = Files.newInputStream(workbookPath); Workbook workbook = WorkbookFactory.create(in)) {
            for (int month = 2; month <= 12; month += 1) {
                Sheet sheet = workbook.getSheet(month + "월일지");
                SteamUnitLngLayout layout = SteamUnitLngLayout.of(sheet);
                String name = sheet.getSheetName();

                // 예전 좌표: 보일러 1,2,3,4 / 단가 6 · 버너 8~11 / 단가 14 · 복합 19,20,22,23,24
                assertEquals(Map.of(0, 1, 1, 2, 2, 3, 3, 4, 5, 6), layout.columns("lng_m_boiler"), name + " 보일러 열");
                assertEquals(Map.of(0, 8, 1, 9, 2, 10, 3, 11, 6, 14), layout.columns("lng_m_burner"), name + " 버너 열");
                assertEquals(Map.of(0, 19, 1, 20, 3, 22, 4, 23, 5, 24), layout.columns("lng_m_mix"), name + " 복합+외부 열");
                assertDayRows(layout, name);
            }
        }
    }

    /** 81~111행이 1~31일 (0-based 80~110). */
    private static void assertDayRows(SteamUnitLngLayout layout, String name) {
        for (int day = 1; day <= 31; day += 1) {
            assertEquals(79 + day, layout.dayRows().get(day), name + " " + day + "일 행");
        }
    }

    private static Path originalDir() {
        for (Path dir : List.of(Path.of("원본"), Path.of("..", "원본"))) {
            if (Files.isDirectory(dir)) return dir;
        }
        return null;
    }

    private static Path findWorkbook(Path dir) throws Exception {
        if (dir == null) return null;
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> found = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("1."))
                    .filter(p -> p.getFileName().toString().endsWith(".xls"))
                    .filter(p -> !p.getFileName().toString().startsWith("~$"))
                    .findFirst();
            return found.orElse(null);
        }
    }
}
