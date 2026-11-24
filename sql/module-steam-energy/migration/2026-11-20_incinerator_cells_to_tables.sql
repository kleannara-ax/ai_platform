-- ============================================================
-- module-steam-energy 이관: 소각로 위탁 옛 셀 → 규격 표 (묶음 3-B, 2026-11-20)
--
-- 원본: steam_cell_value 의 incinerator_contract / incinerator_plan_vs / incinerator_operation / incinerator_total_cost
-- 대상: steam_incinerator_contract_daily, steam_incinerator_plan_monthly,
--       steam_incinerator_operation_monthly, steam_incinerator_cost_monthly
--
-- - 01_schema.sql 을 먼저 실행한다. 이 파일은 여러 번 실행해도 된다:
--   이미 값이 있는 칸은 건드리지 않고 비어 있는 칸만 채운다 (COALESCE).
-- - 옮기는 것은 화면 입력 항목뿐이다. 엑셀 업로드로 들어와 있던 계산값(oper, stop, sub, total|actual,
--   총 운영비용의 자동 계산 칸)은 옮기지 않는다 — 새 화면은 입력값에서 다시 계산한다.
-- - 빈 문자열, 숫자 칸의 글자, 그 달에 없는 날짜(예: 6월 31일)는 옮기지 않는다.
-- - 사업계획대비는 예전에 '조회한 월'마다 12개월 표를 따로 저장했다. (연, 월) 한 행으로 접으며,
--   같은 칸이 여러 조회 월에 있으면 뒤 월의 값을 쓴다. 위탁운영비 이달·누계 칸과 비고는 조회 월의 행에 둔다.
-- - incinerator_yoy 는 저장된 값이 없다. 사고이력은 묶음 3-E 에서 옮긴다.
-- - 원본 셀은 지우지 않는다.
-- ============================================================

-- 1) 소각로 도급내역 (일자별)
INSERT INTO steam_incinerator_contract_daily (USAGE_DATE, U1_STEAM, U1_UNIT, U1_RUNTIME, U1_FIXED, U2_STEAM, U2_UNIT, U2_RUNTIME, U2_FIXED, REAL_STEAM1, REAL_UNIT1, REAL_STEAM2, REAL_UNIT2)
SELECT STR_TO_DATE(CONCAT(month, '-', LPAD(d, 2, '0')), '%Y-%m-%d'),
       MAX(CASE WHEN k = 'unit1|steam' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'unit1|unit' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'unit1|runtime' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'unit1|fixed' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'unit2|steam' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'unit2|unit' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'unit2|runtime' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'unit2|fixed' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'real|steam-1' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'real|unit-1' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'real|steam-2' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'real|unit-2' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT month,
           CONCAT(SUBSTRING_INDEX(row_key, '|', 1), '|', SUBSTRING_INDEX(row_key, '|', -1)) AS k,
           CASE WHEN row_key REGEXP '^[a-z0-9]+\|[0-9]+\|' THEN CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(row_key, '|', 2), '|', -1) AS UNSIGNED) END AS d,
           TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'incinerator_contract'
      AND month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'
      AND row_key REGEXP '^[a-z0-9]+\\|[0-9]+\\|[a-z0-9-]+$'
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
WHERE k IN ('unit1|steam', 'unit1|unit', 'unit1|runtime', 'unit1|fixed', 'unit2|steam', 'unit2|unit', 'unit2|runtime', 'unit2|fixed', 'real|steam-1', 'real|unit-1', 'real|steam-2', 'real|unit-2')
  AND d BETWEEN 1 AND DAY(LAST_DAY(CONCAT(month, '-01')))
GROUP BY month, d
ON DUPLICATE KEY UPDATE
    U1_STEAM = COALESCE(U1_STEAM, VALUES(U1_STEAM)),
    U1_UNIT = COALESCE(U1_UNIT, VALUES(U1_UNIT)),
    U1_RUNTIME = COALESCE(U1_RUNTIME, VALUES(U1_RUNTIME)),
    U1_FIXED = COALESCE(U1_FIXED, VALUES(U1_FIXED)),
    U2_STEAM = COALESCE(U2_STEAM, VALUES(U2_STEAM)),
    U2_UNIT = COALESCE(U2_UNIT, VALUES(U2_UNIT)),
    U2_RUNTIME = COALESCE(U2_RUNTIME, VALUES(U2_RUNTIME)),
    U2_FIXED = COALESCE(U2_FIXED, VALUES(U2_FIXED)),
    REAL_STEAM1 = COALESCE(REAL_STEAM1, VALUES(REAL_STEAM1)),
    REAL_UNIT1 = COALESCE(REAL_UNIT1, VALUES(REAL_UNIT1)),
    REAL_STEAM2 = COALESCE(REAL_STEAM2, VALUES(REAL_STEAM2)),
    REAL_UNIT2 = COALESCE(REAL_UNIT2, VALUES(REAL_UNIT2));

-- 2-1) 소각로 사업계획대비 — 도급내역의 월 인건비 (summary|0|labor)
INSERT INTO steam_incinerator_plan_monthly (YEAR_NO, MONTH_NO, CONTRACT_LABOR)
SELECT CAST(LEFT(month, 4) AS UNSIGNED), CAST(RIGHT(month, 2) AS UNSIGNED), CAST(TRIM(cell_value) AS DECIMAL(18,6))
FROM steam_cell_value
WHERE table_name = 'incinerator_contract' AND row_key = 'summary|0|labor'
  AND month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$' AND TRIM(COALESCE(cell_value, '')) REGEXP '^-?[0-9]+(\\.[0-9]+)?$'
ON DUPLICATE KEY UPDATE
    CONTRACT_LABOR = COALESCE(CONTRACT_LABOR, VALUES(CONTRACT_LABOR));

-- 2-2) 소각로 사업계획대비 — 12개월 표(인건비·운영비·합계)와 월별 비고. 표의 월 = 키 끝의 m월
INSERT INTO steam_incinerator_plan_monthly (YEAR_NO, MONTH_NO, LABOR_PLAN, LABOR_ACTUAL, OPERATION_PLAN, OPERATION_ACTUAL, TOTAL_PLAN, NOTE_U1, NOTE_U2)
SELECT y, n,
       CAST(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN k = 'labor|plan' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1) AS DECIMAL(18,6)),
       CAST(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN k = 'labor|actual' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1) AS DECIMAL(18,6)),
       CAST(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN k = 'operation|plan' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1) AS DECIMAL(18,6)),
       CAST(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN k = 'operation|actual' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1) AS DECIMAL(18,6)),
       CAST(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN k = 'total|plan' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1) AS DECIMAL(18,6)),
       LEFT(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN k = 'notes|u1' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1), 500),
       LEFT(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN k = 'notes|u2' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1), 500)
FROM (
    SELECT CAST(LEFT(month, 4) AS UNSIGNED) AS y, month AS bucket, TRIM(cell_value) AS v,
           CASE WHEN row_key REGEXP '^notes\|m[0-9]+-u[12]$'
                THEN CAST(SUBSTRING(SUBSTRING_INDEX(row_key, '-', 1), 8) AS UNSIGNED)
                WHEN row_key REGEXP '-m[0-9]+$'
                THEN CAST(SUBSTRING_INDEX(row_key, '-m', -1) AS UNSIGNED) END AS n,
           CASE WHEN row_key LIKE 'notes|%'
                THEN CONCAT('notes|', SUBSTRING_INDEX(row_key, '-', -1))
                ELSE SUBSTRING_INDEX(row_key, '-m', 1) END AS k
    FROM steam_cell_value
    WHERE table_name = 'incinerator_plan_vs'
      AND month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'
      AND (row_key REGEXP '^(labor|operation|total)\\|(plan|actual)-m[0-9]+$' OR row_key REGEXP '^notes\\|m[0-9]+-u[12]$')
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
WHERE n BETWEEN 1 AND 12 AND k IN ('labor|plan', 'labor|actual', 'operation|plan', 'operation|actual', 'total|plan', 'notes|u1', 'notes|u2')
GROUP BY y, n
ON DUPLICATE KEY UPDATE
    LABOR_PLAN = COALESCE(LABOR_PLAN, VALUES(LABOR_PLAN)),
    LABOR_ACTUAL = COALESCE(LABOR_ACTUAL, VALUES(LABOR_ACTUAL)),
    OPERATION_PLAN = COALESCE(OPERATION_PLAN, VALUES(OPERATION_PLAN)),
    OPERATION_ACTUAL = COALESCE(OPERATION_ACTUAL, VALUES(OPERATION_ACTUAL)),
    TOTAL_PLAN = COALESCE(TOTAL_PLAN, VALUES(TOTAL_PLAN)),
    NOTE_U1 = COALESCE(NOTE_U1, VALUES(NOTE_U1)),
    NOTE_U2 = COALESCE(NOTE_U2, VALUES(NOTE_U2));

-- 2-3) 소각로 사업계획대비 — 위탁운영비 이달·누계·전월 칸과 비고 (조회 월의 행)
INSERT INTO steam_incinerator_plan_monthly (YEAR_NO, MONTH_NO, BRK_MONTH_PLAN, BRK_MONTH_ACTUAL, BRK_YTD_PLAN, BRK_YTD_ACTUAL, BRK_PREV_ACTUAL, BRK_NOTE)
SELECT CAST(LEFT(bucket, 4) AS UNSIGNED), CAST(RIGHT(bucket, 2) AS UNSIGNED),
       CAST(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN row_key = 'break|m-plan' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1) AS DECIMAL(18,6)),
       CAST(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN row_key = 'break|m-actual' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1) AS DECIMAL(18,6)),
       CAST(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN row_key = 'break|y-plan' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1) AS DECIMAL(18,6)),
       CAST(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN row_key = 'break|y-actual' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1) AS DECIMAL(18,6)),
       CAST(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN row_key = 'break|prev-actual' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1) AS DECIMAL(18,6)),
       LEFT(SUBSTRING_INDEX(GROUP_CONCAT(CASE WHEN row_key = 'break|note' THEN v END ORDER BY bucket DESC SEPARATOR 0x1F), CHAR(31), 1), 500)
FROM (
    SELECT month AS bucket, row_key, TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'incinerator_plan_vs' AND month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'
      AND row_key IN ('break|m-plan', 'break|m-actual', 'break|y-plan', 'break|y-actual', 'break|prev-actual', 'break|note')
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
GROUP BY bucket
ON DUPLICATE KEY UPDATE
    BRK_MONTH_PLAN = COALESCE(BRK_MONTH_PLAN, VALUES(BRK_MONTH_PLAN)),
    BRK_MONTH_ACTUAL = COALESCE(BRK_MONTH_ACTUAL, VALUES(BRK_MONTH_ACTUAL)),
    BRK_YTD_PLAN = COALESCE(BRK_YTD_PLAN, VALUES(BRK_YTD_PLAN)),
    BRK_YTD_ACTUAL = COALESCE(BRK_YTD_ACTUAL, VALUES(BRK_YTD_ACTUAL)),
    BRK_PREV_ACTUAL = COALESCE(BRK_PREV_ACTUAL, VALUES(BRK_PREV_ACTUAL)),
    BRK_NOTE = COALESCE(BRK_NOTE, VALUES(BRK_NOTE));

-- 3) 소각로 계획대비 가동실적 (연/월별) — 키: 구분|월|항목
INSERT INTO steam_incinerator_operation_monthly (YEAR_NO, MONTH_NO, U1_PLAN, U1_ACTUAL, U2_PLAN, U2_ACTUAL, STEAM_PLAN, STEAM_ACTUAL, RATE_PLAN, RATE_ACTUAL, COST_PLAN, COST_ACTUAL)
SELECT y, n,
       MAX(CASE WHEN k = 'hours|u1-plan' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'hours|u1-actual' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'hours|u2-plan' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'hours|u2-actual' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'metric|steam-plan' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'metric|steam-actual' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'metric|rate-plan' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(24,12)) END),
       MAX(CASE WHEN k = 'metric|rate-actual' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(24,12)) END),
       MAX(CASE WHEN k = 'metric|cost-plan' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'metric|cost-actual' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT CASE WHEN month REGEXP '^[0-9]{4}$' THEN CAST(month AS UNSIGNED) END AS y,
           CONCAT(SUBSTRING_INDEX(row_key, '|', 1), '|', SUBSTRING_INDEX(row_key, '|', -1)) AS k,
           CASE WHEN row_key REGEXP '^[a-z-]+\|[0-9]+\|' THEN CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(row_key, '|', 2), '|', -1) AS UNSIGNED) END AS n,
           TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'incinerator_operation'
      AND month REGEXP '^[0-9]{4}$'
      AND row_key REGEXP '^[a-z-]+\\|[0-9]+\\|[a-z0-9-]+$'
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
WHERE n BETWEEN 1 AND 12 AND k IN ('hours|u1-plan', 'hours|u1-actual', 'hours|u2-plan', 'hours|u2-actual', 'metric|steam-plan', 'metric|steam-actual', 'metric|rate-plan', 'metric|rate-actual', 'metric|cost-plan', 'metric|cost-actual')
GROUP BY y, n
ON DUPLICATE KEY UPDATE
    U1_PLAN = COALESCE(U1_PLAN, VALUES(U1_PLAN)),
    U1_ACTUAL = COALESCE(U1_ACTUAL, VALUES(U1_ACTUAL)),
    U2_PLAN = COALESCE(U2_PLAN, VALUES(U2_PLAN)),
    U2_ACTUAL = COALESCE(U2_ACTUAL, VALUES(U2_ACTUAL)),
    STEAM_PLAN = COALESCE(STEAM_PLAN, VALUES(STEAM_PLAN)),
    STEAM_ACTUAL = COALESCE(STEAM_ACTUAL, VALUES(STEAM_ACTUAL)),
    RATE_PLAN = COALESCE(RATE_PLAN, VALUES(RATE_PLAN)),
    RATE_ACTUAL = COALESCE(RATE_ACTUAL, VALUES(RATE_ACTUAL)),
    COST_PLAN = COALESCE(COST_PLAN, VALUES(COST_PLAN)),
    COST_ACTUAL = COALESCE(COST_ACTUAL, VALUES(COST_ACTUAL));

-- 4) 소각로 총 운영비용 (연/월별) — 키: 구분|월|항목
INSERT INTO steam_incinerator_cost_monthly (YEAR_NO, MONTH_NO, OP_BURN_BURN_1, OP_BURN_BURN_2, OP_BURN_RATE_1, OP_BURN_RATE_2, OP_COST_UNIT, OP_COST_DEDUCT, SELF_SRF_IN_QTY, SELF_SRF_IN_REV, SELF_SRF_FLOOR_RECYCLE, SELF_SRF_FLOOR_BURY, SELF_SRF_FLY_SOLID, SELF_SRF_FLY_BURY, SELF_SRF_COST_FLOOR, SELF_SRF_COST_FLY, SELF_SRF_COST_MISC, SELF_UTIL_P1, SELF_UTIL_P2, SELF_UTIL_PRATE, SELF_UTIL_NAOH_QTY, SELF_UTIL_NAOH_UNIT, SELF_UTIL_WATER, SELF_UTIL_WASTE_CHARGE, SELF_UTIL_ETC, SELF_TOTAL_TOTAL, SELF_TOTAL_BURN_RATE, SELF_TOTAL_STEAM_RATE, MAT_OIL_U1, MAT_OIL_U2, MAT_OIL_UNIT, MAT_OIL_CK_QTY, MAT_OIL_CK_UNIT, MAT_CHEM_U_QTY, MAT_CHEM_U_UNIT, MAT_CHEM_S_QTY, MAT_CHEM_S_UNIT, MAT_MISC_A_QTY, MAT_MISC_A_UNIT, MAT_MISC_K_QTY, MAT_MISC_K_UNIT)
SELECT y, n,
       MAX(CASE WHEN k = 'op-burn|burn-1' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'op-burn|burn-2' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'op-burn|rate-1' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'op-burn|rate-2' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'op-cost|unit' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'op-cost|deduct' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-srf|in-qty' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-srf|in-rev' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-srf|floor-recycle' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-srf|floor-bury' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-srf|fly-solid' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-srf|fly-bury' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-srf|cost-floor' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-srf|cost-fly' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-srf|cost-misc' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-util|p1' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-util|p2' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-util|prate' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-util|naoh-qty' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-util|naoh-unit' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-util|water' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-util|waste-charge' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-util|etc' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-total|total' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-total|burn-rate' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'self-total|steam-rate' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-oil|u1' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-oil|u2' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-oil|unit' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-oil|ck-qty' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-oil|ck-unit' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-chem|u-qty' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-chem|u-unit' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-chem|s-qty' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-chem|s-unit' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-misc|a-qty' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-misc|a-unit' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-misc|k-qty' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN k = 'mat-misc|k-unit' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT CASE WHEN month REGEXP '^[0-9]{4}$' THEN CAST(month AS UNSIGNED) END AS y,
           CONCAT(SUBSTRING_INDEX(row_key, '|', 1), '|', SUBSTRING_INDEX(row_key, '|', -1)) AS k,
           CASE WHEN row_key REGEXP '^[a-z-]+\|[0-9]+\|' THEN CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(row_key, '|', 2), '|', -1) AS UNSIGNED) END AS n,
           TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'incinerator_total_cost'
      AND month REGEXP '^[0-9]{4}$'
      AND row_key REGEXP '^[a-z-]+\\|[0-9]+\\|[a-z0-9-]+$'
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
WHERE n BETWEEN 1 AND 12 AND k IN ('op-burn|burn-1', 'op-burn|burn-2', 'op-burn|rate-1', 'op-burn|rate-2', 'op-cost|unit', 'op-cost|deduct', 'self-srf|in-qty', 'self-srf|in-rev', 'self-srf|floor-recycle', 'self-srf|floor-bury', 'self-srf|fly-solid', 'self-srf|fly-bury', 'self-srf|cost-floor', 'self-srf|cost-fly', 'self-srf|cost-misc', 'self-util|p1', 'self-util|p2', 'self-util|prate', 'self-util|naoh-qty', 'self-util|naoh-unit', 'self-util|water', 'self-util|waste-charge', 'self-util|etc', 'self-total|total', 'self-total|burn-rate', 'self-total|steam-rate', 'mat-oil|u1', 'mat-oil|u2', 'mat-oil|unit', 'mat-oil|ck-qty', 'mat-oil|ck-unit', 'mat-chem|u-qty', 'mat-chem|u-unit', 'mat-chem|s-qty', 'mat-chem|s-unit', 'mat-misc|a-qty', 'mat-misc|a-unit', 'mat-misc|k-qty', 'mat-misc|k-unit')
GROUP BY y, n
ON DUPLICATE KEY UPDATE
    OP_BURN_BURN_1 = COALESCE(OP_BURN_BURN_1, VALUES(OP_BURN_BURN_1)),
    OP_BURN_BURN_2 = COALESCE(OP_BURN_BURN_2, VALUES(OP_BURN_BURN_2)),
    OP_BURN_RATE_1 = COALESCE(OP_BURN_RATE_1, VALUES(OP_BURN_RATE_1)),
    OP_BURN_RATE_2 = COALESCE(OP_BURN_RATE_2, VALUES(OP_BURN_RATE_2)),
    OP_COST_UNIT = COALESCE(OP_COST_UNIT, VALUES(OP_COST_UNIT)),
    OP_COST_DEDUCT = COALESCE(OP_COST_DEDUCT, VALUES(OP_COST_DEDUCT)),
    SELF_SRF_IN_QTY = COALESCE(SELF_SRF_IN_QTY, VALUES(SELF_SRF_IN_QTY)),
    SELF_SRF_IN_REV = COALESCE(SELF_SRF_IN_REV, VALUES(SELF_SRF_IN_REV)),
    SELF_SRF_FLOOR_RECYCLE = COALESCE(SELF_SRF_FLOOR_RECYCLE, VALUES(SELF_SRF_FLOOR_RECYCLE)),
    SELF_SRF_FLOOR_BURY = COALESCE(SELF_SRF_FLOOR_BURY, VALUES(SELF_SRF_FLOOR_BURY)),
    SELF_SRF_FLY_SOLID = COALESCE(SELF_SRF_FLY_SOLID, VALUES(SELF_SRF_FLY_SOLID)),
    SELF_SRF_FLY_BURY = COALESCE(SELF_SRF_FLY_BURY, VALUES(SELF_SRF_FLY_BURY)),
    SELF_SRF_COST_FLOOR = COALESCE(SELF_SRF_COST_FLOOR, VALUES(SELF_SRF_COST_FLOOR)),
    SELF_SRF_COST_FLY = COALESCE(SELF_SRF_COST_FLY, VALUES(SELF_SRF_COST_FLY)),
    SELF_SRF_COST_MISC = COALESCE(SELF_SRF_COST_MISC, VALUES(SELF_SRF_COST_MISC)),
    SELF_UTIL_P1 = COALESCE(SELF_UTIL_P1, VALUES(SELF_UTIL_P1)),
    SELF_UTIL_P2 = COALESCE(SELF_UTIL_P2, VALUES(SELF_UTIL_P2)),
    SELF_UTIL_PRATE = COALESCE(SELF_UTIL_PRATE, VALUES(SELF_UTIL_PRATE)),
    SELF_UTIL_NAOH_QTY = COALESCE(SELF_UTIL_NAOH_QTY, VALUES(SELF_UTIL_NAOH_QTY)),
    SELF_UTIL_NAOH_UNIT = COALESCE(SELF_UTIL_NAOH_UNIT, VALUES(SELF_UTIL_NAOH_UNIT)),
    SELF_UTIL_WATER = COALESCE(SELF_UTIL_WATER, VALUES(SELF_UTIL_WATER)),
    SELF_UTIL_WASTE_CHARGE = COALESCE(SELF_UTIL_WASTE_CHARGE, VALUES(SELF_UTIL_WASTE_CHARGE)),
    SELF_UTIL_ETC = COALESCE(SELF_UTIL_ETC, VALUES(SELF_UTIL_ETC)),
    SELF_TOTAL_TOTAL = COALESCE(SELF_TOTAL_TOTAL, VALUES(SELF_TOTAL_TOTAL)),
    SELF_TOTAL_BURN_RATE = COALESCE(SELF_TOTAL_BURN_RATE, VALUES(SELF_TOTAL_BURN_RATE)),
    SELF_TOTAL_STEAM_RATE = COALESCE(SELF_TOTAL_STEAM_RATE, VALUES(SELF_TOTAL_STEAM_RATE)),
    MAT_OIL_U1 = COALESCE(MAT_OIL_U1, VALUES(MAT_OIL_U1)),
    MAT_OIL_U2 = COALESCE(MAT_OIL_U2, VALUES(MAT_OIL_U2)),
    MAT_OIL_UNIT = COALESCE(MAT_OIL_UNIT, VALUES(MAT_OIL_UNIT)),
    MAT_OIL_CK_QTY = COALESCE(MAT_OIL_CK_QTY, VALUES(MAT_OIL_CK_QTY)),
    MAT_OIL_CK_UNIT = COALESCE(MAT_OIL_CK_UNIT, VALUES(MAT_OIL_CK_UNIT)),
    MAT_CHEM_U_QTY = COALESCE(MAT_CHEM_U_QTY, VALUES(MAT_CHEM_U_QTY)),
    MAT_CHEM_U_UNIT = COALESCE(MAT_CHEM_U_UNIT, VALUES(MAT_CHEM_U_UNIT)),
    MAT_CHEM_S_QTY = COALESCE(MAT_CHEM_S_QTY, VALUES(MAT_CHEM_S_QTY)),
    MAT_CHEM_S_UNIT = COALESCE(MAT_CHEM_S_UNIT, VALUES(MAT_CHEM_S_UNIT)),
    MAT_MISC_A_QTY = COALESCE(MAT_MISC_A_QTY, VALUES(MAT_MISC_A_QTY)),
    MAT_MISC_A_UNIT = COALESCE(MAT_MISC_A_UNIT, VALUES(MAT_MISC_A_UNIT)),
    MAT_MISC_K_QTY = COALESCE(MAT_MISC_K_QTY, VALUES(MAT_MISC_K_QTY)),
    MAT_MISC_K_UNIT = COALESCE(MAT_MISC_K_UNIT, VALUES(MAT_MISC_K_UNIT));
