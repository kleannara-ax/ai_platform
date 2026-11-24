package com.company.module.steamenergy.service.excel;

import com.company.module.steamenergy.dto.*;
import com.company.module.steamenergy.legacy.SteamPoiConfig;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 원단위 일지 엑셀 읽기 → 쓰기 → 다시 읽기가 같은 값을 돌려줘야 한다 (순수 POI, DB 없음).
 * 원본 서식이 없으면 건너뛴다.
 */
class SteamUnitLogWorkbookRoundTripTest {

    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    @Test
    @DisplayName("3월 시트: 읽은 네 표를 서식 복사본에 쓰고 다시 읽으면 같다")
    void roundTripMarch() throws Exception {
        Path workbookPath = findWorkbook();
        Assumptions.assumeTrue(workbookPath != null, "원단위 일지 원본이 없어 건너뜀");
        SteamUnitLogWorkbookReader reader = new SteamUnitLogWorkbookReader();
        SteamUnitLogWorkbookWriter writer = new SteamUnitLogWorkbookWriter();
        int year = 2025, month = 3;

        UnitLogSheetData first;
        byte[] written;
        try (InputStream in = Files.newInputStream(workbookPath); HSSFWorkbook workbook = new HSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheet(month + "월일지");
            first = reader.read(sheet, year, month);
            assertTrue(first.notFound().isEmpty(), "못 찾은 칸: " + first.notFound());
            assertFalse(first.unitRows().isEmpty(), "호기별 실적이 읽혀야 한다");
            assertFalse(first.lngRows().isEmpty(), "LNG 가 읽혀야 한다");
            assertFalse(first.fuelRows().isEmpty(), "스팀 구매량이 읽혀야 한다");

            writer.fill(sheet, year, month,
                    first.unitRows().stream().map(SteamUnitLogWorkbookRoundTripTest::toResponse).toList(),
                    first.commonRows().stream().map(SteamUnitLogWorkbookRoundTripTest::toResponse).toList(),
                    first.lngRows().stream().map(SteamUnitLogWorkbookRoundTripTest::toResponse).toList(),
                    first.fuelRows().stream().map(SteamUnitLogWorkbookRoundTripTest::toResponse).toList());
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                written = out.toByteArray();
            }
        }

        UnitLogSheetData second;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(written))) {
            second = reader.read(workbook.getSheet(month + "월일지"), year, month);
        }

        assertEquals(keys(first.unitRows(), r -> r.getUsageDate() + "|" + r.getMachineCode()),
                keys(second.unitRows(), r -> r.getUsageDate() + "|" + r.getMachineCode()), "호기별 행");
        for (int i = 0; i < first.unitRows().size(); i += 1) {
            SteamUnitDailyUsageSaveRequest a = first.unitRows().get(i), b = second.unitRows().get(i);
            String where = a.getUsageDate() + " " + a.getMachineCode();
            assertSame(a.getMainSteam(), b.getMainSteam(), where + " main");
            assertSame(a.getCoaterSteam(), b.getCoaterSteam(), where + " coater");
            assertSame(a.getVentilationSteam(), b.getVentilationSteam(), where + " vent");
            assertSame(a.getSteam(), b.getSteam(), where + " steam");
            assertSame(a.getProductionKg(), b.getProductionKg(), where + " production");
            assertEquals(a.getProductionType(), b.getProductionType(), where + " type");
            assertSame(a.getRuntimeHours(), b.getRuntimeHours(), where + " runtime");
            assertSame(a.getDisperserRuntimeHours(), b.getDisperserRuntimeHours(), where + " disperser runtime");
            assertSame(a.getVentRuntimeHours(), b.getVentRuntimeHours(), where + " vent runtime");
            assertEquals(a.getNote(), b.getNote(), where + " note");
        }
        assertEquals(keys(first.lngRows(), r -> r.getUsageDate().toString()), keys(second.lngRows(), r -> r.getUsageDate().toString()), "LNG 행");
        for (int i = 0; i < first.lngRows().size(); i += 1) {
            SteamLngDailyUsageSaveRequest a = first.lngRows().get(i), b = second.lngRows().get(i);
            assertSame(a.getBoiler20t1(), b.getBoiler20t1(), a.getUsageDate() + " 20톤-1");
            assertSame(a.getBoilerUnitPrice(), b.getBoilerUnitPrice(), a.getUsageDate() + " 보일러 단가");
            assertSame(a.getBurnerTm5(), b.getBurnerTm5(), a.getUsageDate() + " 버너 TM-5");
            assertSame(a.getMixPm2(), b.getMixPm2(), a.getUsageDate() + " 복합 PM-2");
        }
        assertEquals(keys(first.fuelRows(), r -> r.getUsageDate().toString()), keys(second.fuelRows(), r -> r.getUsageDate().toString()), "구매 행");
        for (int i = 0; i < first.fuelRows().size(); i += 1) {
            SteamFuelDailyUsageSaveRequest a = first.fuelRows().get(i), b = second.fuelRows().get(i);
            assertSame(a.getFluidSteamTon(), b.getFluidSteamTon(), a.getUsageDate() + " 유동상 톤");
            assertSame(a.getIncineratorUnitPrice(), b.getIncineratorUnitPrice(), a.getUsageDate() + " 폐합성 단가");
            assertSame(a.getExtBoilerDeduction(), b.getExtBoilerDeduction(), a.getUsageDate() + " 외부 차감비");
        }
    }

    @Test
    @DisplayName("1월 1일 PM-2 Main 스팀량은 시트 1번 구역의 값과 같다 (자리 확인)")
    void spotCheckFirstDay() throws Exception {
        Path workbookPath = findWorkbook();
        Assumptions.assumeTrue(workbookPath != null, "원단위 일지 원본이 없어 건너뜀");
        try (InputStream in = Files.newInputStream(workbookPath); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheet("1월일지");
            UnitLogSheetData data = new SteamUnitLogWorkbookReader().read(sheet, 2025, 1);
            SteamUnitDailyUsageSaveRequest pm2 = data.unitRows().stream()
                    .filter(r -> r.getUsageDate().equals(LocalDate.of(2025, 1, 1)) && r.getMachineCode().equals("PM-2"))
                    .findFirst().orElseThrow();
            // 1번 구역: 머리글 3·4·5행, 1일 = 6행(0-based 5), PM-2 Main 스팀량 = B열(1)
            double sheetValue = sheet.getRow(5).getCell(1).getNumericCellValue();
            assertNotNull(pm2.getMainSteam());
            assertEquals(0, pm2.getMainSteam().compareTo(BigDecimal.valueOf(sheetValue)));
        }
    }

    private static void assertSame(BigDecimal a, BigDecimal b, String where) {
        if (a == null || b == null) {
            assertEquals(a, b, where);
        } else {
            assertEquals(0, a.compareTo(b), where + ": " + a + " ≠ " + b);
        }
    }

    private static <T> List<String> keys(List<T> rows, Function<T, String> key) {
        return rows.stream().map(key).sorted().toList();
    }

    // SaveRequest → Response (writer 입력) — 테스트에서만 쓰는 단순 변환
    private static SteamUnitDailyUsageResponse toResponse(SteamUnitDailyUsageSaveRequest r) {
        return SteamUnitDailyUsageResponse.builder()
                .usageDate(r.getUsageDate()).machineCode(r.getMachineCode())
                .mainSteam(r.getMainSteam()).coaterSteam(r.getCoaterSteam()).ventilationSteam(r.getVentilationSteam())
                .steam(r.getSteam()).productionKg(r.getProductionKg()).productionType(r.getProductionType())
                .runtimeHours(r.getRuntimeHours()).disperserRuntimeHours(r.getDisperserRuntimeHours())
                .ventRuntimeHours(r.getVentRuntimeHours()).note(r.getNote())
                .createdAt(LocalDateTime.now()).build();
    }

    private static SteamUnitDailyCommonResponse toResponse(SteamUnitDailyCommonSaveRequest r) {
        return SteamUnitDailyCommonResponse.builder()
                .usageDate(r.getUsageDate()).disperserTotal(r.getDisperserTotal()).tocSteam(r.getTocSteam()).pica121Vent(r.getPica121Vent())
                .build();
    }

    private static SteamLngDailyUsageResponse toResponse(SteamLngDailyUsageSaveRequest r) {
        return SteamLngDailyUsageResponse.builder()
                .usageDate(r.getUsageDate())
                .boiler20t1(r.getBoiler20t1()).boiler20t2(r.getBoiler20t2()).boiler50t(r.getBoiler50t()).boiler60t(r.getBoiler60t())
                .boilerCombined(r.getBoilerCombined()).boilerNewIncinerator(r.getBoilerNewIncinerator()).boilerUnitPrice(r.getBoilerUnitPrice())
                .burnerPm3(r.getBurnerPm3()).burnerTm3(r.getBurnerTm3()).burnerTm4(r.getBurnerTm4()).burnerTm5(r.getBurnerTm5())
                .burnerUnitPrice(r.getBurnerUnitPrice())
                .mixPm2(r.getMixPm2()).mixPm3(r.getMixPm3()).mixTm3(r.getMixTm3()).mixTm4(r.getMixTm4()).mixTm5(r.getMixTm5())
                .build();
    }

    private static SteamFuelDailyUsageResponse toResponse(SteamFuelDailyUsageSaveRequest r) {
        return SteamFuelDailyUsageResponse.builder()
                .usageDate(r.getUsageDate())
                .fluidSteamTon(r.getFluidSteamTon()).fluidUnitPrice(r.getFluidUnitPrice())
                .incineratorSteamTon(r.getIncineratorSteamTon()).incineratorUnitPrice(r.getIncineratorUnitPrice())
                .combinedSteamTon(r.getCombinedSteamTon()).combinedUnitPrice(r.getCombinedUnitPrice()).combinedDeduction(r.getCombinedDeduction())
                .extBoilerSteamTon(r.getExtBoilerSteamTon()).extBoilerUnitPrice(r.getExtBoilerUnitPrice()).extBoilerDeduction(r.getExtBoilerDeduction())
                .build();
    }

    private static Path findWorkbook() throws Exception {
        for (Path dir : List.of(Path.of("원본"), Path.of("..", "module-steam-energy", "원본"))) {
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
