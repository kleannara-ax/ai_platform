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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "3. 세부 운영내역" 시트의 항목 이름 기반 줄/칸 잇기 검증.
 *
 * <p>이 시트는 DB 키가 {@code "AK16"} 같은 셀 주소라 주소 자체는 남는다. 대신 올라온 파일에서
 * 그 자리를 이름으로 찾는다. 서식 그대로인 파일에서는 <b>주소와 자리가 같아야</b> 하고,
 * 줄이 하나 빠지거나 끼어든 파일에서는 <b>자리가 따라 움직여야</b> 한다.
 */
class FluidizedDetailSheetIndexTest {

    @BeforeAll
    static void relaxPoiLimits() {
        SteamPoiConfig.applyLimits();
    }

    private static Map<String, Object> metadata() {
        return new SheetMetadataLoader().load(FluidizedDetailSheetIndex.METADATA_NAME);
    }

    @Test
    @DisplayName("서식 그대로인 원본에서는 서식 주소와 실제 자리가 같다")
    void mapsCanonicalToSameCell() throws Exception {
        withDetailSheet(sheet -> {
            FluidizedDetailSheetIndex index = FluidizedDetailSheetIndex.of(sheet, metadata());
            assertTrue(index.usable(), "색인을 만들지 못했다");

            // 서식 N행 → 0-based N-1
            for (int canonicalRow : new int[]{4, 6, 11, 16, 24, 41, 44, 65, 96, 97}) {
                assertEquals(canonicalRow - 1, index.rowFor(canonicalRow),
                        canonicalRow + "행이 제자리가 아니다");
            }
            // 날짜·합계 칸
            assertEquals(2, index.columnFor("C"), "1일 칸이 다르다");
            assertEquals(32, index.columnFor("AG"), "31일 칸이 다르다");
            assertEquals(33, index.columnFor("AH"), "합계 칸이 다르다");
            assertEquals(35, index.columnFor("AJ"), "단가 칸이 다르다");
            assertEquals(36, index.columnFor("AK"), "비용 칸이 다르다");
        });
    }

    @Test
    @DisplayName("같은 이름이 두 번 나오는 '활성탄 반입' 을 위아래 순서로 가른다")
    void separatesRepeatedLabels() throws Exception {
        withDetailSheet(sheet -> {
            FluidizedDetailSheetIndex index = FluidizedDetailSheetIndex.of(sheet, metadata());
            assertEquals(23, index.rowFor(24), "첫 번째 활성탄 반입이 다르다");
            assertEquals(40, index.rowFor(41), "두 번째 활성탄 반입이 다르다");
        });
    }

    @Test
    @DisplayName("줄을 하나 지우면 그 아래가 따라 움직이고 지운 줄은 건너뛴다")
    void followsDeletedRow() throws Exception {
        withDetailSheet(sheet -> {
            // "소각량 / 소각량"(서식 10행)을 통째로 지운 상황
            sheet.removeRow(sheet.getRow(9));
            sheet.shiftRows(10, sheet.getLastRowNum(), -1);

            FluidizedDetailSheetIndex index = FluidizedDetailSheetIndex.of(sheet, metadata());
            assertNull(index.rowFor(10), "지워진 줄은 건너뛰어야 한다");
            assertEquals(8, index.rowFor(9), "지운 줄 위가 움직였다");
            assertEquals(9, index.rowFor(11), "지운 줄 아래가 따라오지 않았다");
            assertEquals(14, index.rowFor(16), "추가비용 줄이 따라오지 않았다");
            assertEquals(95, index.rowFor(97), "마지막 줄이 따라오지 않았다");
        });
    }

    @Test
    @DisplayName("줄이 끼어들어도 그 아래가 따라 움직인다")
    void followsInsertedRow() throws Exception {
        withDetailSheet(sheet -> {
            // 내려받기가 추가 항목을 끼워 넣은 파일을 다시 올린 상황
            sheet.shiftRows(10, sheet.getLastRowNum(), 1);

            FluidizedDetailSheetIndex index = FluidizedDetailSheetIndex.of(sheet, metadata());
            assertEquals(9, index.rowFor(10), "끼운 줄 위가 움직였다");
            assertEquals(11, index.rowFor(11), "끼운 줄 아래가 따라오지 않았다");
            assertEquals(16, index.rowFor(16), "추가비용 줄이 따라오지 않았다");
            assertEquals(97, index.rowFor(97), "마지막 줄이 따라오지 않았다");
        });
    }

    @Test
    @DisplayName("'추가비용' 줄이 빠진 파일에서는 그 줄만 없고 나머지는 이어진다")
    void handlesMissingExtraCostRow() throws Exception {
        withDetailSheet(sheet -> {
            sheet.removeRow(sheet.getRow(15));              // 서식 16행 = 추가비용
            sheet.shiftRows(16, sheet.getLastRowNum(), -1);

            FluidizedDetailSheetIndex index = FluidizedDetailSheetIndex.of(sheet, metadata());
            assertNull(index.rowFor(16), "빠진 추가비용 줄을 찾아내면 안 된다");
            assertEquals(14, index.rowFor(15), "설비개선비용 줄이 움직였다");
            assertEquals(15, index.rowFor(17), "운영비 합계 줄이 따라오지 않았다");
        });
    }

    private interface SheetCheck {
        void run(Sheet sheet);
    }

    private static void withDetailSheet(SheetCheck check) throws Exception {
        Path workbookPath = findWorkbook();
        Assumptions.assumeTrue(workbookPath != null, "유동상 운영내역 원본이 없어 건너뜀");
        try (InputStream in = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = FluidizedSummaryLayout.sheetByName(workbook, 2, "3. 세부");
            assertNotNull(sheet, "세부 운영내역 시트를 찾지 못했다");
            check.run(sheet);
        }
    }

    private static Path findWorkbook() throws Exception {
        Path dir = Path.of("원본");
        if (!Files.isDirectory(dir)) dir = Path.of("..", "원본");
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> found = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("2."))
                    .filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .filter(p -> !p.getFileName().toString().startsWith("~$"))
                    .findFirst();
            return found.orElse(null);
        }
    }
}
