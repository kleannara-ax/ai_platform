package com.company.module.steamenergy.service.table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 소각로 셀 저장소 — 옛 키 ↔ 표 필드 대응이 표 정의와 어긋나지 않는지 본다.
 */
class IncineratorCellStoreTest {

    @Test
    @DisplayName("총 운영비용: 구분|항목 → 필드 → 구분|항목 으로 되돌아오고, 필드가 표에 있다")
    void costKeysMatchTable() {
        List<String> fields = new SteamIncineratorCostMonthlyAdapter(null).valueFields();
        Set<String> seen = new HashSet<>();
        for (String key : IncineratorCellStore.COST_ITEMS) {
            String field = IncineratorCellStore.costField(key);
            assertTrue(fields.contains(field), key + " → " + field + " 가 표에 없다");
            assertEquals(key, IncineratorCellStore.costKey(field));
            assertTrue(seen.add(field), field + " 가 겹친다");
        }
        assertEquals(fields.size(), seen.size(), "표의 값 컬럼과 입력 항목 수가 같아야 한다");
        assertEquals("opBurnBurn1", IncineratorCellStore.costField("op-burn|burn-1"));
        assertEquals("selfUtilNaohQty", IncineratorCellStore.costField("self-util|naoh-qty"));
    }

    @Test
    @DisplayName("도급내역·사업계획대비·가동실적: 대응 필드가 모두 표에 있다")
    void mappedFieldsExist() {
        List<String> contract = new SteamIncineratorContractDailyAdapter(null).valueFields();
        assertTrue(contract.containsAll(IncineratorCellStore.CONTRACT_KEYS.values()));
        assertEquals(contract.size(), IncineratorCellStore.CONTRACT_KEYS.size());

        List<String> plan = new SteamIncineratorPlanMonthlyAdapter(null).valueFields();
        assertTrue(plan.containsAll(IncineratorCellStore.PLAN_GRID_KEYS.values()));
        assertTrue(plan.containsAll(IncineratorCellStore.PLAN_BREAK_KEYS.values()));
        assertTrue(plan.containsAll(List.of("contractLabor", "noteU1", "noteU2")));

        List<String> operation = new SteamIncineratorOperationMonthlyAdapter(null).valueFields();
        assertTrue(operation.containsAll(IncineratorCellStore.OPERATION_KEYS.values()));
        assertEquals(operation.size(), IncineratorCellStore.OPERATION_KEYS.size());
    }
}
