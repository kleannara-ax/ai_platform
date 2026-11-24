package com.company.module.steamenergy.legacy.service;

import java.util.List;
import java.util.Map;

/**
 * 시험용 가짜 셀 표 — 엑셀 가져오기가 읽은 칸을 옛 형식(month, table_name, row_key, col_index, cell_value)으로
 * 받아 두는 곳. 시험마다 이 클래스를 이어받아 기록 방식을 정한다.
 */
class CellTable {

    public Map<String, Object> upsert(String tableName, Map<String, Object> payload) {
        return payload;
    }

    public List<Map<String, Object>> listAllByMonth(String tableName, String month) {
        return List.of();
    }
}
