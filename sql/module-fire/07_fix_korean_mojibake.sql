-- =============================================================================
-- 07_fix_korean_mojibake.sql
-- DB 한글 깨짐 점검/복구 스크립트 (MariaDB)
-- =============================================================================

USE fireweb;

-- 1) 문자셋/콜레이션 상태 확인
SHOW VARIABLES LIKE 'character_set%';
SHOW VARIABLES LIKE 'collation%';

-- 2) 테이블/컬럼 문자셋 확인
SELECT TABLE_NAME, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('building','floor','fire_hydrant','extinguisher');

SELECT TABLE_NAME, COLUMN_NAME, CHARACTER_SET_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('building','floor','fire_hydrant','extinguisher')
  AND DATA_TYPE IN ('char','varchar','text');

-- 3) 대표 깨짐 데이터 확인
SELECT building_id, building_name FROM building ORDER BY building_id;
SELECT floor_id, floor_name FROM floor ORDER BY sort_order, floor_id;

-- 4) 알려진 깨짐 값 보정 (프로젝트 seed 기준)
UPDATE building
SET building_name = '복지관'
WHERE building_name IN ('蹂듭?愿','蹂듭지관');

UPDATE building
SET building_name = '관리동'
WHERE building_name IN ('愿由щ룞','관리동?');

UPDATE floor
SET floor_name = '지하1층'
WHERE floor_name IN ('吏??1痢?','지하1층?');

UPDATE floor
SET floor_name = '1층'
WHERE floor_name IN ('1痢?','1층?');

UPDATE floor
SET floor_name = '2층'
WHERE floor_name IN ('2痢?','2층?');

UPDATE floor
SET floor_name = '3층'
WHERE floor_name IN ('3痢?','3층?');

-- 5) 결과 재확인
SELECT building_id, building_name FROM building ORDER BY building_id;
SELECT floor_id, floor_name FROM floor ORDER BY sort_order, floor_id;

