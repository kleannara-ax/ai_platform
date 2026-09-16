-- ============================================================
-- module-steam-energy: 초기 데이터 (설비 마스터 + 단가 샘플)
-- 실행 순서: 01_schema.sql 이후
-- ============================================================

-- ────────────────────────────────────────────────
-- 스팀 설비 마스터
-- ────────────────────────────────────────────────
INSERT INTO steam_equipment (EQUIPMENT_CODE, NAME, CATEGORY, UNIT, SORT_ORDER, IS_ACTIVE, CREATED_AT)
VALUES
    ('PM-2',        'PM-2 제지기',       '제지',   '톤',   10, 1, NOW()),
    ('PM-3',        'PM-3 제지기',       '제지',   '톤',   20, 1, NOW()),
    ('TM-3',        'TM-3 화장지기',     '화장지', '톤',   30, 1, NOW()),
    ('TM-4',        'TM-4 화장지기',     '화장지', '톤',   40, 1, NOW()),
    ('TM-5',        'TM-5 화장지기',     '화장지', '톤',   50, 1, NOW()),
    ('FLUID',       '유동상소각로',       '소각로', '톤',   60, 1, NOW()),
    ('WASTE_INC',   '폐합성소각로',       '소각로', '톤',   70, 1, NOW()),
    ('NEW_INC',     '신설소각로',         '소각로', '톤',   80, 1, NOW()),
    ('COMBINED',    '복합보일러',         '보일러', '톤',   90, 1, NOW()),
    ('EXT_BOILER',  '외부보일러',         '보일러', '톤',  100, 1, NOW()),
    ('LNG_BOILER',  'LNG 보일러',        'LNG',    'N㎥', 110, 1, NOW()),
    ('LNG_BURNER',  'LNG 버너',          'LNG',    'N㎥', 120, 1, NOW())
ON DUPLICATE KEY UPDATE NAME = VALUES(NAME);

-- ────────────────────────────────────────────────
-- 시설별 단가 샘플 (2025년 6월) — 엑셀 "단가 등록(관리자)" 구조 반영
--   INPUT_TYPE: DIRECT(직접입력), AUTO(자동계산)
-- ────────────────────────────────────────────────
INSERT INTO steam_price (YEAR_NO, MONTH_NO, FACILITY, ITEM_CODE, ITEM_NAME, INPUT_TYPE, UNIT, PRICE_VALUE, CREATED_AT)
VALUES
    (2025, 6, 'LNG',        'lng_unit',           'LNG 단가',                    'DIRECT', '원/㎥',  984.640261, NOW()),
    (2025, 6, '복합보일러',  'comb_bsc',           'BSC 단가',                    'DIRECT', '원/톤', 21449.619627, NOW()),
    (2025, 6, '복합보일러',  'comb_asc',           'ASC 단가',                    'DIRECT', '원/톤', 14722.333564, NOW()),
    (2025, 6, '복합보일러',  'comb_lng_cost',      '(차감) 복합 LNG 사용비용',     'AUTO',   '',       NULL,       NOW()),
    (2025, 6, '복합보일러',  'comb_depreciation',  '(차감) 감가상각 + 토지지상권 지료', 'DIRECT', '', 95.132416, NOW()),
    -- 폐합성소각로는 1·2호기 단가가 달라 호기별로 나눠 등록한다.
    (2025, 6, '폐합성소각로 1호기','waste1_steam',      '스팀단가',                     'DIRECT', '원/톤',  NULL,       NOW()),
    (2025, 6, '폐합성소각로 2호기','waste2_steam',      '스팀단가',                     'DIRECT', '원/톤',  NULL,       NOW()),
    (2025, 6, '유동상소각로','fluid_steam',        '스팀단가',                     'DIRECT', '원/톤',  NULL,       NOW()),
    (2025, 6, '신설소각로',  'newinc_steam',       '스팀단가',                     'DIRECT', '원/톤',  NULL,       NOW())
ON DUPLICATE KEY UPDATE ITEM_NAME = VALUES(ITEM_NAME);
