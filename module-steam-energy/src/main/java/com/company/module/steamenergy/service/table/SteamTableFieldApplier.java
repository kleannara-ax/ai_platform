package com.company.module.steamenergy.service.table;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 규격 표의 몇 칸만 일괄로 채우는 도우미 — 단가 입력의 "그 달 모든 날에 단가 기록" 같은 연동에 쓴다.
 * 행 전체 업서트 API 위에서 동작하므로, 기존 행을 읽어 지정한 칸만 바꾼 뒤 다시 저장한다 (다른 칸은 그대로).
 */
public final class SteamTableFieldApplier {

    private SteamTableFieldApplier() {
    }

    /** 일자별 표: 그 달의 모든 날(없는 날은 새 행)에 values 의 칸을 기록한다. 기록한 날 수를 돌려준다. */
    public static int applyToEveryDay(SteamTableAdapter adapter, int year, int month, Map<String, Object> values) {
        Map<LocalDate, Map<String, Object>> existing = new LinkedHashMap<>();
        for (Map<String, Object> row : adapter.load(year, month)) existing.put((LocalDate) row.get("usageDate"), row);
        List<Map<String, Object>> rows = new ArrayList<>();
        int lastDay = YearMonth.of(year, month).lengthOfMonth();
        for (int day = 1; day <= lastDay; day += 1) {
            LocalDate date = LocalDate.of(year, month, day);
            Map<String, Object> row = new LinkedHashMap<>(existing.getOrDefault(date, Map.of()));
            row.put("usageDate", date);
            row.putAll(values);
            rows.add(row);
        }
        adapter.saveBulk(year, month, rows, false);
        return rows.size();
    }

    /** 연/월 표: (연, 월) 행 하나(없으면 새 행)에 values 의 칸을 기록한다. */
    public static void applyToMonth(SteamTableAdapter adapter, int year, int month, Map<String, Object> values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map<String, Object> existing : adapter.load(year, month)) row.putAll(existing);
        row.put("yearNo", year);
        row.put("monthNo", month);
        row.putAll(values);
        adapter.saveBulk(year, month, List.of(row), false);
    }
}
