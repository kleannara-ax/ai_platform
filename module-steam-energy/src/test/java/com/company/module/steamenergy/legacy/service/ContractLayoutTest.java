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

/**
 * 소각로 "도급내역" 시트의 머리글 기반 열/행 찾기 검증.
 *
 * <p>예전에는 열 번호(1호기=2, 2호기=9, 실=20)와 행(6행=1일)을 코드에 박아 뒀다.
 * 머리글로 찾은 결과가 그 번호와 같은지 확인한다.
 */
class ContractLayoutTest {

    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    @Test
    @DisplayName("원본 도급내역에서 머리글로 찾은 열이 기존 열 번호와 같다")
    void resolvesDeclaredColumns() throws Exception {
        Path workbookPath = findWorkbook();
        Assumptions.assumeTrue(workbookPath != null, "소각로 위탁운영비 원본이 없어 건너뜀");

        try (InputStream in = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheetAt(0);
            ContractLayout layout = ContractLayout.of(sheet);

            Map<String, Integer> expectedUnit1 = new LinkedHashMap<>();
            expectedUnit1.put("steam", 2);
            expectedUnit1.put("unit", 3);
            expectedUnit1.put("oper", 4);
            expectedUnit1.put("runtime", 5);
            expectedUnit1.put("fixed", 6);
            expectedUnit1.put("stop", 7);
            expectedUnit1.put("sub", 8);
            assertEquals(expectedUnit1, layout.columns("unit1"), "1호기 열이 다르다");

            Map<String, Integer> expectedUnit2 = new LinkedHashMap<>();
            expectedUnit2.put("steam", 9);
            expectedUnit2.put("unit", 10);
            expectedUnit2.put("oper", 11);
            expectedUnit2.put("runtime", 12);
            expectedUnit2.put("fixed", 13);
            expectedUnit2.put("stop", 14);
            expectedUnit2.put("sub", 15);
            assertEquals(expectedUnit2, layout.columns("unit2"), "2호기 열이 다르다");

            Map<String, Integer> expectedReal = new LinkedHashMap<>();
            expectedReal.put("steam-1", 20);
            expectedReal.put("unit-1", 21);
            expectedReal.put("oper-1", 22);
            expectedReal.put("steam-2", 23);
            expectedReal.put("unit-2", 24);
            expectedReal.put("oper-2", 25);
            expectedReal.put("steam-sum", 26);
            expectedReal.put("total-cost", 27);
            assertEquals(expectedReal, layout.columns("real"), "실 스팀량 열이 다르다");

            // 일별 칸: 6행이 1일 (0-based)
            for (String section : new String[]{"unit1", "unit2", "real"}) {
                Map<Integer, Integer> rows = layout.dayRows(section);
                for (int day = 1; day <= 31; day += 1) {
                    assertEquals(day + 5, rows.get(day), section + " " + day + "일 행이 다르다");
                }
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
                    .filter(p -> p.getFileName().toString().contains("위탁운영비"))
                    .filter(p -> !p.getFileName().toString().startsWith("~$"))
                    .findFirst();
            return found.orElse(null);
        }
    }
}
