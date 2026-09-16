-- ============================================================
--  module-steam-energy : 폐합성소각로 단가를 1·2호기로 분리
--
--  1호기와 2호기의 스팀단가·고정단가가 서로 달라 하나로 묶어 쓸 수 없다.
--  단가 입력 페이지의 항목 코드를 아래와 같이 나눴다.
--
--    waste_steam       →  waste1_steam      / waste2_steam       (스팀단가, 원/톤)
--    waste_fixed_hr    →  waste1_fixed_hr   / waste2_fixed_hr    (고정단가, 원/HR)
--    waste_fixed_cost  →  waste1_fixed_cost / waste2_fixed_cost  ((추가) 고정비용, 자동계산)
--
--  기존에 등록된 폐합성 단가가 있으면 1호기 값으로 옮긴다.
--  (2호기는 값이 다르므로 비워 두고 담당자가 새로 입력한다 — 임의 복사 금지)
--
--  재실행 안전. DROP/TRUNCATE 없음. DELETE 는 옮겨 담은 옛 코드에만 건다.
-- ============================================================

SET NAMES utf8mb4;

-- 1) 등록 단가(table_cell_value / unit_price_detail): 옛 코드 → 1호기 코드로 복사
--    이미 1호기 값이 있으면 그대로 둔다(IGNORE).
INSERT IGNORE INTO table_cell_value (month, year_no, month_no, table_name, row_key, col_index, cell_value)
SELECT month, year_no, month_no, table_name,
       CONCAT('waste1_', SUBSTRING(row_key, 7)), col_index, cell_value
  FROM table_cell_value
 WHERE table_name = 'unit_price_detail'
   AND row_key IN ('waste_steam', 'waste_fixed_hr', 'waste_fixed_cost');

-- 2) 옮겨 담은 옛 코드 정리 (화면에서 더 이상 쓰지 않는다)
DELETE FROM table_cell_value
 WHERE table_name = 'unit_price_detail'
   AND row_key IN ('waste_steam', 'waste_fixed_hr', 'waste_fixed_cost');

-- 3) steam_price 샘플 행의 옛 코드 정리 (04_seed_data.sql 이 호기별 행을 새로 넣는다)
DELETE FROM steam_price
 WHERE ITEM_CODE IN ('waste_steam', 'waste_fixed_hr', 'waste_fixed_cost');

-- 확인용
-- SELECT row_key, COUNT(*) FROM table_cell_value
--  WHERE table_name='unit_price_detail' AND row_key LIKE 'waste%' GROUP BY row_key;
-- SELECT FACILITY, ITEM_CODE FROM steam_price WHERE ITEM_CODE LIKE 'waste%' ORDER BY ITEM_CODE;
