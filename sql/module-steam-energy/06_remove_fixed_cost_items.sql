-- ============================================================
--  module-steam-energy : 단가 입력의 "(추가) 고정비용" 항목 제거
--
--  (추가) 고정비용 = 당사 사유 운휴시간 × 고정단가 는 각 업무 페이지
--  (폐합성 도급내역, 유동상 세부 운영내역)에서 계산하는 값이라 단가 입력에 둘 필요가 없다.
--  대상: 폐합성소각로 1호기·2호기, 유동상소각로.
--  복합보일러의 (차감) 항목과 신설소각로 고정비용은 그대로 둔다.
--
--  재실행 안전. DROP/TRUNCATE 없음. DELETE 는 아래 항목 코드에만 건다.
-- ============================================================

SET NAMES utf8mb4;

-- 1) 등록 단가 (자동계산 칸이라 값이 없어야 정상이지만, 다른 환경에 남아 있을 수 있어 정리)
--    waste_fixed_cost 는 09 에서 1호기로 옮기기 전 옛 코드
DELETE FROM table_cell_value
 WHERE table_name = 'unit_price_detail'
   AND row_key IN ('waste1_fixed_cost', 'waste2_fixed_cost', 'fluid_fixed_cost', 'waste_fixed_cost');

-- 2) steam_price 샘플 행 (04_seed_data.sql 에서도 뺐다)
DELETE FROM steam_price
 WHERE ITEM_CODE IN ('waste1_fixed_cost', 'waste2_fixed_cost', 'fluid_fixed_cost', 'waste_fixed_cost');

-- 확인용
-- SELECT FACILITY, ITEM_CODE FROM steam_price WHERE ITEM_CODE LIKE '%fixed_cost';
-- SELECT row_key FROM table_cell_value WHERE table_name='unit_price_detail' AND row_key LIKE '%fixed_cost';
