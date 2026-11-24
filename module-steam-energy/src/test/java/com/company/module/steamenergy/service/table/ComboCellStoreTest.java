package com.company.module.steamenergy.service.table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 복합보일러 셀 저장소 — 계획 표 칸 번호 ↔ 대상 월 변환과 셀 값 변환.
 */
class ComboCellStoreTest {

    private static YearMonth target(String prefix, int index, int year) {
        return YearMonth.of(year, 1).plusMonths(ComboCellStore.planOffset(ComboCellStore.PLAN_ROWS.get(prefix), index));
    }

    @Test
    @DisplayName("복합보일러 계획: 칸 0 = 전년 6월, 칸 14 = 당해 8월")
    void comboPlanMonths() {
        assertEquals(YearMonth.of(2025, 6), target("combo-plan-1-", 0, 2026));
        assertEquals(YearMonth.of(2026, 8), target("combo-plan-4-", 14, 2026));
    }

    @Test
    @DisplayName("제스코 계획: 칸 0 = 전년 5월, 칸 12 = 당해 5월")
    void jescoPlanMonths() {
        assertEquals(YearMonth.of(2025, 5), target("jesco-plan-0-", 0, 2026));
        assertEquals(YearMonth.of(2026, 5), target("jesco-plan-1-", 12, 2026));
    }

    @Test
    @DisplayName("LNG 사용량: 키의 숫자가 곧 월 (1~12)")
    void lngMonths() {
        assertEquals(YearMonth.of(2026, 1), target("lngBoiler-", 1, 2026));
        assertEquals(YearMonth.of(2026, 12), target("lngCombo-", 12, 2026));
    }

    @Test
    @DisplayName("칸 번호 → 달 수 → 칸 번호 로 되돌아온다")
    void planIndexRoundTrip() {
        ComboCellStore.PLAN_ROWS.values().forEach(row -> {
            for (int index = row.firstIndex(); index < row.firstIndex() + 15; index += 1) {
                assertEquals(index, ComboCellStore.planIndex(row, ComboCellStore.planOffset(row, index)));
            }
        });
    }

    @Test
    @DisplayName("셀 값 변환: 빈 값·숫자 칸의 글자는 null, 쉼표 제거, 숫자는 뒤 0 없이")
    void cellValues() {
        assertNull(CellValues.convert("  ", BigDecimal.class));
        assertNull(CellValues.convert("가동중지", BigDecimal.class));
        assertEquals(new BigDecimal("1234.5"), CellValues.convert("1,234.5", BigDecimal.class));
        assertEquals("3/26", CellValues.convert(" 3/26 ", String.class));
        assertEquals("1142", CellValues.text(new BigDecimal("1142.000000")));
        assertEquals("21.5", CellValues.text(new BigDecimal("21.500000")));
        assertEquals("1000", CellValues.text(new BigDecimal("1E+3")));
        assertNull(CellValues.text(null));
    }
}
