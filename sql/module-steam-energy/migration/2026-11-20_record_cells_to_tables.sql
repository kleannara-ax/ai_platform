-- ============================================================
-- module-steam-energy 이관: 사고·정비 이력 · 유동상 설비개선 옛 셀 → 규격 표 (묶음 3-E, 2026-11-20)
--
-- 원본: steam_cell_value (table_name = 'steam_cell_value', 월 키 YYYY-00, row_key = 접두어:번호, col_index = 항목)
-- 대상: steam_incident, steam_fluidized_improvement_payment, steam_fluidized_improvement_item
--
-- - 01_schema.sql 을 먼저 실행한다. 이 파일은 여러 번 실행해도 된다:
--   이미 값이 있는 칸은 건드리지 않고 비어 있는 칸만 채운다 (COALESCE).
-- - 한 건(같은 row_key)의 칸들을 한 행으로 모은다. RECORD_KEY 는 옛 row_key 그대로다.
-- - 사고·정비 이력: 일자·사유·조치내역·운휴시간·손실금액이 모두 비어 있는 줄(엑셀에서 들어온 빈 줄, 비고만 "0")은 옮기지 않는다.
-- - 설비개선: 정렬순서 말고는 값이 없는 줄은 옮기지 않는다. 숫자 칸(지급액·사용액·계획비용·실비용)의 글자는 옮기지 않는다.
-- - 원본 셀은 지우지 않는다.
-- ============================================================

-- 1) 사고·정비 이력 (유동상 · SRF 보일러 · 폐합성수지 소각로)
INSERT INTO steam_incident (YEAR_NO, RECORD_KEY, FACILITY, KIND, OCCUR_DATE, REASON, ACTION_TAKEN, DOWNTIME_HOURS, LOSS_AMOUNT, NOTE, SORT_ORDER)
SELECT CAST(LEFT(month, 4) AS UNSIGNED), row_key,
       CASE WHEN row_key LIKE 'srf-accident-record:%' THEN 'SRF'
            WHEN row_key LIKE 'incinerator-%' THEN 'INCINERATOR' ELSE 'FLUIDIZED' END,
       CASE WHEN row_key LIKE '%maintenance-record:%' THEN 'MAINTENANCE' ELSE 'ACCIDENT' END,
       MAX(CASE WHEN col_index = 1 THEN LEFT(v, 100) END),
       MAX(CASE WHEN col_index = 2 THEN LEFT(v, 1000) END),
       MAX(CASE WHEN col_index = 3 THEN LEFT(v, 1000) END),
       MAX(CASE WHEN col_index = 4 THEN LEFT(v, 100) END),
       MAX(CASE WHEN col_index = 5 THEN LEFT(v, 100) END),
       MAX(CASE WHEN col_index = 6 THEN LEFT(v, 500) END),
       MAX(CASE WHEN col_index = 7 AND v REGEXP '^-?[0-9]+$' THEN CAST(v AS SIGNED) END)
FROM (
    SELECT month, row_key, col_index, TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'steam_cell_value'
      AND month REGEXP '^[0-9]{4}-00$'
      AND (row_key LIKE 'accident-record:%' OR row_key LIKE 'maintenance-record:%' OR row_key LIKE 'srf-accident-record:%' OR row_key LIKE 'incinerator-accident-record:%' OR row_key LIKE 'incinerator-maintenance-record:%')
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
GROUP BY month, row_key
HAVING SUM(col_index BETWEEN 1 AND 5) > 0
ON DUPLICATE KEY UPDATE
    OCCUR_DATE = COALESCE(OCCUR_DATE, VALUES(OCCUR_DATE)),
    REASON = COALESCE(REASON, VALUES(REASON)),
    ACTION_TAKEN = COALESCE(ACTION_TAKEN, VALUES(ACTION_TAKEN)),
    DOWNTIME_HOURS = COALESCE(DOWNTIME_HOURS, VALUES(DOWNTIME_HOURS)),
    LOSS_AMOUNT = COALESCE(LOSS_AMOUNT, VALUES(LOSS_AMOUNT)),
    NOTE = COALESCE(NOTE, VALUES(NOTE)),
    SORT_ORDER = COALESCE(SORT_ORDER, VALUES(SORT_ORDER));

-- 사고·정비 이력의 시설·종류는 키의 접두어에서 온다 (위에서 새로 넣은 행은 이미 채워져 있다 — 비어 있는 행만 보정)
UPDATE steam_incident
SET FACILITY = COALESCE(FACILITY, CASE WHEN RECORD_KEY LIKE 'srf-accident-record:%' THEN 'SRF'
                                        WHEN RECORD_KEY LIKE 'incinerator-%' THEN 'INCINERATOR' ELSE 'FLUIDIZED' END),
    KIND = COALESCE(KIND, CASE WHEN RECORD_KEY LIKE '%maintenance-record:%' THEN 'MAINTENANCE' ELSE 'ACCIDENT' END)
WHERE FACILITY IS NULL OR KIND IS NULL;

-- 2) 유동상 설비개선 월별 비용
INSERT INTO steam_fluidized_improvement_payment (YEAR_NO, RECORD_KEY, PAY_MONTH, PAYMENT_AMOUNT, SPENT_AMOUNT, VENDOR_NAME, USAGE_DESC, SORT_ORDER)
SELECT CAST(LEFT(month, 4) AS UNSIGNED), row_key,
       MAX(CASE WHEN col_index = 1 THEN LEFT(v, 10) END),
       MAX(CASE WHEN col_index = 2 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 3 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 4 THEN LEFT(v, 200) END),
       MAX(CASE WHEN col_index = 5 THEN LEFT(v, 500) END),
       MAX(CASE WHEN col_index = 6 AND v REGEXP '^-?[0-9]+$' THEN CAST(v AS SIGNED) END)
FROM (
    SELECT month, row_key, col_index, TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'steam_cell_value'
      AND month REGEXP '^[0-9]{4}-00$'
      AND (row_key LIKE 'improvement-monthly:%')
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
GROUP BY month, row_key
HAVING SUM(col_index BETWEEN 1 AND 5) > 0
ON DUPLICATE KEY UPDATE
    PAY_MONTH = COALESCE(PAY_MONTH, VALUES(PAY_MONTH)),
    PAYMENT_AMOUNT = COALESCE(PAYMENT_AMOUNT, VALUES(PAYMENT_AMOUNT)),
    SPENT_AMOUNT = COALESCE(SPENT_AMOUNT, VALUES(SPENT_AMOUNT)),
    VENDOR_NAME = COALESCE(VENDOR_NAME, VALUES(VENDOR_NAME)),
    USAGE_DESC = COALESCE(USAGE_DESC, VALUES(USAGE_DESC)),
    SORT_ORDER = COALESCE(SORT_ORDER, VALUES(SORT_ORDER));

-- 3) 유동상 설비개선 항목
INSERT INTO steam_fluidized_improvement_item (YEAR_NO, RECORD_KEY, CATEGORY, ITEM_NO, ITEM_NAME, PLAN_COST, ACTUAL_COST, STATUS, REMARK, SORT_ORDER)
SELECT CAST(LEFT(month, 4) AS UNSIGNED), row_key,
       MAX(CASE WHEN col_index = 1 THEN LEFT(v, 50) END),
       MAX(CASE WHEN col_index = 2 THEN LEFT(v, 50) END),
       MAX(CASE WHEN col_index = 3 THEN LEFT(v, 500) END),
       MAX(CASE WHEN col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 5 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 6 THEN LEFT(v, 50) END),
       MAX(CASE WHEN col_index = 7 THEN LEFT(v, 500) END),
       MAX(CASE WHEN col_index = 8 AND v REGEXP '^-?[0-9]+$' THEN CAST(v AS SIGNED) END)
FROM (
    SELECT month, row_key, col_index, TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'steam_cell_value'
      AND month REGEXP '^[0-9]{4}-00$'
      AND (row_key LIKE 'improvement-item:%')
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
GROUP BY month, row_key
HAVING SUM(col_index BETWEEN 1 AND 7) > 0
ON DUPLICATE KEY UPDATE
    CATEGORY = COALESCE(CATEGORY, VALUES(CATEGORY)),
    ITEM_NO = COALESCE(ITEM_NO, VALUES(ITEM_NO)),
    ITEM_NAME = COALESCE(ITEM_NAME, VALUES(ITEM_NAME)),
    PLAN_COST = COALESCE(PLAN_COST, VALUES(PLAN_COST)),
    ACTUAL_COST = COALESCE(ACTUAL_COST, VALUES(ACTUAL_COST)),
    STATUS = COALESCE(STATUS, VALUES(STATUS)),
    REMARK = COALESCE(REMARK, VALUES(REMARK)),
    SORT_ORDER = COALESCE(SORT_ORDER, VALUES(SORT_ORDER));
