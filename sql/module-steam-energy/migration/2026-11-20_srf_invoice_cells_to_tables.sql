-- ============================================================
-- module-steam-energy 이관: SRF 보일러 인보이스 옛 셀 → 규격 표 (묶음 3-C, 2026-11-20)
--
-- 원본: steam_cell_value 의 srf_boiler_invoice_summary
-- 대상: steam_srf_invoice_summary
--
-- - 01_schema.sql 을 먼저 실행한다. 이 파일은 여러 번 실행해도 된다:
--   이미 값이 있는 칸은 건드리지 않고 비어 있는 칸만 채운다 (COALESCE).
-- - 옮기는 것은 Summary(VK) 의 입력 칸 17개다 (row_key summary-vk-r{엑셀 행}, col_index 4 = D열 · 6 = F열).
-- - Client Invoice · 운영내역 · 월별 운영내역 · Accumulated · BSC · Adjustment Factor 는 옛 저장소에
--   값이 든 행이 없어(빈 문자열뿐) 옮길 것이 없다.
-- - Accumulated 에 옛 형식으로 남은 2건(month 'static', row_key current|0|bsc = 37068, current|0|contract = 38000)은
--   어느 해의 값인지 알 수 없어 옮기지 않는다. 필요하면 화면에서 다시 넣는다.
-- - 원본 셀은 지우지 않는다.
-- ============================================================

INSERT INTO steam_srf_invoice_summary (YEAR_NO, MONTH_NO, BSC_RATE, BSC_FACTOR, AFBSCR_BSC, BSC_STEAM_AMOUNT, ASC_RATE, ASC_FACTOR, ASC_STEAM_AMOUNT, OTHERS_0, OTHERS_1, OTHERS_2, OTHERS_3, PAYABLE_ADJUST, LIQUIDATED_DAMAGE, ASC_REFUND_ANNUAL, UTILITIES_DEDUCTION, OTHER_DEDUCTION_1, OTHER_DEDUCTION_2)
SELECT CAST(LEFT(month, 4) AS UNSIGNED), CAST(RIGHT(month, 2) AS UNSIGNED),
       MAX(CASE WHEN row_key = 'summary-vk-r16' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r18' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r20' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r22' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r27' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r29' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r31' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r34' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r36' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r37' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r38' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r44' AND col_index = 6 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r51' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r53' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r55' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r57' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END),
       MAX(CASE WHEN row_key = 'summary-vk-r59' AND col_index = 4 AND v REGEXP '^-?[0-9]+(\\.[0-9]+)?$' THEN CAST(v AS DECIMAL(18,6)) END)
FROM (
    SELECT month, row_key, col_index, TRIM(cell_value) AS v
    FROM steam_cell_value
    WHERE table_name = 'srf_boiler_invoice_summary'
      AND month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'
      AND row_key IN ('summary-vk-r16', 'summary-vk-r18', 'summary-vk-r20', 'summary-vk-r22', 'summary-vk-r27', 'summary-vk-r29', 'summary-vk-r31', 'summary-vk-r34', 'summary-vk-r36', 'summary-vk-r37', 'summary-vk-r38', 'summary-vk-r44', 'summary-vk-r51', 'summary-vk-r53', 'summary-vk-r55', 'summary-vk-r57', 'summary-vk-r59')
      AND TRIM(COALESCE(cell_value, '')) REGEXP '^-?[0-9]+(\\.[0-9]+)?$'
) x
GROUP BY month
ON DUPLICATE KEY UPDATE
    BSC_RATE = COALESCE(BSC_RATE, VALUES(BSC_RATE)),
    BSC_FACTOR = COALESCE(BSC_FACTOR, VALUES(BSC_FACTOR)),
    AFBSCR_BSC = COALESCE(AFBSCR_BSC, VALUES(AFBSCR_BSC)),
    BSC_STEAM_AMOUNT = COALESCE(BSC_STEAM_AMOUNT, VALUES(BSC_STEAM_AMOUNT)),
    ASC_RATE = COALESCE(ASC_RATE, VALUES(ASC_RATE)),
    ASC_FACTOR = COALESCE(ASC_FACTOR, VALUES(ASC_FACTOR)),
    ASC_STEAM_AMOUNT = COALESCE(ASC_STEAM_AMOUNT, VALUES(ASC_STEAM_AMOUNT)),
    OTHERS_0 = COALESCE(OTHERS_0, VALUES(OTHERS_0)),
    OTHERS_1 = COALESCE(OTHERS_1, VALUES(OTHERS_1)),
    OTHERS_2 = COALESCE(OTHERS_2, VALUES(OTHERS_2)),
    OTHERS_3 = COALESCE(OTHERS_3, VALUES(OTHERS_3)),
    PAYABLE_ADJUST = COALESCE(PAYABLE_ADJUST, VALUES(PAYABLE_ADJUST)),
    LIQUIDATED_DAMAGE = COALESCE(LIQUIDATED_DAMAGE, VALUES(LIQUIDATED_DAMAGE)),
    ASC_REFUND_ANNUAL = COALESCE(ASC_REFUND_ANNUAL, VALUES(ASC_REFUND_ANNUAL)),
    UTILITIES_DEDUCTION = COALESCE(UTILITIES_DEDUCTION, VALUES(UTILITIES_DEDUCTION)),
    OTHER_DEDUCTION_1 = COALESCE(OTHER_DEDUCTION_1, VALUES(OTHER_DEDUCTION_1)),
    OTHER_DEDUCTION_2 = COALESCE(OTHER_DEDUCTION_2, VALUES(OTHER_DEDUCTION_2));
