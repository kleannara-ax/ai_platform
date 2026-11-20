-- ============================================================
-- module-steam-energy 이관: 복합보일러 옛 셀 → 규격 표 (묶음 3-A, 2026-11-20)
--
-- 원본: steam_cell_value 의 combo_boiler_production / combo_boiler_downtime / combo_boiler_recovery
-- 대상: steam_combo_daily_production, steam_combo_daily_downtime, steam_combo_monthly_price, steam_combo_recovery_loss
--
-- - 01_schema.sql 을 먼저 실행한다. 이 파일은 여러 번 실행해도 된다:
--   이미 값이 있는 칸은 건드리지 않고 비어 있는 칸만 채운다 (COALESCE).
-- - 빈 문자열, 숫자 칸의 글자, 그 달에 없는 날짜(예: 6월 31일)는 옮기지 않는다.
-- - combo_boiler_etc(연간 계획)는 값이 든 행이 없어 옮길 것이 없다 (빈 문자열 9건).
-- - 원본 셀은 지우지 않는다.
-- ============================================================

-- 복합보일러 스팀생산실적 (일자별)
INSERT INTO steam_combo_daily_production (USAGE_DATE, COMBO_STEAM, COMBO_CONDENSATE, COMBO_MAKEUP, COMBO_DEAERATOR, COMBO_PROCESS, COMBO_DILUTION, EXTERNAL_STEAM, EXTERNAL_CONDENSATE, EXTERNAL_MAKEUP, WASTE_STEAM1, WASTE_STEAM2, KN20, KN50, FLUIDIZED_STEAM, FLUIDIZED_CONDENSATE, RUNTIME20, RUNTIME50, RUNTIME_FLUIDIZED)
SELECT STR_TO_DATE(CONCAT(month, '-', LPAD(d, 2, '0')), '%Y-%m-%d'),
       MAX(CASE WHEN f = 'comboSteam' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'comboCondensate' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'comboMakeup' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'comboDeaerator' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'comboProcess' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'comboDilution' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'externalSteam' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'externalCondensate' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'externalMakeup' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'wasteSteam1' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'wasteSteam2' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'kn20' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'kn50' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'fluidizedSteam' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'fluidizedCondensate' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'runtime20' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'runtime50' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'runtimeFluidized' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT month,
           SUBSTRING_INDEX(row_key, '-', 1) AS f,
           CAST(SUBSTRING_INDEX(row_key, '-', -1) AS UNSIGNED) AS d,
           TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'combo_boiler_production'
      AND month REGEXP '^[0-9]{4}-[0-9]{2}$'
      AND row_key REGEXP '^[A-Za-z0-9]+-[0-9]+$'
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
WHERE f IN ('comboSteam', 'comboCondensate', 'comboMakeup', 'comboDeaerator', 'comboProcess', 'comboDilution', 'externalSteam', 'externalCondensate', 'externalMakeup', 'wasteSteam1', 'wasteSteam2', 'kn20', 'kn50', 'fluidizedSteam', 'fluidizedCondensate', 'runtime20', 'runtime50', 'runtimeFluidized')
  AND d BETWEEN 1 AND DAY(LAST_DAY(CONCAT(month, '-01')))
GROUP BY month, d
ON DUPLICATE KEY UPDATE
    COMBO_STEAM = COALESCE(COMBO_STEAM, VALUES(COMBO_STEAM)),
    COMBO_CONDENSATE = COALESCE(COMBO_CONDENSATE, VALUES(COMBO_CONDENSATE)),
    COMBO_MAKEUP = COALESCE(COMBO_MAKEUP, VALUES(COMBO_MAKEUP)),
    COMBO_DEAERATOR = COALESCE(COMBO_DEAERATOR, VALUES(COMBO_DEAERATOR)),
    COMBO_PROCESS = COALESCE(COMBO_PROCESS, VALUES(COMBO_PROCESS)),
    COMBO_DILUTION = COALESCE(COMBO_DILUTION, VALUES(COMBO_DILUTION)),
    EXTERNAL_STEAM = COALESCE(EXTERNAL_STEAM, VALUES(EXTERNAL_STEAM)),
    EXTERNAL_CONDENSATE = COALESCE(EXTERNAL_CONDENSATE, VALUES(EXTERNAL_CONDENSATE)),
    EXTERNAL_MAKEUP = COALESCE(EXTERNAL_MAKEUP, VALUES(EXTERNAL_MAKEUP)),
    WASTE_STEAM1 = COALESCE(WASTE_STEAM1, VALUES(WASTE_STEAM1)),
    WASTE_STEAM2 = COALESCE(WASTE_STEAM2, VALUES(WASTE_STEAM2)),
    KN20 = COALESCE(KN20, VALUES(KN20)),
    KN50 = COALESCE(KN50, VALUES(KN50)),
    FLUIDIZED_STEAM = COALESCE(FLUIDIZED_STEAM, VALUES(FLUIDIZED_STEAM)),
    FLUIDIZED_CONDENSATE = COALESCE(FLUIDIZED_CONDENSATE, VALUES(FLUIDIZED_CONDENSATE)),
    RUNTIME20 = COALESCE(RUNTIME20, VALUES(RUNTIME20)),
    RUNTIME50 = COALESCE(RUNTIME50, VALUES(RUNTIME50)),
    RUNTIME_FLUIDIZED = COALESCE(RUNTIME_FLUIDIZED, VALUES(RUNTIME_FLUIDIZED));

-- 복합보일러 운휴·디스퍼져 (일자별)
INSERT INTO steam_combo_daily_downtime (USAGE_DATE, CHANGE_COMBO, CHANGE_JESCO, DOWN1, DOWN2, DISPERSER_STEAM, RUNTIME_MIN, VENT_PRODUCTION, VENT_INTERNAL, VENT_USAGE)
SELECT STR_TO_DATE(CONCAT(month, '-', LPAD(d, 2, '0')), '%Y-%m-%d'),
       MAX(CASE WHEN f = 'changeCombo' THEN LEFT(v, 500) END),
       MAX(CASE WHEN f = 'changeJesco' THEN LEFT(v, 500) END),
       MAX(CASE WHEN f = 'down1' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'down2' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'disperserSteam' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'runtimeMin' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'ventProduction' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'ventInternal' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'ventUsage' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT month,
           SUBSTRING_INDEX(row_key, '-', 1) AS f,
           CAST(SUBSTRING_INDEX(row_key, '-', -1) AS UNSIGNED) AS d,
           TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'combo_boiler_downtime'
      AND month REGEXP '^[0-9]{4}-[0-9]{2}$'
      AND row_key REGEXP '^[A-Za-z0-9]+-[0-9]+$'
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
WHERE f IN ('changeCombo', 'changeJesco', 'down1', 'down2', 'disperserSteam', 'runtimeMin', 'ventProduction', 'ventInternal', 'ventUsage')
  AND d BETWEEN 1 AND DAY(LAST_DAY(CONCAT(month, '-01')))
GROUP BY month, d
ON DUPLICATE KEY UPDATE
    CHANGE_COMBO = COALESCE(CHANGE_COMBO, VALUES(CHANGE_COMBO)),
    CHANGE_JESCO = COALESCE(CHANGE_JESCO, VALUES(CHANGE_JESCO)),
    DOWN1 = COALESCE(DOWN1, VALUES(DOWN1)),
    DOWN2 = COALESCE(DOWN2, VALUES(DOWN2)),
    DISPERSER_STEAM = COALESCE(DISPERSER_STEAM, VALUES(DISPERSER_STEAM)),
    RUNTIME_MIN = COALESCE(RUNTIME_MIN, VALUES(RUNTIME_MIN)),
    VENT_PRODUCTION = COALESCE(VENT_PRODUCTION, VALUES(VENT_PRODUCTION)),
    VENT_INTERNAL = COALESCE(VENT_INTERNAL, VALUES(VENT_INTERNAL)),
    VENT_USAGE = COALESCE(VENT_USAGE, VALUES(VENT_USAGE));

-- 복합보일러 월 연료단가 (연/월별) — 연료단가 5종(production) + 벤트 손실 단가(downtime)
INSERT INTO steam_combo_monthly_price (YEAR_NO, MONTH_NO, FUEL_PRICE_LNG_BOILER, FUEL_PRICE_FLUIDIZED, FUEL_PRICE_COMBO, FUEL_PRICE_WASTE, FUEL_PRICE_EXTERNAL, VENT_LOSS_UNIT_PRICE)
SELECT CAST(LEFT(month, 4) AS UNSIGNED), CAST(RIGHT(month, 2) AS UNSIGNED),
       MAX(CASE WHEN f = 'fuelPrice-lngBoiler' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'fuelPrice-fluidized' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'fuelPrice-combo' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'fuelPrice-waste' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'fuelPrice-external' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'ventLossUnitPrice' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT month, row_key AS f, TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name IN ('combo_boiler_production', 'combo_boiler_downtime')
      AND month REGEXP '^[0-9]{4}-[0-9]{2}$'
      AND row_key IN ('fuelPrice-lngBoiler', 'fuelPrice-fluidized', 'fuelPrice-combo', 'fuelPrice-waste', 'fuelPrice-external', 'ventLossUnitPrice')
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
GROUP BY month
ON DUPLICATE KEY UPDATE
    FUEL_PRICE_LNG_BOILER = COALESCE(FUEL_PRICE_LNG_BOILER, VALUES(FUEL_PRICE_LNG_BOILER)),
    FUEL_PRICE_FLUIDIZED = COALESCE(FUEL_PRICE_FLUIDIZED, VALUES(FUEL_PRICE_FLUIDIZED)),
    FUEL_PRICE_COMBO = COALESCE(FUEL_PRICE_COMBO, VALUES(FUEL_PRICE_COMBO)),
    FUEL_PRICE_WASTE = COALESCE(FUEL_PRICE_WASTE, VALUES(FUEL_PRICE_WASTE)),
    FUEL_PRICE_EXTERNAL = COALESCE(FUEL_PRICE_EXTERNAL, VALUES(FUEL_PRICE_EXTERNAL)),
    VENT_LOSS_UNIT_PRICE = COALESCE(VENT_LOSS_UNIT_PRICE, VALUES(VENT_LOSS_UNIT_PRICE));

-- 복합보일러 스팀 회수 감소 (연/월 · 줄 번호별)
INSERT INTO steam_combo_recovery_loss (YEAR_NO, MONTH_NO, LINE_NO, LOSS_DATE, LOSS_TIME, LOSS_NOTE, LOSS_STEAM_RATE, LOSS_BASE_RATE, LOSS_MINUTES)
SELECT CAST(LEFT(month, 4) AS UNSIGNED), CAST(RIGHT(month, 2) AS UNSIGNED), d,
       MAX(CASE WHEN f = 'lossDate' THEN LEFT(v, 50) END),
       MAX(CASE WHEN f = 'lossTime' THEN LEFT(v, 100) END),
       MAX(CASE WHEN f = 'lossNote' THEN LEFT(v, 500) END),
       MAX(CASE WHEN f = 'lossSteamRate' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'lossBaseRate' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN f = 'lossMinutes' AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT month,
           SUBSTRING_INDEX(row_key, '-', 1) AS f,
           CAST(SUBSTRING_INDEX(row_key, '-', -1) AS UNSIGNED) AS d,
           TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'combo_boiler_recovery'
      AND month REGEXP '^[0-9]{4}-[0-9]{2}$'
      AND row_key REGEXP '^[A-Za-z0-9]+-[0-9]+$'
      AND TRIM(COALESCE(cell_value, '')) <> ''
) x
WHERE f IN ('lossDate', 'lossTime', 'lossNote', 'lossSteamRate', 'lossBaseRate', 'lossMinutes') AND d >= 1
GROUP BY month, d
ON DUPLICATE KEY UPDATE
    LOSS_DATE = COALESCE(LOSS_DATE, VALUES(LOSS_DATE)),
    LOSS_TIME = COALESCE(LOSS_TIME, VALUES(LOSS_TIME)),
    LOSS_NOTE = COALESCE(LOSS_NOTE, VALUES(LOSS_NOTE)),
    LOSS_STEAM_RATE = COALESCE(LOSS_STEAM_RATE, VALUES(LOSS_STEAM_RATE)),
    LOSS_BASE_RATE = COALESCE(LOSS_BASE_RATE, VALUES(LOSS_BASE_RATE)),
    LOSS_MINUTES = COALESCE(LOSS_MINUTES, VALUES(LOSS_MINUTES));
