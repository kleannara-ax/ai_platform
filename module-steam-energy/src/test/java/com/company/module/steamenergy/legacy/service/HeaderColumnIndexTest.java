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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 원단위 일지의 머리글 기반 열 찾기 검증.
 *
 * <p>예전에는 열 번호를 코드에 박아 뒀다. 원본에서 찾은 열이 그 번호와 같은지 확인한다.
 */
class HeaderColumnIndexTest {

    /** 예전 코드에 박혀 있던 열 번호(0-based). */
    private static final Map<String, Integer> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("PM-2.main_steam", 1);
        EXPECTED.put("PM-2.coater_steam", 2);
        EXPECTED.put("PM-2.production", 4);
        EXPECTED.put("PM-2.production_type", 6);
        EXPECTED.put("PM-3.main_steam", 7);
        EXPECTED.put("PM-3.coater_steam", 8);
        EXPECTED.put("PM-3.ventilation_steam", 10);
        EXPECTED.put("PM-3.production", 12);
        EXPECTED.put("PM-3.production_type", 14);
        EXPECTED.put("TM-3.steam", 18);
        EXPECTED.put("TM-3.production", 19);
        EXPECTED.put("TM-4.steam", 21);
        EXPECTED.put("TM-4.production", 22);
        EXPECTED.put("TM-5.steam", 24);
        EXPECTED.put("TM-5.production", 25);
        EXPECTED.put("raw.disperser_total", 9);
        EXPECTED.put("raw.toc_steam", 34);
        EXPECTED.put("raw.pica121_vent", 35);
    }

    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    @Test
    @DisplayName("원본 일지에서 머리글로 찾은 열이 기존 열 번호와 같다")
    void resolvesDeclaredColumns() throws Exception {
        Path workbookPath = findWorkbook();
        Assumptions.assumeTrue(workbookPath != null, "원단위 일지 원본이 없어 건너뜀");

        try (InputStream in = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheet("1월일지");
            Assumptions.assumeTrue(sheet != null, "1월일지 시트가 없어 건너뜀");

            HeaderColumnIndex index =
                    HeaderColumnIndex.build(sheet, 2, 3, 4, 5, 35, 0);

            Map<String, Integer> found = new LinkedHashMap<>();
            for (String equipment : new String[]{"PM-2", "PM-3", "TM-3", "TM-4", "TM-5"}) {
                SteamUnitLogColumnMap.columns(index, equipment)
                        .forEach((field, col) -> found.put(equipment + "." + field, col));
            }
            SteamUnitLogColumnMap.rawColumns(index).forEach((field, col) -> found.put("raw." + field, col));

            assertEquals(EXPECTED, found, "머리글로 찾은 열이 기존 열 번호와 다르다");

            // 일자 행도 기존 계산(5행 = 1일)과 같아야 한다
            assertTrue(index.hasDays(), "일자 칸을 못 읽었다");
            for (int day = 1; day <= 31; day += 1) {
                assertEquals(day + 4, index.rowOfDay(day), day + "일 행이 다르다");
            }
        }
    }

    private static Path findWorkbook() throws Exception {
        Path dir = Path.of("원본");                                  // 모듈 폴더에서 실행할 때
        if (!Files.isDirectory(dir)) dir = Path.of("..", "module-steam-energy", "원본");
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> found = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().contains("원단위 일지"))
                    .filter(p -> !p.getFileName().toString().startsWith("~$"))
                    .findFirst();
            return found.orElse(null);
        }
    }
}
