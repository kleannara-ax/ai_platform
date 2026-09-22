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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 원단위 일지 1·2·4번 구역과 제목 칸의 제목 기반 찾기 검증.
 *
 * <p>예전에 박아 뒀던 번호 — 1번 구역 머리글 3·4·5행 / 일자 6~36행, 2번 구역 43~73행 C·E·G·I·K·N·T·W·Z·AC,
 * 4번 구역 120~150행 B·C·E·F·H·I·K·M·N·P, 제목 칸 A1·A155·A169 — 와 12개 월 시트 모두 같은 자리가 나와야 한다.
 */
class SteamUnitSectionLayoutTest {

    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    @Test
    @DisplayName("2번 호기별 가동시간당 스팀 구역 — 예전 좌표와 같다")
    void resolvesUsageSection() throws Exception {
        forEachMonth((name, sheet) -> {
            SteamUnitSectionLayout layout = SteamUnitSectionLayout.usage(sheet);
            assertEquals(Map.of(1, 2, 3, 4), layout.columns("usage_m_pm2"), name + " PM-2");
            assertEquals(Map.of(1, 6, 3, 8), layout.columns("usage_m_pm3a"), name + " PM-3 스팀량 묶음");
            assertEquals(Map.of(1, 10, 5, 13), layout.columns("usage_m_pm3b"), name + " PM-3 디스퍼져 묶음");
            assertEquals(Map.of(1, 19, 4, 22, 7, 25), layout.columns("usage_m_tm"), name + " TM");
            assertEquals(Map.of(1, 28), layout.columns("usage_m_tissue"), name + " 화장지 총 합계");
            for (int day = 1; day <= 31; day += 1) assertEquals(41 + day, layout.dayRows().get(day), name + " " + day + "일");
            assertTrue(layout.notFound().isEmpty(), name + " 못 찾은 칸: " + layout.notFound());
        });
    }

    @Test
    @DisplayName("4번 유동상·소각로·외부·복합 구역 — 예전 좌표와 같다")
    void resolvesFlowSection() throws Exception {
        forEachMonth((name, sheet) -> {
            SteamUnitSectionLayout layout = SteamUnitSectionLayout.flow(sheet);
            assertEquals(Map.of(0, 1, 1, 2, 3, 4, 4, 5), layout.columns("flow_m_fluid_incinerator"), name + " 유동상·폐합성");
            assertEquals(Map.of(0, 7, 1, 8, 3, 10), layout.columns("flow_m_combined"), name + " 복합보일러");
            assertEquals(Map.of(0, 12, 1, 13, 3, 15), layout.columns("flow_m_boiler"), name + " 외부보일러");
            for (int day = 1; day <= 31; day += 1) assertEquals(118 + day, layout.dayRows().get(day), name + " " + day + "일");
            assertTrue(layout.notFound().isEmpty(), name + " 못 찾은 칸: " + layout.notFound());
        });
    }

    @Test
    @DisplayName("1번 구역 — 머리글 줄을 '일자' 로 찾아도 예전(3·4·5행 고정)과 같은 열·일자가 나온다")
    void resolvesUnitHeader() throws Exception {
        forEachMonth((name, sheet) -> {
            HeaderColumnIndex byTitle = SteamUnitSectionLayout.unitHeaderIndex(sheet);
            assertNotNull(byTitle, name + " '일자' 칸을 못 찾았다");
            HeaderColumnIndex fixed = HeaderColumnIndex.build(sheet, 2, 3, 4, 5, 35, 0);   // 예전 고정 번호
            for (String machine : List.of("PM-2", "PM-3", "TM-3", "TM-4", "TM-5")) {
                assertEquals(SteamUnitLogColumnMap.columns(fixed, machine), SteamUnitLogColumnMap.columns(byTitle, machine), name + " " + machine);
            }
            assertEquals(SteamUnitLogColumnMap.rawColumns(fixed), SteamUnitLogColumnMap.rawColumns(byTitle), name + " 원시 열");
            for (int day = 1; day <= 31; day += 1) assertEquals(4 + day, byTitle.rowOfDay(day), name + " " + day + "일");
        });
    }

    @Test
    @DisplayName("내보내기 제목 칸 — A1 표 제목 / A155 월 누계 / A169 월 을 글자로 찾는다")
    void resolvesLabelRows() throws Exception {
        forEachMonth((name, sheet) -> {
            assertEquals(0, SteamUnitSectionLayout.findLabelRow(sheet, Pattern.compile("^1\\.\\s*호기별 생산량당.*"), false), name + " 표 제목");
            assertEquals(154, SteamUnitSectionLayout.findLabelRow(sheet, Pattern.compile("^\\d{1,2}\\s*월\\s*누계$"), true), name + " 월 누계");
            assertEquals(168, SteamUnitSectionLayout.findLabelRow(sheet, Pattern.compile("^\\d{1,2}\\s*월$"), true), name + " 월");
        });
    }

    private interface SheetCheck {
        void run(String name, Sheet sheet);
    }

    private static void forEachMonth(SheetCheck check) throws Exception {
        Path workbookPath = findWorkbook();
        Assumptions.assumeTrue(workbookPath != null, "원단위 일지 원본이 없어 건너뜀");
        try (InputStream in = Files.newInputStream(workbookPath); Workbook workbook = WorkbookFactory.create(in)) {
            for (int month = 1; month <= 12; month += 1) {
                Sheet sheet = workbook.getSheet(month + "월일지");
                check.run(sheet.getSheetName(), sheet);
            }
        }
    }

    private static Path findWorkbook() throws Exception {
        for (Path dir : List.of(Path.of("원본"), Path.of("..", "원본"))) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> paths = Files.list(dir)) {
                Optional<Path> found = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().startsWith("1."))
                        .filter(p -> p.getFileName().toString().endsWith(".xls"))
                        .filter(p -> !p.getFileName().toString().startsWith("~$"))
                        .findFirst();
                if (found.isPresent()) return found.get();
            }
        }
        return null;
    }
}
