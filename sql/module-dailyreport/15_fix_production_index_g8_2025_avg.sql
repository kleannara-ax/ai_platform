-- ============================================================
-- 모듈: module-dailyreport (세부공장일보 — 표1 주요 생산지표 현황)
-- 파일: 15_fix_production_index_g8_2025_avg.sql
-- 설명: ★ 표1(TBL_PRODUCTION_INDEX) "초지5 생산량(톤/日)" 행의 '25년
--       월평균(G열, EXCEL_COORD='G8', ROW_INDEX=3, COL_INDEX=5) READONLY
--       셀 하드코딩 값을 76 → 80.7로 정정하는 1회성 운영 반영 스크립트
--       (2026-09).
--
--       [배경]
--       이 값은 DefaultCellTemplate.addProductionIndexCells()에 리터럴
--       문자열로 하드코딩되어 있으며(yearlyAverage(... , "76")), 2025년
--       (ANCHOR_MONTH=2026-07보다 이전 연도)은 DB 실측 조회 없이 항상 이
--       리터럴값을 그대로 사용해 READONLY 셀로 고정 저장한다.
--       즉 이 값은 "일보가 생성되는 시점"에 코드가 계산해 DB에 박아 넣는
--       값이라, 코드(리터럴 "76"→"80.7")를 고쳐도 *이미 생성되어 있던*
--       일보의 G8 값은 자동으로 바뀌지 않는다 — 이 스크립트가 그 기존
--       데이터를 코드 수정과 별도로 백필한다.
--
--       코드 수정(DefaultCellTemplate.java, "76"→"80.7")은 이 스크립트와
--       별개로 이미 feature/daily-report-safety-incident-recompute-fix-jspark1
--       브랜치에 반영되어 있다 — 그 코드 배포 이후 "신규 생성"되는 일보는
--       자동으로 80.7이 채워지며, 이 스크립트는 "이미 존재하는" 일보만
--       대상으로 한다.
--
-- 실행 순서:
--   1) "0. 사전 점검" — 현재 G8 값 분포 확인 (76으로 저장된 셀이 몇 건인지,
--      혹시 이미 80.7이거나 그 외 값으로 되어 있는 셀이 있는지)
--   2) "1. 값 정정" — G8 좌표(TBL_PRODUCTION_INDEX, ROW_INDEX=3, COL_INDEX=5)
--      중 CELL_VALUE='76'인 셀만 '80.7'로 UPDATE
--   3) "2. 사후 검증" — 76이 남아있지 않은지, 80.7 건수가 늘었는지 확인
--
-- 주의:
--   - CELL_TYPE='READONLY' 조건을 반드시 포함한다 — 좌표(ROW_INDEX/
--     COL_INDEX)만으로 걸러도 이론상 항상 READONLY만 존재하는 위치이지만,
--     혹시 모를 표 구조 변경/오염 데이터로부터 사람이 입력한 DATA 셀을
--     실수로 건드리지 않도록 방어적으로 명시한다.
--   - CELL_VALUE='76'인 셀만 대상으로 한다(값을 특정해서 좁힘) — 이미
--     수동으로 다른 값(예: 사용자가 직접 고친 케이스)으로 바뀌어 있는
--     셀은 건드리지 않는다. 재실행해도 안전(idempotent)하다 — 이미
--     80.7로 바뀐 셀은 CELL_VALUE='76' 조건에 걸리지 않아 대상에서
--     자동 제외된다.
--   - 이 스크립트가 대상으로 삼는 셀은 TBL_PRODUCTION_INDEX(표1)의
--     ROW_INDEX=3, COL_INDEX=5(G8, "초지5 생산량" 행의 '25년 월평균)
--     1개 좌표뿐이며, 다른 표/다른 좌표에는 영향을 주지 않는다.
--   - 운영/플랫폼: mysql -u {user} -p platform_db < 15_fix_production_index_g8_2025_avg.sql
-- ============================================================


-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — 현재 G8 값 분포 확인
-- ═══════════════════════════════════════════════
SELECT '=== 0-1. 사전 점검: G8(표1, 초지5 생산량 25년 월평균) 값 분포 ===' AS section;

SELECT c.CELL_VALUE, COUNT(*) AS cell_count
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.COL_INDEX = 5
 GROUP BY c.CELL_VALUE
 ORDER BY cell_count DESC;

SELECT '=== 0-2. 사전 점검: 변경 대상 건수 (CELL_VALUE=76, CELL_TYPE=READONLY) ===' AS section;

SELECT COUNT(*) AS target_count
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.COL_INDEX = 5
   AND c.CELL_TYPE = 'READONLY'
   AND c.CELL_VALUE = '76';


-- ═══════════════════════════════════════════════
-- 1. 값 정정 — G8 좌표 중 '76'으로 저장된 셀만 '80.7'로 UPDATE
-- ═══════════════════════════════════════════════
SELECT '=== 1. 값 정정: G8 CELL_VALUE 76 → 80.7 ===' AS section;

UPDATE daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
   SET c.CELL_VALUE = '80.7'
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.COL_INDEX = 5
   AND c.CELL_TYPE = 'READONLY'
   AND c.CELL_VALUE = '76';


-- ═══════════════════════════════════════════════
-- 2. 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== 2-1. 사후 검증: G8 중 아직 76으로 남아있는 셀 (0건이어야 함) ===' AS section;

SELECT COUNT(*) AS remaining_old_value
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.COL_INDEX = 5
   AND c.CELL_VALUE = '76';

SELECT '=== 2-2. 사후 검증: G8 값 분포 (최종 확인용) ===' AS section;

SELECT c.CELL_VALUE, COUNT(*) AS cell_count
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.COL_INDEX = 5
 GROUP BY c.CELL_VALUE
 ORDER BY cell_count DESC;

SELECT '=== 15_fix_production_index_g8_2025_avg.sql 실행 완료 ===' AS message;
