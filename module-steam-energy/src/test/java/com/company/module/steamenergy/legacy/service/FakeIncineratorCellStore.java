package com.company.module.steamenergy.legacy.service;

import com.company.module.steamenergy.service.table.IncineratorCellStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시험용 소각로 셀 저장소 — 규격 표 대신 시험의 가짜 셀 표(CellTable) 에 옛 형식 그대로 적고 읽는다.
 * 엑셀 읽기·쓰기(칸 찾기)만 시험하려는 것이므로 계산값 키도 버리지 않고 그대로 남긴다.
 */
class FakeIncineratorCellStore extends IncineratorCellStore {

    private final CellTable target;

    FakeIncineratorCellStore(CellTable target) {
        super(null, null, null, null);
        this.target = target;
    }

    /** 사고·정비 이력용 가짜 — 같은 가짜 셀 표(CellTable) 에 옛 형식(월 키 YYYY-00)으로 적고 읽는다. */
    com.company.module.steamenergy.service.table.RecordCellStore records() {
        return new com.company.module.steamenergy.service.table.RecordCellStore(null, null, null) {
            @Override
            public List<Map<String, Object>> listByYear(int year) {
                return target.listAllByMonth("steam_cell_value", year + "-00");
            }

            @Override
            public int replace(int year, List<String> prefixes, List<Cell> cells) {
                for (Cell cell : cells) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("month", year + "-00");
                    payload.put("table_name", "steam_cell_value");
                    payload.put("row_key", cell.rowKey());
                    payload.put("col_index", cell.col());
                    payload.put("cell_value", cell.value());
                    target.upsert("steam_cell_value", payload);
                }
                return cells.size();
            }
        };
    }

    @Override
    public List<Map<String, Object>> listByMonthKey(String monthKey) {
        return target.listAllByMonth("steam_cell_value", monthKey);
    }

    @Override
    public int replaceFromImport(int year, int month, Map<String, Map<String, String>> cells) {
        int saved = 0;
        for (Map.Entry<String, Map<String, String>> table : cells.entrySet()) {
            boolean monthly = CONTRACT.equals(table.getKey()) || PLAN_VS.equals(table.getKey());
            String monthKey = monthly ? String.format("%d-%02d", year, month) : String.valueOf(year);
            for (Map.Entry<String, String> cell : table.getValue().entrySet()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("month", monthKey);
                payload.put("table_name", table.getKey());
                payload.put("row_key", cell.getKey());
                payload.put("col_index", 1);
                payload.put("cell_value", cell.getValue());
                target.upsert("steam_cell_value", payload);
                saved += 1;
            }
        }
        return saved;
    }
}
