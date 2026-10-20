-- 회계비용 마감비교(energy-month-compare) — 화면에서 뺀 항목의 잔여 데이터 정리
--
-- 배경
--   · 화면에서 "2. 전력비", "3. 에너지 합산 / 손익", "4. 차트" 를 제거했다.
--   · 엑셀 연동도 원본 시트 37행 이후(전력비·에너지 합산)를 매핑에서 뺐다.
--     → EnergyMonthCompareWorkbookService.ROW_MAPS 는 이제 36행까지(연료비)만 왕복한다.
--   · 토지 지상권 지료 / 복합보일러 처분수익 연료비 차감분은 고정값(6.7 / 63.2)으로 바뀌어
--     더 이상 DB 에서 읽지 않는다.
--
--   그래서 아래 row_key 들은 읽는 곳이 없는 잔여 데이터다.
--
-- 남는 것
--   fuel|* 의 나머지 행(연료비 입력값)은 화면이 계속 쓰므로 건드리지 않는다.
--   total|* 는 원래 전부 계산식이라 저장된 적이 없다.

-- 1) 전력비 섹션 데이터 + 비고
DELETE FROM table_cell_value
 WHERE table_name = 'energy_month_compare'
   AND (row_key LIKE 'power|%' OR row_key LIKE 'note|power|%');

-- 2) 에너지 합산 섹션 비고 (데이터는 없고 비고만 남아 있을 수 있다)
DELETE FROM table_cell_value
 WHERE table_name = 'energy_month_compare'
   AND row_key LIKE 'note|total|%';

-- 3) 고정값으로 바뀐 차감비 두 항목
DELETE FROM table_cell_value
 WHERE table_name = 'energy_month_compare'
   AND row_key IN ('fuel|landLease', 'fuel|compositeIncome');

-- 확인용
-- SELECT row_key, COUNT(*) FROM table_cell_value
--  WHERE table_name = 'energy_month_compare' GROUP BY row_key ORDER BY row_key;
