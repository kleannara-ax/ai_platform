-- ============================================================
--  [module-steam-energy] 옛 표 정리 — 더 이상 쓰지 않는 표 3개를 지운다
--
--  ※ 번호 파일(01 ~ 06)과 달리 "순서대로 실행" 대상이 아니다. 필요할 때 한 번, 따로 실행한다.
--  ※ 되돌릴 수 없다. 반드시 아래 백업을 먼저 받는다.
--
--  지우는 표
--    steam_cell_value      옛 셀 저장소 (화면 38개의 값이 칸 단위로 들어 있던 표)
--    steam_unit_usage      옛 원단위 일지 (호기별 일별 값)
--    steam_unit_usage_raw  옛 원단위 일지 (일별 공통 값)
--
--  지워도 되는 이유
--    2026-11-23 부터 모든 화면과 엑셀 올리기·내려받기가 01_schema.sql 의 실제 컬럼 표만 쓴다.
--    위 세 표를 읽거나 쓰는 코드는 없다 (/steam/api/tables/** 도 없앴다).
--
--  실행 전에 확인할 것
--    1) 새 버전 서버가 배포돼 있다 (옛 버전 서버는 이 표들이 없으면 스팀 화면이 열리지 않는다).
--    2) 옛 값을 새 표로 옮길 생각이면 migration/ 의 이관 SQL 을 먼저 돌렸다.
--       이관 SQL 은 steam_cell_value 를 읽으므로, 이 파일을 실행한 뒤에는 돌릴 수 없다.
--    3) 이 표를 직접 읽는 다른 시스템이 없다 (api/19_스팀에너지_공유데이터.md 의 "없어진 옛 API" 참고).
--    4) 백업을 받았다:
--         mysqldump -u {user} -p --default-character-set=utf8mb4 {database} \
--             steam_cell_value steam_unit_usage steam_unit_usage_raw > steam_legacy_tables_backup.sql
--       되돌릴 때:  mysql -u {user} -p --default-character-set=utf8mb4 {database} < steam_legacy_tables_backup.sql
--
--  실행
--    mysql -u {user} -p --default-character-set=utf8mb4 {database} < cleanup/drop_legacy_tables.sql
--
--  재실행해도 안전하다 (없으면 건너뛴다). 다른 표와 데이터는 건드리지 않는다.
-- ============================================================

-- 지우기 전 건수 (기록용)
SELECT t.table_name AS 지울_표, t.table_rows AS 대략_행수
  FROM information_schema.tables t
 WHERE t.table_schema = DATABASE()
   AND t.table_name IN ('steam_cell_value', 'steam_unit_usage', 'steam_unit_usage_raw');

DROP TABLE IF EXISTS steam_cell_value;
DROP TABLE IF EXISTS steam_unit_usage;
DROP TABLE IF EXISTS steam_unit_usage_raw;

-- 지운 뒤 확인 — 아무 행도 나오지 않아야 한다
SELECT t.table_name AS 남아_있는_옛_표
  FROM information_schema.tables t
 WHERE t.table_schema = DATABASE()
   AND t.table_name IN ('steam_cell_value', 'steam_unit_usage', 'steam_unit_usage_raw');
