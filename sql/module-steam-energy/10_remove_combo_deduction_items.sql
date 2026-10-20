-- ============================================================
--  module-steam-energy : 단가 입력의 복합보일러 "(차감)" 항목 제거
--
--  (차감) 복합 LNG 사용비용 / (차감) 처분부담금 / (차감) 감가상각 + 토지지상권 지료
--  세 항목을 단가 입력 화면에서 제거한다. 복합보일러의 BSC·ASC 단가와 BSC 보증량은 남는다.
--
--  재실행 안전. DROP/TRUNCATE 없음. DELETE 는 아래 항목 코드에만 건다.
-- ============================================================

SET NAMES utf8mb4;

-- 1) 등록 단가 (연/월 그리드 값)
DELETE FROM table_cell_value
 WHERE table_name = 'unit_price_detail'
   AND row_key IN ('comb_lng_cost', 'comb_disposal', 'comb_depreciation');

-- 2) steam_price 샘플 행 (04_seed_data.sql 에서도 뺐다)
DELETE FROM steam_price
 WHERE ITEM_CODE IN ('comb_lng_cost', 'comb_disposal', 'comb_depreciation');

-- 확인용
-- SELECT FACILITY, ITEM_CODE FROM steam_price WHERE FACILITY = '복합보일러';
-- SELECT row_key FROM table_cell_value WHERE table_name='unit_price_detail' AND row_key LIKE 'comb_%';
