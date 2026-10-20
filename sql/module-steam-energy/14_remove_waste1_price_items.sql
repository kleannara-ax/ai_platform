-- ============================================================
--  module-steam-energy : 단가 입력의 "폐합성소각로 1호기 스팀단가" 제거
--
--  1호기는 고정단가(waste1_fixed_hr)만 남기고 스팀단가(waste1_steam)를 단가 입력 화면에서 뺐다.
--  2호기(waste2_*)는 그대로다. 도급내역 1호기 운영단가는 그 페이지에서 직접 넣는다.
--
--  재실행 안전. DROP/TRUNCATE 없음. DELETE 는 아래 항목 코드에만 건다.
-- ============================================================

SET NAMES utf8mb4;

-- 1) 등록 단가 (연/월 그리드 값)
DELETE FROM steam_cell_value
 WHERE table_name = 'unit_price_detail'
   AND row_key = 'waste1_steam';

-- 2) steam_price 샘플 행 (04_seed_data.sql 에서도 뺐다)
DELETE FROM steam_price
 WHERE ITEM_CODE = 'waste1_steam';
