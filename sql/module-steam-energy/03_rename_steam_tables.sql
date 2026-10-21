-- 스팀 모듈 표 이름을 steam_ 접두로 통일
--
-- 배경
--   · 모듈이 쓰는 물리 표 6개 중 3개(table_cell_value·unit_usage·unit_usage_raw)만 접두어가 없어
--     DB 에서 어느 모듈 표인지 한눈에 안 보였다. steam_equipment 처럼 steam_ 으로 맞춘다.
--   · 코드(TableService 정의, 화면 JS 상수, 서비스)와 다른 SQL 도 같은 커밋에서 새 이름으로 바꿨다.
--     그래서 신규 설치는 02 가 바로 새 이름으로 만들고(이 파일은 아무 것도 안 함), 기존 DB 는 이 파일로 이름만 바꾼다.
--   · 번호가 03 인 이유 — 뒤의 04~15 가 모두 새 이름으로 표를 읽으므로, 기존 DB 에서는 이 파일이
--     그 앞에 와야 한다. (처음엔 15 번이었다가 2026-10-21 에 실행 순서대로 번호를 다시 매겼다)
--
--     table_cell_value  → steam_cell_value
--     unit_usage        → steam_unit_usage
--     unit_usage_raw    → steam_unit_usage_raw
--
--   · 덤으로 두 가지를 정리한다.
--       1) 일부 화면(유동상 개선·사고이력·정비 기록 등)이 table_name 에 물리 표 이름을 그대로 써 왔다.
--          그 행들이 계속 읽히도록 새 표 이름으로 옮긴다. (제대로 된 논리 이름 부여는 별도 작업)
--       2) table_name='app_users' 는 옛 단독 서버 로그인 해시다. 읽는 코드가 없어 지운다.
--
-- 남는 것
--   표 안의 데이터·인덱스·AUTO_INCREMENT 는 RENAME TABLE 로 그대로 유지된다.
--
-- 재실행 안전. 옛 이름이 있고 새 이름이 없을 때만 바꾸고, 그 외엔 아무 것도 하지 않는다.
-- DROP/TRUNCATE 없음. DELETE 는 app_users 네임스페이스 한 곳에만 건다.
-- (서버는 내린 상태에서 실행하고, 새 코드와 함께 올린다 — 옛 코드는 옛 이름을 찾는다)

SET NAMES utf8mb4;

-- 1) 표 이름 변경 (옛 표가 있고 새 표가 없을 때만)
SET @sql = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'table_cell_value')
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'steam_cell_value'),
    'RENAME TABLE table_cell_value TO steam_cell_value', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'unit_usage')
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'steam_unit_usage'),
    'RENAME TABLE unit_usage TO steam_unit_usage', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'unit_usage_raw')
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'steam_unit_usage_raw'),
    'RENAME TABLE unit_usage_raw TO steam_unit_usage_raw', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 인덱스 이름도 새 표 이름에 맞춘다 (옛 이름이 남아 있을 때만)
SET @sql = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
             AND table_name = 'steam_cell_value' AND index_name = 'uk_table_cell_value_month_table_row_col'),
    'ALTER TABLE steam_cell_value RENAME INDEX uk_table_cell_value_month_table_row_col TO uk_steam_cell_value_month_table_row_col', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
             AND table_name = 'steam_cell_value' AND index_name = 'ix_table_cell_value_table_month'),
    'ALTER TABLE steam_cell_value RENAME INDEX ix_table_cell_value_table_month TO ix_steam_cell_value_table_month', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
             AND table_name = 'steam_unit_usage' AND index_name = 'uk_unit_usage_month_day_machine'),
    'ALTER TABLE steam_unit_usage RENAME INDEX uk_unit_usage_month_day_machine TO uk_steam_unit_usage_month_day_machine', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
             AND table_name = 'steam_unit_usage_raw' AND index_name = 'uk_unit_usage_raw_month_day'),
    'ALTER TABLE steam_unit_usage_raw RENAME INDEX uk_unit_usage_raw_month_day TO uk_steam_unit_usage_raw_month_day', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 물리 표 이름을 논리 표 이름으로 써 온 행 — 새 이름으로 옮겨 계속 읽히게 한다
UPDATE steam_cell_value SET table_name = 'steam_cell_value' WHERE table_name = 'table_cell_value';

-- 4) 옛 로그인 해시 정리
DELETE FROM steam_cell_value WHERE table_name = 'app_users';

-- 확인용
-- SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'steam_%';
-- SELECT table_name, COUNT(*) FROM steam_cell_value WHERE table_name IN ('table_cell_value','steam_cell_value','app_users') GROUP BY table_name;
