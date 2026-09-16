-- ============================================================
-- 모듈: module-dailyreport (세부공장일보 — 표1 주요 생산지표 현황)
-- 파일: 17_fix_production_index_tissue_yield_mar_apr.sql
-- 설명: 표1(TBL_PRODUCTION_INDEX) "수율(%) - 화장지" 행(ROW_INDEX=6)의
--       3월/4월 실적 하드코딩값 오기입 정정 (2026-09, 개발서버 테스트 중
--       사용자 재확인 요청으로 발견).
--
--       [배경]
--       - DefaultCellTemplate.java의 하드코딩 폴백맵(fb11)에서:
--           · 3월실적(anchor 기준 2026-03): 63.6 → 63.1
--           · 4월실적(anchor 기준 2026-04): 69.6 → 66.6
--         로 코드를 수정했다(feature/daily-report-ui-fixes-jspark1,
--         커밋 ca63428).
--       - 하지만 이 하드코딩 값은 "일보(리포트)가 처음 생성되는 시점"에만
--         셀에 채워지고, 이미 생성되어 DB에 저장된 리포트의 셀은 코드가
--         바뀌어도 재계산되지 않는다. 3월/4월은 FEATURE_CUTOFF_DATE
--         (2026-07-22) 이전 달이라 실측 조회 대상도 아니므로, 이미
--         생성된 리포트에는 옛 하드코딩 값(63.6/69.6)이 그대로 남는다.
--       - 따라서 코드 배포와 별개로, 이미 존재하는 리포트의 해당 셀 값을
--         이 스크립트로 직접 정정해야 한다.
--
--       코드 수정은 이 스크립트와 별개로 이미
--       feature/daily-report-ui-fixes-jspark1 브랜치에 반영되어 있다 —
--       그 코드 배포 이후 "신규 생성"되는 일보는 자동으로 올바른 값
--       (3월 63.1 / 4월 66.6)이 채워지며, 이 스크립트는 "이미 존재하는"
--       일보만 대상으로 한다.
--
-- 구성:
--   [A] 3월(anchor 2026-03) 실적 히스토리 컬럼 정정 — 63.6 → 63.1
--   [B] 4월(anchor 2026-04) 실적 히스토리 컬럼 정정 — 69.6 → 66.6
--       (리포트 날짜에 따라 실제 컬럼(COL_INDEX)이 달라지므로, 리포트월과
--        대상월(2026-03/2026-04) 사이의 개월 차이를 계산해 정확한 컬럼
--        위치까지 특정한다. 단순히 CELL_VALUE로만 전체를 바꾸면, 실제
--        측정값이 우연히 같은 다른 리포트/다른 달의 데이터까지 잘못
--        건드릴 위험이 있어 방어적으로 좁혔다. — 16번 스크립트와 동일한
--        컬럼 위치 계산 패턴 재사용)
--
-- 실행 순서: [A]/[B] 각각 0.사전점검 → 1.값정정 → 2.사후검증
--
-- 주의:
--   - CELL_TYPE='READONLY' 조건을 반드시 포함해 사람이 입력한 DATA 셀을
--     보호한다.
--   - 재실행해도 안전(idempotent) — 이미 정정된 셀은 조건에 안 걸려 자동
--     제외된다.
--   - 운영/플랫폼: mysql -u {user} -p platform_db < 17_fix_production_index_tissue_yield_mar_apr.sql
-- ============================================================


-- ═══════════════════════════════════════════════
-- [A-0] 사전 점검 — "3월"(anchor 2026-03) 실적 히스토리 컬럼 대상 확인
--   * 리포트월(REPORT_DATE의 연-월)과 2026-03 사이의 개월 차이(delta)가
--     1~7 이면, 그 리포트에서 2026-03은 히스토리 컬럼(H~N, COL_INDEX 6~12)
--     중 COL_INDEX = 13 - delta 위치에 표시된다.
--     (delta=1 → COL_INDEX=12(N), delta=7 → COL_INDEX=6(H))
-- ═══════════════════════════════════════════════
SELECT '=== A-0-1. 사전 점검: 2026-03 실적 컬럼 후보 리포트 및 현재 값 ===' AS section;

SELECT r.REPORT_DATE,
       TIMESTAMPDIFF(MONTH, '2026-03-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) AS delta_months,
       13 - TIMESTAMPDIFF(MONTH, '2026-03-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) AS expected_col_index,
       c.COL_INDEX, c.CELL_VALUE, c.CELL_TYPE
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 6
   AND c.CELL_TYPE = 'READONLY'
   AND TIMESTAMPDIFF(MONTH, '2026-03-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-03-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'))
 ORDER BY r.REPORT_DATE;

SELECT '=== A-0-2. 사전 점검: 변경 대상 건수 (위 조건 + CELL_VALUE=63.6) ===' AS section;

SELECT COUNT(*) AS target_count
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 6
   AND c.CELL_TYPE = 'READONLY'
   AND c.CELL_VALUE = '63.6'
   AND TIMESTAMPDIFF(MONTH, '2026-03-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-03-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'));


-- ═══════════════════════════════════════════════
-- [A-1] 값 정정 — 2026-03 실적 히스토리 컬럼: 63.6 → 63.1
-- ═══════════════════════════════════════════════
SELECT '=== A-1. 값 정정: 2026-03 실적 컬럼 CELL_VALUE 63.6 → 63.1 ===' AS section;

UPDATE daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
   SET c.CELL_VALUE = '63.1'
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 6
   AND c.CELL_TYPE = 'READONLY'
   AND c.CELL_VALUE = '63.6'
   AND TIMESTAMPDIFF(MONTH, '2026-03-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-03-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'));


-- ═══════════════════════════════════════════════
-- [A-2] 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== A-2-1. 사후 검증: 2026-03 실적 컬럼 중 옛값(63.6) 남아있는 셀 (0건이어야 함) ===' AS section;

SELECT COUNT(*) AS remaining_old_value
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 6
   AND c.CELL_VALUE = '63.6'
   AND TIMESTAMPDIFF(MONTH, '2026-03-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-03-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'));

SELECT '=== A-2-2. 사후 검증: 2026-03 실적 컬럼 값/건수 (최종 확인용) ===' AS section;

SELECT r.REPORT_DATE, c.COL_INDEX, c.CELL_VALUE
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 6
   AND c.CELL_TYPE = 'READONLY'
   AND TIMESTAMPDIFF(MONTH, '2026-03-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-03-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'))
 ORDER BY r.REPORT_DATE;


-- ═══════════════════════════════════════════════
-- [B-0] 사전 점검 — "4월"(anchor 2026-04) 실적 히스토리 컬럼 대상 확인
-- ═══════════════════════════════════════════════
SELECT '=== B-0-1. 사전 점검: 2026-04 실적 컬럼 후보 리포트 및 현재 값 ===' AS section;

SELECT r.REPORT_DATE,
       TIMESTAMPDIFF(MONTH, '2026-04-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) AS delta_months,
       13 - TIMESTAMPDIFF(MONTH, '2026-04-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) AS expected_col_index,
       c.COL_INDEX, c.CELL_VALUE, c.CELL_TYPE
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 6
   AND c.CELL_TYPE = 'READONLY'
   AND TIMESTAMPDIFF(MONTH, '2026-04-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-04-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'))
 ORDER BY r.REPORT_DATE;

SELECT '=== B-0-2. 사전 점검: 변경 대상 건수 (위 조건 + CELL_VALUE=69.6) ===' AS section;

SELECT COUNT(*) AS target_count
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 6
   AND c.CELL_TYPE = 'READONLY'
   AND c.CELL_VALUE = '69.6'
   AND TIMESTAMPDIFF(MONTH, '2026-04-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-04-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'));


-- ═══════════════════════════════════════════════
-- [B-1] 값 정정 — 2026-04 실적 히스토리 컬럼: 69.6 → 66.6
-- ═══════════════════════════════════════════════
SELECT '=== B-1. 값 정정: 2026-04 실적 컬럼 CELL_VALUE 69.6 → 66.6 ===' AS section;

UPDATE daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
   SET c.CELL_VALUE = '66.6'
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 6
   AND c.CELL_TYPE = 'READONLY'
   AND c.CELL_VALUE = '69.6'
   AND TIMESTAMPDIFF(MONTH, '2026-04-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-04-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'));


-- ═══════════════════════════════════════════════
-- [B-2] 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== B-2-1. 사후 검증: 2026-04 실적 컬럼 중 옛값(69.6) 남아있는 셀 (0건이어야 함) ===' AS section;

SELECT COUNT(*) AS remaining_old_value
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 6
   AND c.CELL_VALUE = '69.6'
   AND TIMESTAMPDIFF(MONTH, '2026-04-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-04-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'));

SELECT '=== B-2-2. 사후 검증: 2026-04 실적 컬럼 값/건수 (최종 확인용) ===' AS section;

SELECT r.REPORT_DATE, c.COL_INDEX, c.CELL_VALUE
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.ROW_INDEX = 6
   AND c.CELL_TYPE = 'READONLY'
   AND TIMESTAMPDIFF(MONTH, '2026-04-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01')) BETWEEN 1 AND 7
   AND c.COL_INDEX = 13 - TIMESTAMPDIFF(MONTH, '2026-04-01', DATE_FORMAT(r.REPORT_DATE, '%Y-%m-01'))
 ORDER BY r.REPORT_DATE;

SELECT '=== 17_fix_production_index_tissue_yield_mar_apr.sql 실행 완료 ===' AS message;
