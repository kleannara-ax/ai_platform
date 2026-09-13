-- ============================================================
-- 모듈: module-dailyreport (세부공장일보 — 사고 통계, 표5/6)
-- 파일: 14_safety_incident_subtotal_autosum_migration.sql
-- 설명: ★ 표5(TBL_SAFETY_INCIDENT_COUNT)/표6(TBL_SAFETY_INCIDENT_AMOUNT)의
--       "당월" 소계(R열, COL_INDEX=17) 컬럼을 사람이 직접 입력하는 항목에서
--       "시스템이 자동 계산하는 항목"으로 전환하는 1회성 운영 반영 스크립트 (2026-09).
--
--       [배경]
--       이 변경 전에는 소계(R3~R9, 제지~에너지 7개 행)를 사람이 직접 입력할
--       수 있었다. 이제부터는 애플리케이션(CellService.java)이 저장 시점마다
--         (1) 각 행의 소계(R) = 그 행의 기계(O)+전기(P)+생산(Q) 가로합
--         (2) 합계 행(rowIndex=9, 엑셀 10행)의 O/P/Q = 7개 기여 행의 세로합
--         (3) 합계 행 자신의 소계(R10) = O10+P10+Q10 가로합
--       을 자동 계산한다. 합계 행(O10~R10)은 이전부터 이미 자동계산/컬럼권한
--       제외 대상이었고, 이번 변경으로 R3~R9(각 기여 행의 소계)도 동일하게
--       자동계산/컬럼권한 제외 대상에 새로 추가되었다.
--
--       이 스크립트는 그 앱 코드 변경(별도 배포)과 함께 운영 DB에 반드시
--       실행해야 하는 3가지 데이터 정리 작업을 담당한다:
--         1) daily_report_cell_auth.CELL_COORDS에서 R3~R9 좌표 제거
--            (더 이상 사람에게 배정 가능한 좌표가 아니므로)
--         2) daily_report_cell의 R3~R9 셀의 OWNER_IDS/OWNER_NAMES 캐시 초기화
--            (OWNER_IDS가 남아있으면 CellAuth보다 우선 적용되어 여전히
--             편집 가능한 것으로 오판될 수 있음 — README.md "셀 편집 가능
--             판단 흐름: 1순위 OWNER_IDS" 참고)
--         3) 기존에 이미 저장되어 있던 값을 새 자동계산 규칙에 맞게 백필
--            (R3~R9 가로합, 합계행 O10~Q10 세로합, 합계행 R10 가로합)
--
-- 실행 순서:
--   1) "0. 사전 점검" — 현재 상태 확인 (영향받는 CellAuth / 셀 개수)
--   2) "1. CellAuth 좌표 정리" — R3~R9 좌표 제거
--   3) "2. Owner 캐시 초기화" — R3~R9 셀의 OWNER_IDS/OWNER_NAMES = NULL
--   4) "3. 데이터 백필" — 3단계 순서대로 실행 (순서 중요! 3-3은 3-2 결과에 의존)
--      3-1) 기여 행(rowIndex 2~8) 소계(R) = 기계+전기+생산
--      3-2) 합계 행(rowIndex 9) O/P/Q = 기여 행 세로합
--      3-3) 합계 행(rowIndex 9) 소계(R) = 방금 계산된 합계 행의 O+P+Q
--   5) "4. 사후 검증" — R3~R9가 CellAuth에 전혀 남아있지 않은지, 백필 결과가
--      화면(가로합/세로합)과 일치하는지 확인
--
-- 주의:
--   - 모든 단계는 재실행해도 안전(idempotent)하도록 작성했다 — 이미 적용된
--     운영 DB에 다시 실행해도 결과가 바뀌지 않는다(같은 값을 다시 계산해
--     넣을 뿐이며, 이미 제거된 좌표를 다시 지우려 해도 대상이 없어 0 rows
--     affected로 끝난다).
--   - 11_add_daily_batchjob.sql / 12_alter_batchjob_date_to_date_type.sql과
--     동일한 방식으로 USE dailyreport_dev;를 강제하지 않는다 — 실행 전
--     mysql 클라이언트에서 대상 DB를 반드시 먼저 지정해야 한다:
--       자체 테스트: mysql -u factory_admin -p dailyreport_dev < 14_safety_incident_subtotal_autosum_migration.sql
--       운영/플랫폼: mysql -u {user} -p platform_db          < 14_safety_incident_subtotal_autosum_migration.sql
--   - MariaDB 10.5+ (JSON_TABLE, 윈도우 함수 불필요) 필요 — 본 프로젝트는
--     10.11 사용이므로 문제 없음.
--   - 이 스크립트가 대상으로 삼는 표는 TBL_SAFETY_INCIDENT_COUNT(표5, 발생건수)
--     / TBL_SAFETY_INCIDENT_AMOUNT(표6, 손실금액) 2개뿐이며, 다른 표(생산지표/
--     재공품/에너지/보일러/표7·표8 추이표)에는 영향을 주지 않는다.
-- ============================================================


-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — 현재 상태 확인
-- ═══════════════════════════════════════════════
SELECT '=== 0-1. 사전 점검: R3~R9 좌표가 남아있는 CellAuth 그랜트 ===' AS section;

SELECT ca.AUTH_ID, u.LOGIN_ID, ca.TABLE_CODE, ca.CELL_COORDS
  FROM daily_report_cell_auth ca
  JOIN core_user u ON u.USER_ID = ca.USER_ID
 WHERE ca.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND EXISTS (
       SELECT 1
         FROM JSON_TABLE(ca.CELL_COORDS, '$[*]' COLUMNS (coord VARCHAR(10) PATH '$')) jt
        WHERE jt.coord REGEXP '^R[3-9]$'
   );

SELECT '=== 0-2. 사전 점검: R3~R9 셀 중 OWNER_IDS가 아직 남아있는 셀 개수 ===' AS section;

SELECT t.TABLE_CODE, COUNT(*) AS cell_count
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
 WHERE t.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND c.ROW_INDEX BETWEEN 2 AND 8
   AND c.COL_INDEX = 17
   AND c.OWNER_IDS IS NOT NULL
 GROUP BY t.TABLE_CODE;

SELECT '=== 0-3. 사전 점검: 영향받는 표(일보) 인스턴스 개수 ===' AS section;

SELECT t.TABLE_CODE, COUNT(*) AS table_instance_count
  FROM daily_report_table t
 WHERE t.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
 GROUP BY t.TABLE_CODE;


-- ═══════════════════════════════════════════════
-- 1. CellAuth 좌표 정리 — R3~R9 좌표를 CELL_COORDS JSON 배열에서 제거
--    (JSON_TABLE로 배열을 펼친 뒤 R3~R9만 걸러내고 JSON_ARRAYAGG로 재조립.
--     이미 R3~R9가 없는 그랜트는 필터링 결과가 원본과 동일하므로 무해함.
--     모든 좌표가 R3~R9뿐이었던 그랜트는 결과가 빈 배열 '[]'이 된다 — 이
--     그랜트 자체를 삭제하지는 않는다(관리자가 컬럼관리 화면에서 직접 판단).)
-- ═══════════════════════════════════════════════
SELECT '=== 1. CellAuth 좌표 정리: R3~R9 제거 ===' AS section;

UPDATE daily_report_cell_auth ca
   SET ca.CELL_COORDS = COALESCE(
       (
           SELECT JSON_ARRAYAGG(jt.coord)
             FROM JSON_TABLE(ca.CELL_COORDS, '$[*]' COLUMNS (coord VARCHAR(10) PATH '$')) jt
            WHERE jt.coord NOT REGEXP '^R[3-9]$'
       ),
       '[]'
   )
 WHERE ca.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND EXISTS (
       SELECT 1
         FROM JSON_TABLE(ca.CELL_COORDS, '$[*]' COLUMNS (coord VARCHAR(10) PATH '$')) jt2
        WHERE jt2.coord REGEXP '^R[3-9]$'
   );


-- ═══════════════════════════════════════════════
-- 2. Owner 캐시 초기화 — R3~R9 셀의 OWNER_IDS/OWNER_NAMES를 NULL로
--    (README.md "셀 편집 가능 판단 흐름" 1순위가 OWNER_IDS이므로, 이 값이
--     남아있으면 CellAuth 정리와 무관하게 여전히 편집 가능한 것으로 판단됨)
-- ═══════════════════════════════════════════════
SELECT '=== 2. Owner 캐시 초기화: R3~R9 (rowIndex 2~8, colIndex 17) ===' AS section;

UPDATE daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
   SET c.OWNER_IDS = NULL,
       c.OWNER_NAMES = NULL
 WHERE t.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND c.ROW_INDEX BETWEEN 2 AND 8
   AND c.COL_INDEX = 17
   AND (c.OWNER_IDS IS NOT NULL OR c.OWNER_NAMES IS NOT NULL);


-- ═══════════════════════════════════════════════
-- 3. 데이터 백필 — 기존에 저장되어 있던 값을 새 자동계산 규칙에 맞게 재계산
--    (CellService.recomputeSafetyIncidentTotalIfNeeded와 동일한 규칙.
--     숫자 파싱: NULL/빈값/"-" → 0, 쉼표 제거 후 숫자 변환.
--     표시 형식: 소수 1자리로 반올림 후, 정수면 정수로 표시(".0" 제거).)
-- ═══════════════════════════════════════════════

-- ── 3-1. 기여 행(rowIndex 2~8)의 소계(R열, COL_INDEX=17)
--         = 같은 행의 기계(14)+전기(15)+생산(16) 가로합
SELECT '=== 3-1. 백필: 기여 행(제지~에너지) 소계 = 기계+전기+생산 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID, c.ROW_INDEX,
             SUM(
                 CASE
                     WHEN c.CELL_VALUE IS NULL THEN 0
                     WHEN TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                     ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4))
                 END
             ) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
         AND c.ROW_INDEX BETWEEN 2 AND 8
         AND c.COL_INDEX IN (14, 15, 16)
       GROUP BY c.TABLE_ID, c.ROW_INDEX
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID AND agg.ROW_INDEX = tgt.ROW_INDEX
   SET tgt.CELL_VALUE = IF(
       ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0),
       CAST(ROUND(agg.row_sum, 0) AS CHAR),
       CAST(ROUND(agg.row_sum, 1) AS CHAR)
   )
 WHERE tt.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND tgt.ROW_INDEX BETWEEN 2 AND 8
   AND tgt.COL_INDEX = 17;

-- ── 3-2. 합계 행(rowIndex=9, 엑셀 10행)의 기계(14)/전기(15)/생산(16)
--         = 7개 기여 행(rowIndex 2~8)의 같은 컬럼 세로합
SELECT '=== 3-2. 백필: 합계 행 기계/전기/생산 = 7개 기여 행 세로합 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID, c.COL_INDEX,
             SUM(
                 CASE
                     WHEN c.CELL_VALUE IS NULL THEN 0
                     WHEN TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                     ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4))
                 END
             ) AS col_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
         AND c.ROW_INDEX BETWEEN 2 AND 8
         AND c.COL_INDEX IN (14, 15, 16)
       GROUP BY c.TABLE_ID, c.COL_INDEX
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID AND agg.COL_INDEX = tgt.COL_INDEX
   SET tgt.CELL_VALUE = IF(
       ROUND(agg.col_sum, 1) = ROUND(agg.col_sum, 0),
       CAST(ROUND(agg.col_sum, 0) AS CHAR),
       CAST(ROUND(agg.col_sum, 1) AS CHAR)
   )
 WHERE tt.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND tgt.ROW_INDEX = 9
   AND tgt.COL_INDEX IN (14, 15, 16);

-- ── 3-3. 합계 행(rowIndex=9)의 소계(R10, COL_INDEX=17)
--         = 방금(3-2에서) 갱신된 합계 행 자신의 기계+전기+생산 가로합
--         ※ 반드시 3-2 다음에 실행해야 한다 (3-2 결과에 의존)
SELECT '=== 3-3. 백필: 합계 행 소계(R10) = 방금 계산된 합계 행 O10+P10+Q10 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(
                 CASE
                     WHEN c.CELL_VALUE IS NULL THEN 0
                     WHEN TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                     ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4))
                 END
             ) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
         AND c.ROW_INDEX = 9
         AND c.COL_INDEX IN (14, 15, 16)
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(
       ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0),
       CAST(ROUND(agg.row_sum, 0) AS CHAR),
       CAST(ROUND(agg.row_sum, 1) AS CHAR)
   )
 WHERE tt.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND tgt.ROW_INDEX = 9
   AND tgt.COL_INDEX = 17;


-- ═══════════════════════════════════════════════
-- 4. 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== 4-1. 사후 검증: R3~R9가 CellAuth에 완전히 사라졌는지 확인 (0건이어야 함) ===' AS section;

SELECT ca.AUTH_ID, u.LOGIN_ID, ca.TABLE_CODE, ca.CELL_COORDS
  FROM daily_report_cell_auth ca
  JOIN core_user u ON u.USER_ID = ca.USER_ID
 WHERE ca.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND EXISTS (
       SELECT 1
         FROM JSON_TABLE(ca.CELL_COORDS, '$[*]' COLUMNS (coord VARCHAR(10) PATH '$')) jt
        WHERE jt.coord REGEXP '^R[3-9]$'
   );

SELECT '=== 4-2. 사후 검증: R3~R9 셀 중 OWNER_IDS가 남아있는지 확인 (0건이어야 함) ===' AS section;

SELECT COUNT(*) AS remaining_owner_cache
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
 WHERE t.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND c.ROW_INDEX BETWEEN 2 AND 8
   AND c.COL_INDEX = 17
   AND c.OWNER_IDS IS NOT NULL;

SELECT '=== 4-3. 사후 검증: 표별 rowIndex/colIndex 14~17 최종 값 (표본 확인용) ===' AS section;

SELECT rp.REPORT_DATE, t.TABLE_CODE, c.ROW_INDEX, c.EXCEL_COORD, c.CELL_VALUE
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report rp ON rp.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND c.ROW_INDEX BETWEEN 2 AND 9
   AND c.COL_INDEX BETWEEN 14 AND 17
 ORDER BY rp.REPORT_DATE DESC, t.TABLE_CODE, c.ROW_INDEX, c.COL_INDEX
 LIMIT 40;

SELECT '=== 14_safety_incident_subtotal_autosum_migration.sql 실행 완료 ===' AS message;
