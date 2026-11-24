-- ============================================================
-- module-steam-energy 이관: 유동상 운영 옛 셀 → 규격 표 (묶음 3-D, 2026-11-20)
--
-- 원본: steam_cell_value 의 fluidized_plan_actual / fluidized_plan_summary / fluidized_year_note /
--       fluidized_contract_cost / fluidized_monthly_cache / fluidized_contract_daily / fluidized_srf_inbound
-- 대상: steam_fluidized_month, steam_fluidized_contract_daily,
--       steam_fluidized_srf_inbound_daily, steam_fluidized_srf_rate
--
-- - 01_schema.sql 을 먼저 실행한다. 이 파일은 여러 번 실행해도 된다:
--   이미 값이 있는 칸은 건드리지 않고 비어 있는 칸만 채운다 (COALESCE).
-- - 빈 문자열, 숫자 칸의 글자, 그 달에 없는 날짜는 옮기지 않는다. SRF 업체 칸은 1~10번만 옮긴다.
-- - 월별 특이사항(fluidized_year_note)은 그 해 1월 키의 note-월 → 그 월의 행으로 옮긴다.
-- - 계산값 사본(fluidized_monthly_cache)은 0 이 아닌 값만 옮긴다 (0 은 '계산할 자료 없음' 이었다).
-- - 월별 운영내역 화면의 직접 입력 6개는 서버에 없던 값(브라우저 저장)이라 여기서 옮길 수 없다.
--   화면을 열면 그 브라우저에 남은 값을 DB 가 비어 있는 칸에 한 번 올린다.
-- - 원본 셀은 지우지 않는다.
-- ============================================================

-- 1-1) 유동상 월별 계획·비용 — 사업계획 4칸, 요약 비고, 도급비용 지급현황
INSERT INTO steam_fluidized_month (YEAR_NO, MONTH_NO, PLAN_OPERATING, PLAN_POWER, PLAN_WASTE, PLAN_SRF_INCOME, SUMMARY_REMARK, LABOR_COST, OPERATING_PAY_COST, IMPROVEMENT_PAY_COST)
SELECT CAST(LEFT(month, 4) AS UNSIGNED), CAST(RIGHT(month, 2) AS UNSIGNED),
       MAX(CASE WHEN t = 'fluidized_plan_actual' AND row_key = 'operating' AND col_index = 0 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN t = 'fluidized_plan_actual' AND row_key = 'power' AND col_index = 0 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN t = 'fluidized_plan_actual' AND row_key = 'waste' AND col_index = 0 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN t = 'fluidized_plan_actual' AND row_key = 'srf_income' AND col_index = 0 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN t = 'fluidized_plan_summary' AND row_key = 'monthly-summary' AND col_index = 0 THEN LEFT(v, 1000) END),
       MAX(CASE WHEN t = 'fluidized_contract_cost' AND row_key = 'cost' AND col_index = 0 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN t = 'fluidized_contract_cost' AND row_key = 'cost' AND col_index = 1 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN t = 'fluidized_contract_cost' AND row_key = 'cost' AND col_index = 2 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT month, table_name AS t, row_key, col_index, TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name IN ('fluidized_plan_actual', 'fluidized_plan_summary', 'fluidized_contract_cost')
      AND month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
GROUP BY month
ON DUPLICATE KEY UPDATE
    PLAN_OPERATING = COALESCE(PLAN_OPERATING, VALUES(PLAN_OPERATING)),
    PLAN_POWER = COALESCE(PLAN_POWER, VALUES(PLAN_POWER)),
    PLAN_WASTE = COALESCE(PLAN_WASTE, VALUES(PLAN_WASTE)),
    PLAN_SRF_INCOME = COALESCE(PLAN_SRF_INCOME, VALUES(PLAN_SRF_INCOME)),
    SUMMARY_REMARK = COALESCE(SUMMARY_REMARK, VALUES(SUMMARY_REMARK)),
    LABOR_COST = COALESCE(LABOR_COST, VALUES(LABOR_COST)),
    OPERATING_PAY_COST = COALESCE(OPERATING_PAY_COST, VALUES(OPERATING_PAY_COST)),
    IMPROVEMENT_PAY_COST = COALESCE(IMPROVEMENT_PAY_COST, VALUES(IMPROVEMENT_PAY_COST));

-- 1-2) 월별 특이사항 — 그 해 1월 키의 note-월 → 그 월의 행
INSERT INTO steam_fluidized_month (YEAR_NO, MONTH_NO, MONTH_NOTE)
SELECT CAST(LEFT(month, 4) AS UNSIGNED),
       CAST(SUBSTRING(row_key, 6) AS UNSIGNED),
       LEFT(TRIM(cell_value), 1000)
FROM steam_cell_value
WHERE table_name = 'fluidized_year_note'
  AND month REGEXP '^[0-9]{4}-01$'
  AND row_key REGEXP '^note-(0[1-9]|1[0-2])$'
  AND col_index = 0
  AND TRIM(COALESCE(cell_value, '')) <> ''
ON DUPLICATE KEY UPDATE
    MONTH_NOTE = COALESCE(MONTH_NOTE, VALUES(MONTH_NOTE));

-- 1-3) 화면 계산값 사본 — 도급비·총 운영비(천원). 옛 키: 연 키 YYYY, col_index = 월
INSERT INTO steam_fluidized_month (YEAR_NO, MONTH_NO, CONTRACT_COST, OPERATING_COST)
SELECT CAST(month AS UNSIGNED), col_index,
       MAX(CASE WHEN row_key = 'contractCost' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'operatingCost' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT month, row_key, col_index, TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'fluidized_monthly_cache'
      AND month REGEXP '^[0-9]{4}$'
      AND row_key IN ('contractCost', 'operatingCost')
      AND col_index BETWEEN 1 AND 12
      AND TRIM(COALESCE(cell_value, '')) REGEXP '^-?[0-9]+(\\.[0-9]+)?$'
      AND CAST(TRIM(cell_value) AS DECIMAL(18,6)) <> 0
) x
GROUP BY month, col_index
ON DUPLICATE KEY UPDATE
    CONTRACT_COST = COALESCE(CONTRACT_COST, VALUES(CONTRACT_COST)),
    OPERATING_COST = COALESCE(OPERATING_COST, VALUES(OPERATING_COST));

-- 2) 유동상 소각로 도급비용 (일자별) — 옛 키: 일(두 자리), col_index 0·2·4·5
INSERT INTO steam_fluidized_contract_daily (USAGE_DATE, STEAM_TON, STEAM_UNIT_PRICE, STOP_HOURS, FIXED_COST_PER_HOUR)
SELECT STR_TO_DATE(CONCAT(month, '-', LPAD(d, 2, '0')), '%Y-%m-%d'),
       MAX(CASE WHEN col_index = 0 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 2 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 5 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT month, col_index, TRIM(cell_value) AS v,
           CASE WHEN row_key REGEXP '^[0-9]{1,2}$' THEN CAST(row_key AS UNSIGNED) END AS d
    FROM steam_cell_value
    WHERE table_name = 'fluidized_contract_daily'
      AND month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'
      AND row_key REGEXP '^[0-9]{1,2}$'
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
WHERE d BETWEEN 1 AND DAY(LAST_DAY(CONCAT(month, '-01')))
GROUP BY month, d
ON DUPLICATE KEY UPDATE
    STEAM_TON = COALESCE(STEAM_TON, VALUES(STEAM_TON)),
    STEAM_UNIT_PRICE = COALESCE(STEAM_UNIT_PRICE, VALUES(STEAM_UNIT_PRICE)),
    STOP_HOURS = COALESCE(STOP_HOURS, VALUES(STOP_HOURS)),
    FIXED_COST_PER_HOUR = COALESCE(FIXED_COST_PER_HOUR, VALUES(FIXED_COST_PER_HOUR));

-- 3-1) 유동상 SRF 입고량 (일자별) — 옛 키: entry:일, col_index = 업체 칸 1~10
INSERT INTO steam_fluidized_srf_inbound_daily (USAGE_DATE, VENDOR01_KG, VENDOR02_KG, VENDOR03_KG, VENDOR04_KG, VENDOR05_KG, VENDOR06_KG, VENDOR07_KG, VENDOR08_KG, VENDOR09_KG, VENDOR10_KG)
SELECT STR_TO_DATE(CONCAT(month, '-', LPAD(d, 2, '0')), '%Y-%m-%d'),
       MAX(CASE WHEN col_index = 1 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 2 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 3 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 5 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 6 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 7 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 8 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 9 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 10 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT month, col_index, TRIM(cell_value) AS v,
           CASE WHEN row_key REGEXP '^entry:[0-9]{1,2}$' THEN CAST(SUBSTRING(row_key, 7) AS UNSIGNED) END AS d
    FROM steam_cell_value
    WHERE table_name = 'fluidized_srf_inbound'
      AND month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'
      AND row_key REGEXP '^entry:[0-9]{1,2}$'
      AND col_index BETWEEN 1 AND 10
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
WHERE d BETWEEN 1 AND DAY(LAST_DAY(CONCAT(month, '-01')))
GROUP BY month, d
ON DUPLICATE KEY UPDATE
    VENDOR01_KG = COALESCE(VENDOR01_KG, VALUES(VENDOR01_KG)),
    VENDOR02_KG = COALESCE(VENDOR02_KG, VALUES(VENDOR02_KG)),
    VENDOR03_KG = COALESCE(VENDOR03_KG, VALUES(VENDOR03_KG)),
    VENDOR04_KG = COALESCE(VENDOR04_KG, VALUES(VENDOR04_KG)),
    VENDOR05_KG = COALESCE(VENDOR05_KG, VALUES(VENDOR05_KG)),
    VENDOR06_KG = COALESCE(VENDOR06_KG, VALUES(VENDOR06_KG)),
    VENDOR07_KG = COALESCE(VENDOR07_KG, VALUES(VENDOR07_KG)),
    VENDOR08_KG = COALESCE(VENDOR08_KG, VALUES(VENDOR08_KG)),
    VENDOR09_KG = COALESCE(VENDOR09_KG, VALUES(VENDOR09_KG)),
    VENDOR10_KG = COALESCE(VENDOR10_KG, VALUES(VENDOR10_KG));

-- 3-2) 유동상 SRF 처리단가 (연/월별) — 옛 키: rate, col_index = 업체 칸 1~10
INSERT INTO steam_fluidized_srf_rate (YEAR_NO, MONTH_NO, VENDOR01_RATE, VENDOR02_RATE, VENDOR03_RATE, VENDOR04_RATE, VENDOR05_RATE, VENDOR06_RATE, VENDOR07_RATE, VENDOR08_RATE, VENDOR09_RATE, VENDOR10_RATE)
SELECT CAST(LEFT(month, 4) AS UNSIGNED), CAST(RIGHT(month, 2) AS UNSIGNED),
       MAX(CASE WHEN col_index = 1 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 2 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 3 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 5 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 6 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 7 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 8 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 9 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN col_index = 10 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT month, col_index, TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'fluidized_srf_inbound'
      AND month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'
      AND row_key = 'rate'
      AND col_index BETWEEN 1 AND 10
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
GROUP BY month
ON DUPLICATE KEY UPDATE
    VENDOR01_RATE = COALESCE(VENDOR01_RATE, VALUES(VENDOR01_RATE)),
    VENDOR02_RATE = COALESCE(VENDOR02_RATE, VALUES(VENDOR02_RATE)),
    VENDOR03_RATE = COALESCE(VENDOR03_RATE, VALUES(VENDOR03_RATE)),
    VENDOR04_RATE = COALESCE(VENDOR04_RATE, VALUES(VENDOR04_RATE)),
    VENDOR05_RATE = COALESCE(VENDOR05_RATE, VALUES(VENDOR05_RATE)),
    VENDOR06_RATE = COALESCE(VENDOR06_RATE, VALUES(VENDOR06_RATE)),
    VENDOR07_RATE = COALESCE(VENDOR07_RATE, VALUES(VENDOR07_RATE)),
    VENDOR08_RATE = COALESCE(VENDOR08_RATE, VALUES(VENDOR08_RATE)),
    VENDOR09_RATE = COALESCE(VENDOR09_RATE, VALUES(VENDOR09_RATE)),
    VENDOR10_RATE = COALESCE(VENDOR10_RATE, VALUES(VENDOR10_RATE));
