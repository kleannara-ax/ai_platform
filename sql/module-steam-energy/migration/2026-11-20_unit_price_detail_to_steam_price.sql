-- =====================================================================
-- [module-steam-energy] 단가 입력 데이터 이관 — steam_cell_value(unit_price_detail) → steam_price
-- 작성일 : 2026-11-20
-- 용도   : 단가 입력 화면이 규격 표 steam_price 로 바뀌면서, 옛 셀 저장소에 있던 등록 단가(연/월/항목)를
--          그대로 옮긴다. 원단위 일지 묶음 중 단가만 이관하는 이유: 다른 사이트와 공유하는 데이터이고
--          건수가 적어(연 132건) 다시 입력하는 비용이 더 크기 때문.
-- 실행   : 01_schema.sql 적용 뒤, 운영/개발 DB 에 한 번. 다시 실행해도 안전하다(같은 키는 값만 갱신).
--          신규 설치(셀 데이터 없음)에서는 실행하지 않아도 된다.
-- 대상   : table_name = 'unit_price_detail', col_index 1~12 (= 월), month = 연도 4자리
-- 제외   : 항목 코드 목록에 없는 row_key, 빈 cell_value
-- 되돌리기: DELETE FROM steam_price WHERE REMARK = 'migrated:unit_price_detail';
-- =====================================================================

INSERT INTO steam_price
    (YEAR_NO, MONTH_NO, FACILITY, ITEM_CODE, ITEM_NAME, INPUT_TYPE, UNIT, PRICE_VALUE, REMARK, CREATED_AT)
SELECT
    CAST(c.month AS UNSIGNED)                         AS YEAR_NO,
    c.col_index                                       AS MONTH_NO,
    m.facility                                        AS FACILITY,
    c.row_key                                         AS ITEM_CODE,
    m.item_name                                       AS ITEM_NAME,
    'DIRECT'                                          AS INPUT_TYPE,
    m.unit                                            AS UNIT,
    CAST(REPLACE(c.cell_value, ',', '') AS DECIMAL(18,6)) AS PRICE_VALUE,
    'migrated:unit_price_detail'                      AS REMARK,
    COALESCE(c.updated_at, c.created_at, NOW())       AS CREATED_AT
FROM steam_cell_value c
JOIN (
    SELECT 'lng_unit'           AS code, 'LNG'               AS facility, 'LNG 단가'   AS item_name, '원/㎥' AS unit UNION ALL
    SELECT 'comb_bsc',             '복합보일러',         'BSC 단가',    '원/톤' UNION ALL
    SELECT 'comb_bsc_factor',      '복합보일러',         'BSC FACTOR',  NULL    UNION ALL
    SELECT 'comb_asc',             '복합보일러',         'ASC 단가',    '원/톤' UNION ALL
    SELECT 'comb_asc_factor',      '복합보일러',         'ASC FACTOR',  NULL    UNION ALL
    SELECT 'comb_bsc_guarantee',   '복합보일러',         'BSC 보증량',  NULL    UNION ALL
    SELECT 'waste1_fixed_hr',      '폐합성소각로 1호기', '고정단가',    '원/HR' UNION ALL
    SELECT 'waste2_steam',         '폐합성소각로 2호기', '스팀단가',    '원/톤' UNION ALL
    SELECT 'waste2_fixed_hr',      '폐합성소각로 2호기', '고정단가',    '원/HR' UNION ALL
    SELECT 'fluid_steam',          '유동상소각로',       '스팀단가',    '원/톤' UNION ALL
    SELECT 'fluid_fixed_hr',       '유동상소각로',       '고정단가',    '원/HR' UNION ALL
    SELECT 'newinc_steam',         '신설소각로',         '스팀단가',    '원/톤' UNION ALL
    SELECT 'newinc_fixed_cost',    '신설소각로',         '고정비용',    NULL
) m ON m.code = c.row_key
WHERE c.table_name = 'unit_price_detail'
  AND c.col_index BETWEEN 1 AND 12
  AND c.month REGEXP '^[0-9]{4}$'
  AND c.cell_value IS NOT NULL AND TRIM(c.cell_value) <> ''
ON DUPLICATE KEY UPDATE
    PRICE_VALUE = VALUES(PRICE_VALUE),
    FACILITY    = VALUES(FACILITY),
    ITEM_NAME   = VALUES(ITEM_NAME),
    UNIT        = VALUES(UNIT),
    DELETED_YN  = 'N',
    DELETED_AT  = NULL,
    UPDATED_AT  = NOW();

-- 확인
-- SELECT YEAR_NO, ITEM_CODE, COUNT(*) FROM steam_price GROUP BY YEAR_NO, ITEM_CODE ORDER BY 1, 2;
