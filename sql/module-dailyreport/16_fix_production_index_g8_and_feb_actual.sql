-- ============================================================
-- 모듈: module-dailyreport (세부공장일보 — 표1 주요 생산지표 현황)
-- 파일: 16_fix_production_index_g8_and_feb_actual.sql
-- 설명: 표1(TBL_PRODUCTION_INDEX) "초지5 생산량(톤/日)" 행(ROW_INDEX=3)의
--       두 값이 서로 바뀌어 있던 오류를 정정 (2026-09, 개발서버 테스트 중 발견).
--
--       [배경]
--       - '25년 월평균(G열, COL_INDEX=5, EXCEL_COORD='G8')은 15번 스크립트에서
--         76 → 80.7 로 정정했으나, 이 80.7이라는 값 자체가 잘못된 값이었다.
--         올바른 값은 82.2 이다.
--       - "2월"(anchor 2026-02월 기준, 히스토리 컬럼 H8~N8 중 리포트 날짜에
--         따라 위치가 달라짐)의 실적 하드코딩값이 85.6으로 되어 있었는데,
--         올바른 값은 80.7 이다.
--       - 즉 82.2(월평균)과 80.7(2월 실적) 두 값이 코드 작성 시점에 서로
--         뒤섞여 들어간 것으로 보인다 (80.7이 월평균 자리에, 85.6이 2월
--         실적 자리에 잘못 들어감).
--
--       코드 수정(DefaultCellTemplate.java)은 이 스크립트와 별개로 이미
--       feature/daily-report-safety-incident-recompute-fix-jspark1 브랜치에
--       반영되어 있다 — 그 코드 배포 이후 "신규 생성"되는 일보는 자동으로
--       올바른 값(월평균 82.2 / 2월 79.9→아니고 80.7)이 채워지며, 이
--       스크립트는 "이미 존재하는" 일보만 대상으로 한다.
--
--       ※ 15번 스크립트를 직접 고치지 않고 이 16번 스크립트를 새로 추가한
--         이유: 15번은 이미 테스트 DB에 실행되어 76→80.7로 바뀐 이력이
--         있으므로, 이미 실행된 마이그레이션 파일은 그대로 두고 "정정"은
--         새 파일로 누적하는 것이 안전하다(재실행/이력 추적 관점).
--
-- 구성:
--   [A] G8('25년 월평균) 정정 — 76 또는 80.7로 되어 있는 셀을 82.2로 UPDATE
--       (운영 DB=아직 15번 미실행=76 그대로인 경우와, 테스트 DB=15번 실행
--        완료=80.7인 경우를 모두 커버. 이미 82.2인 셀은 대상에서 자동 제외)
--   [B] "2월"(anchor 2026-02) 실적 히스토리 컬럼 정정 — 85.6 → 80.7
--       (리포트 날짜에 따라 실제 컬럼(COL_INDEX)이 달라지므로, 리포트월과
--        2026-02 사이의 개월 차이를 계산해 정확한 컬럼 위치까지 특정한다.
--        단순히 CELL_VALUE=85.6인 셀 전체를 바꾸면, 실제 측정값이 우연히
--        85.6인 다른 리포트/다른 달의 데이터까지 잘못 건드릴 위험이 있어
--        방어적으로 좁혔다.)
--
-- 실행 순서: [A]/[B] 각각 0.사전점검 → 1.값정정 → 2.사후검증
--
-- 주의:
--   - CELL_TYPE='READONLY' 조건을 반드시 포함해 사람이 입력한 DATA 셀을
--     보호한다.
--   - 재실행해도 안전(idempotent) — 이미 정정된 셀은 조건에 안 걸려 자동
--     제외된다.
--   - 운영/플랫폼: mysql -u {user} -p platform_db < 16_fix_production_index_g8_and_feb_actual.sql
-- ============================================================


-- ═══════════════════════════════════════════════
-- [A-0] 사전 점검 — G8('25년 월평균) 현재 값 분포
-- ═══════════════════════════════════════════════
SELECT '=== A-0-1. 사전 점검: G8(표1, 초지5 생산량 25년 월평균) 값 분포 ===' AS section;

SELECT c.CELL_VALUE, COUNT(*) AS cell_count
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.COL_INDEX = 5
 GROUP BY c.CELL_VALUE
 ORDER BY cell_count DESC;

SELECT '=== A-0-2. 사전 점검: 변경 대상 건수 (CELL_VALUE IN (76,80.7), READONLY) ===' AS section;

SELECT COUNT(*) AS target_count
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.COL_INDEX = 5
   AND c.CELL_TYPE = 'READONLY'
   AND c.CELL_VALUE IN ('76', '80.7');


-- ═══════════════════════════════════════════════
-- [A-1] 값 정정 — G8: 76 또는 80.7 → 82.2
-- ═══════════════════════════════════════════════
SELECT '=== A-1. 값 정정: G8 CELL_VALUE (76 또는 80.7) → 82.2 ===' AS section;

UPDATE daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
   SET c.CELL_VALUE = '82.2'
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.COL_INDEX = 5
   AND c.CELL_TYPE = 'READONLY'
   AND c.CELL_VALUE IN ('76', '80.7');


-- ═══════════════════════════════════════════════
-- [A-2] 사후 검증 — G8
-- ═══════════════════════════════════════════════
SELECT '=== A-2-1. 사후 검증: G8 중 옛값(76/80.7)이 남아있는 셀 (0건이어야 함) ===' AS section;

SELECT COUNT(*) AS remaining_old_value
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.COL_INDEX = 5
   AND c.CELL_VALUE IN ('76', '80.7');

SELECT '=== A-2-2. 사후 검증: G8 값 분포 (최종 확인용) ===' AS section;

SELECT c.CELL_VALUE, COUNT(*) AS cell_count
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.COL_INDEX = 5
 GROUP BY c.CELL_VALUE
 ORDER BY cell_count DESC;


-- ═══════════════════════════════════════════════
-- [B-0] 사전 점검 — "2월"(anchor 2026-02) 실적 히스토리 컬럼 대상 확인
--   * 리포트월(REPORT_DATE의 연-월)과 2026-02 사이의 개월 차이(delta)가
--     1~7 이면, 그 리포트에서 2026-02는 히스토리 컬럼(H~N, COL_INDEX 6~12)
--     중 COL_INDEX = 13 - delta 위치에 표시된다.
--     (delta=1 → COL_INDEX=12(N), delta=7 → COL_INDEX=6(H))
-- ═══════════════════════════════════════════════
SELECT '=== B-0-1. 사전 점검: 2026-02 실적 컬럼 후보 리포트 및 현재 값 ===' AS section;

SELECT r.REPORT_DATE,
       TIMESTAMPDIFF(MONTH, '2026-02-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) AS delta_months,
       13 - TIMESTAMPDIFF(MONTH, '2026-02-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) AS expected_col_index,
       c.COL_INDEX, c.CELL_VALUE, c.CELL_TYPE
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.CELL_TYPE = 'READONLY'
   AND TIMESTAMPDIFF(MONTH, '2026-02-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-02-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'))
 ORDER BY r.REPORT_DATE;

SELECT '=== B-0-2. 사전 점검: 변경 대상 건수 (위 조건 + CELL_VALUE=85.6) ===' AS section;

SELECT COUNT(*) AS target_count
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.CELL_TYPE = 'READONLY'
   AND c.CELL_VALUE = '85.6'
   AND TIMESTAMPDIFF(MONTH, '2026-02-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-02-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'));


-- ═══════════════════════════════════════════════
-- [B-1] 값 정정 — 2026-02 히스토리 컬럼: 85.6 → 80.7
-- ═══════════════════════════════════════════════
SELECT '=== B-1. 값 정정: 2026-02 실적 컬럼 CELL_VALUE 85.6 → 80.7 ===' AS section;

UPDATE daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
   SET c.CELL_VALUE = '80.7'
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.CELL_TYPE = 'READONLY'
   AND c.CELL_VALUE = '85.6'
   AND TIMESTAMPDIFF(MONTH, '2026-02-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-02-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'));


-- ═══════════════════════════════════════════════
-- [B-2] 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== B-2-1. 사후 검증: 2026-02 실적 컬럼 중 옛값(85.6) 남아있는 셀 (0건이어야 함) ===' AS section;

SELECT COUNT(*) AS remaining_old_value
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.CELL_VALUE = '85.6'
   AND TIMESTAMPDIFF(MONTH, '2026-02-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-02-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'));

SELECT '=== B-2-2. 사후 검증: 2026-02 실적 컬럼 값/건수 (최종 확인용) ===' AS section;

SELECT r.REPORT_DATE, c.COL_INDEX, c.CELL_VALUE
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 3
   AND c.CELL_TYPE = 'READONLY'
   AND TIMESTAMPDIFF(MONTH, '2026-02-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-02-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'))
 ORDER BY r.REPORT_DATE;

SELECT '=== 16_fix_production_index_g8_and_feb_actual.sql 실행 완료 ===' AS message;
