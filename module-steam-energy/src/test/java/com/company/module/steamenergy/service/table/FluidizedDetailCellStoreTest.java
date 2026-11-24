package com.company.module.steamenergy.service.table;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 유동상 세부 운영내역 셀 저장소 — 셀 주소 ↔ 일별 · 월별 · 추가 행 표.
 */
class FluidizedDetailCellStoreTest {

    private SteamFluidizedDetailDailyAdapter daily;
    private SteamFluidizedDetailMonthAdapter monthly;
    private SteamFluidizedDetailCustomRowAdapter custom;
    private FluidizedDetailCellStore store;

    @BeforeEach
    void setUp() {
        daily = mock(SteamFluidizedDetailDailyAdapter.class);
        monthly = mock(SteamFluidizedDetailMonthAdapter.class);
        custom = mock(SteamFluidizedDetailCustomRowAdapter.class);
        store = new FluidizedDetailCellStore(daily, monthly, custom);
    }

    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) row.put((String) pairs[i], pairs[i + 1]);
        return row;
    }

    private Map<String, String> cells(String monthKey) {
        return store.listByMonthKey(monthKey).stream()
                .collect(Collectors.toMap(r -> (String) r.get("row_key"), r -> (String) r.get("cell_value")));
    }

    @Test
    @DisplayName("열 글자 ↔ 번호: C=3(1일), AG=33(31일), AJ=36, AK=37")
    void columns() {
        assertEquals(3, FluidizedDetailCellStore.columnNumber("C"));
        assertEquals(33, FluidizedDetailCellStore.columnNumber("AG"));
        assertEquals("AJ", FluidizedDetailCellStore.columnLabel(36));
        assertEquals("AK", FluidizedDetailCellStore.columnLabel(37));
    }

    @Test
    @DisplayName("읽기: 일별 값은 일자 열, 단가는 AJ, 비용은 AK, 규사 글자는 C40, 추가 행은 custom| 키")
    void emitCells() {
        when(daily.load(2026, 7)).thenReturn(List.of(
                row("usageDate", LocalDate.of(2026, 7, 2), "runHours", new BigDecimal("24.000000"), "steamUnitPrice", new BigDecimal("18760"))));
        when(monthly.load(2026, 7)).thenReturn(List.of(
                row("yearNo", 2026, "monthNo", 7, "improvementCost", new BigDecimal("45000000"), "powerUnitPrice", new BigDecimal("168.27"),
                        "silicaSandUseNote", "사용량 측정불가")));
        when(custom.load(2026, 7)).thenReturn(List.of(
                row("yearNo", 2026, "monthNo", 7, "section", "kne", "rowKey", "r1", "label", null, "insertAfter", 14, "day03", new BigDecimal("1000"))));

        Map<String, String> cells = cells("2026-07");

        assertEquals("24", cells.get("D4"));
        assertEquals("18760", cells.get("D11"));
        assertEquals("45000000", cells.get("AK15"));
        assertEquals("168.27", cells.get("AJ65"));
        assertEquals("사용량 측정불가", cells.get("C40"));
        assertEquals("", cells.get("custom|kne|r1|label"));
        assertEquals("14", cells.get("custom|kne|r1|insertAfter"));
        assertEquals("1000", cells.get("custom|kne|r1|d3"));
        assertEquals(8, cells.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("바꾸기: 입력 행만 받는다 — 수식 행 · 그 달에 없는 날 · 숫자 칸의 글자는 버리고, C40 글자는 월별 비고로")
    void replaceMonth() {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("C4", "24");
        cells.put("C5", "0");                 // 운휴시간 = 수식 행
        cells.put("AG6", "500.5");            // 2월에는 31일이 없다
        cells.put("D9", "점검");              // 숫자 칸의 글자
        cells.put("C40", "사용량 측정불가");
        cells.put("AJ65", "168.27");
        cells.put("AJ66", "168.27");          // Main 단가를 따르는 수식 줄
        cells.put("AK15", "45,000,000");
        cells.put("AK17", "1");               // 합계 = 수식

        int saved = store.replaceMonth(2026, 2, cells);

        assertEquals(4, saved);
        ArgumentCaptor<List<Map<String, Object>>> days = ArgumentCaptor.forClass(List.class);
        verify(daily).saveBulk(eq(2026), eq(2), days.capture(), eq(true));
        assertEquals(1, days.getValue().size());
        assertEquals(LocalDate.of(2026, 2, 1), days.getValue().get(0).get("usageDate"));
        assertEquals(new BigDecimal("24"), days.getValue().get(0).get("runHours"));
        ArgumentCaptor<List<Map<String, Object>>> months = ArgumentCaptor.forClass(List.class);
        verify(monthly).saveBulk(eq(2026), eq(2), months.capture(), eq(true));
        Map<String, Object> month = months.getValue().get(0);
        assertEquals(new BigDecimal("168.27"), month.get("powerUnitPrice"));
        assertEquals(new BigDecimal("45000000"), month.get("improvementCost"));
        assertEquals("사용량 측정불가", month.get("silicaSandUseNote"));
        verifyNoInteractions(custom);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("덮어쓰기: 주어진 칸만 고치고 그 날의 다른 칸은 그대로, 월별 표는 건드리지 않는다")
    void mergeCells() {
        when(daily.load(2026, 7)).thenReturn(List.of(
                row("usageDate", LocalDate.of(2026, 7, 5), "runHours", new BigDecimal("24"), "steamProducedTon", new BigDecimal("474"))));
        when(monthly.load(2026, 7)).thenReturn(List.of());

        int saved = store.mergeCells(2026, 7, Map.of("G6", "555.5"));

        assertEquals(1, saved);
        ArgumentCaptor<List<Map<String, Object>>> days = ArgumentCaptor.forClass(List.class);
        verify(daily).saveBulk(eq(2026), eq(7), days.capture(), eq(false));
        assertEquals(new BigDecimal("555.5"), days.getValue().get(0).get("steamProducedTon"));
        assertEquals(new BigDecimal("24"), days.getValue().get(0).get("runHours"));
        verify(monthly, never()).saveBulk(anyInt(), any(), anyList(), anyBoolean());
    }

    @Test
    @DisplayName("항목표: 일별 65 · 단가 47 · 비용 2, 운전일지 75 — 필드 이름이 겹치지 않는다")
    void fieldMaps() {
        assertEquals(65, FluidizedDetailFields.DAY_ROWS.size());
        assertEquals(47, FluidizedDetailFields.PRICE_ROWS.size());
        assertEquals(2, FluidizedDetailFields.COST_ROWS.size());
        assertEquals(65, FluidizedDetailFields.DAY_ROWS.values().stream().distinct().count());
        assertEquals(75, FluidizedDailyLogFields.FIELDS.values().stream().distinct().count());
        assertEquals("steamUnitPrice", FluidizedDetailFields.DAY_ROWS.get(11));
        assertEquals("stopHourUnitPrice", FluidizedDetailFields.DAY_ROWS.get(13));
    }
}
