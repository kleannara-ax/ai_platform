-- ============================================================
-- 모듈: module-dailyreport (세부공장일보 — 사고 통계, 표5/6/7/8)
-- 파일: 18_fix_safety_incident_forward_carryover_recompute.sql
-- 설명: ★ 표5(TBL_SAFETY_INCIDENT_COUNT)/표6(TBL_SAFETY_INCIDENT_AMOUNT)의
--       "당월" 소계(R열)/합계(10행)와 표7(TBL_SAFETY_YEARLY_TREND)/
--       표8(TBL_SAFETY_MONTHLY_TREND)의 "발생건수/합계" 계층 합산 행이,
--       저장 당일 이후(다음 날 이후 새로 생성/이어받기되는 일보)로는
--       재계산되지 않고 예전(빈/오래된) 값이 그대로 남아 있던 버그를
--       바로잡는 1회성 운영 반영 스크립트 (2026-09-21).
--
--       [배경 — 버그였던 이유]
--       CellService.saveCells()는 저장된 셀이 표5/6/7/8의 "실측 입력 컬럼"
--       일 때 그 저장이 벌어진 "오늘" 일보에 대해서만 소계/합계 재계산
--       (recomputeSafetyIncidentTotalIfNeeded/recomputeSafetyTrendTotalsIfNeeded)
--       을 실행했다. 그런데 같은 저장 트랜잭션 안에서 propagateValueForward()가
--       "이미 만들어져 있는 미래 일보들"에도 같은 좌표의 값을 함께 전파하는데,
--       이 전파 대상 미래 일보들에 대해서는 재계산 호출이 빠져 있었다.
--
--       즉:
--         1) 9/15에 표6 기계/전기/생산 값을 저장 → 9/15의 소계/합계는 정상 계산됨
--         2) 이미 존재하던 9/16 일보에도 같은 기계/전기/생산 "원본값"은
--            정상적으로 전파(propagate)됨
--         3) 그러나 9/16 일보의 소계/합계는 재계산되지 않고 예전(빈) 값으로 멈춤
--         4) 이후 9/17, 9/18 ... 매일 새로 생성되는 일보가 "직전 일보(9/16)"의
--            값을 그대로 이어받으므로, 한 번 멈춘 빈 소계/합계가 그 이후 모든
--            날짜로 계속 이어져 내려간다 — 코드 재배포만으로는 이미 잘못
--            저장되어 있는 이 데이터가 자동으로 고쳐지지 않는다.
--
--       코드 수정(CellService.propagateValueForward에 재계산 호출 추가, 별도
--       배포)과 함께, 운영 DB에 이미 쌓여 있는 "구멍난" 소계/합계 값을 이
--       스크립트로 한 번 백필해야 한다.
--
--       이 스크립트는 4개 표 모두를 대상으로 한다:
--         - 표5(TBL_SAFETY_INCIDENT_COUNT)/표6(TBL_SAFETY_INCIDENT_AMOUNT):
--           R열(COL_INDEX=17, 소계) = 같은 행의 기계(14)+전기(15)+생산(16),
--           합계 행(ROW_INDEX=9)의 14~16열 = 7개 기여 행(2~8)의 세로합,
--           합계 행의 R열(17) = 합계 행 자신의 14+15+16 가로합
--           (14_safety_incident_subtotal_autosum_migration.sql과 동일한 계산
--            규칙 — 이번엔 "이미 값이 있었지만 전파 과정에서 갱신이 빠진" 모든
--            일보를 대상으로 다시 돌리는 것이 차이점)
--         - 표7(TBL_SAFETY_YEARLY_TREND, 라이브 컬럼=COL_INDEX 11):
--           row3=row1+row2, row6=row4+row5, row7=row3+row6,
--           row10=row8+row9, row11=row1+row4+row8, row12=row2+row5+row9,
--           row13=row11+row12  (CellService.SAFETY_TREND_SUM_STEPS와 동일,
--           반드시 이 순서대로 실행 — 뒷 단계가 앞 단계 결과를 재료로 사용)
--         - 표8(TBL_SAFETY_MONTHLY_TREND, 라이브 컬럼=COL_INDEX 18):
--           row4=row2+row3, row7=row5+row6, row10=row8+row9,
--           row11=row2+row5+row8, row12=row3+row6+row9, row13=row11+row12
--
-- 실행 순서:
--   1) "0. 사전 점검" — 영향받는 표 인스턴스/셀 개수 확인
--   2) "1. 표5/6 백필" — 3단계(기여 행 소계 → 합계 행 세로합 → 합계 행 소계)
--   3) "2. 표7 백필" — 7단계(SAFETY_TREND_SUM_STEPS 순서 그대로)
--   4) "3. 표8 백필" — 6단계(SAFETY_TREND_SUM_STEPS 순서 그대로)
--   5) "4. 사후 검증" — 표본 조회로 화면과 값이 일치하는지 확인
--
-- 주의:
--   - 모든 UPDATE는 재실행해도 안전(idempotent)하다 — 같은 재료 값으로 같은
--     결과를 다시 계산해 넣을 뿐이며, 이미 올바른 값이면 그대로 유지된다.
--   - 값 0은 "0"이 아니라 "-"로 저장한다(CellService.formatSafetyIncidentSum과
--     동일한 표시 규칙 — 화면 formatNumber()도 0→"-"로 표시하므로 저장값 자체를
--     일관되게 맞춘다).
--   - USE dailyreport_dev;를 강제하지 않는다 — 실행 전 mysql 클라이언트에서
--     대상 DB를 반드시 먼저 지정해야 한다:
--       자체 테스트: mysql -u factory_admin -p dailyreport_dev < 18_fix_safety_incident_forward_carryover_recompute.sql
--       운영/플랫폼: mysql -u {user} -p platform_db          < 18_fix_safety_incident_forward_carryover_recompute.sql
--   - MariaDB 10.5+ 필요 (JSON_TABLE 미사용, 일반 UPDATE...JOIN만 사용) — 본
--     프로젝트는 10.11 사용이므로 문제 없음.
--   - 이 스크립트가 대상으로 삼는 표는 TBL_SAFETY_INCIDENT_COUNT(표5)/
--     TBL_SAFETY_INCIDENT_AMOUNT(표6)/TBL_SAFETY_YEARLY_TREND(표7)/
--     TBL_SAFETY_MONTHLY_TREND(표8) 4개뿐이며, 다른 표(생산지표/재공품/
--     에너지/보일러/표9·10 특이사항)에는 영향을 주지 않는다.
-- ============================================================


-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — 현재 상태 확인
-- ═══════════════════════════════════════════════
SELECT '=== 0-1. 사전 점검: 표5/6 영향받는 일보(표 인스턴스) 개수 ===' AS section;

SELECT t.TABLE_CODE, COUNT(*) AS table_instance_count
  FROM daily_report_table t
 WHERE t.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
 GROUP BY t.TABLE_CODE;

SELECT '=== 0-2. 사전 점검: 표7/8 영향받는 일보(표 인스턴스) 개수 ===' AS section;

SELECT t.TABLE_CODE, COUNT(*) AS table_instance_count
  FROM daily_report_table t
 WHERE t.TABLE_CODE IN ('TBL_SAFETY_YEARLY_TREND', 'TBL_SAFETY_MONTHLY_TREND')
 GROUP BY t.TABLE_CODE;

SELECT '=== 0-3. 사전 점검: 표6(손실금액) 최근 10일 합계 행(ROW_INDEX=9) 소계(R열) 표본 ===' AS section;

SELECT rp.REPORT_DATE, c.CELL_VALUE AS current_total_subtotal
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report rp ON rp.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_SAFETY_INCIDENT_AMOUNT'
   AND c.ROW_INDEX = 9
   AND c.COL_INDEX = 17
 ORDER BY rp.REPORT_DATE DESC
 LIMIT 10;


-- ═══════════════════════════════════════════════
-- 1. 표5(발생건수)/표6(손실금액) 백필
--    (14_safety_incident_subtotal_autosum_migration.sql의 3단계와 동일한
--     계산 규칙을 "이미 값이 있는 모든 일보"에 다시 적용한다)
-- ═══════════════════════════════════════════════

-- ── 1-1. 기여 행(ROW_INDEX 2~8)의 소계(R열, COL_INDEX=17)
--         = 같은 행의 기계(14)+전기(15)+생산(16) 가로합
SELECT '=== 1-1. 표5/6 백필: 기여 행(제지~에너지) 소계 = 기계+전기+생산 ===' AS section;

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
       ROUND(agg.row_sum, 1) = 0, '-',
       IF(
           ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0),
           CAST(ROUND(agg.row_sum, 0) AS CHAR),
           CAST(ROUND(agg.row_sum, 1) AS CHAR)
       )
   )
 WHERE tt.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND tgt.ROW_INDEX BETWEEN 2 AND 8
   AND tgt.COL_INDEX = 17;

-- ── 1-2. 합계 행(ROW_INDEX=9, 엑셀 10행)의 기계(14)/전기(15)/생산(16)
--         = 7개 기여 행(ROW_INDEX 2~8)의 같은 컬럼 세로합
SELECT '=== 1-2. 표5/6 백필: 합계 행 기계/전기/생산 = 7개 기여 행 세로합 ===' AS section;

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
       ROUND(agg.col_sum, 1) = 0, '-',
       IF(
           ROUND(agg.col_sum, 1) = ROUND(agg.col_sum, 0),
           CAST(ROUND(agg.col_sum, 0) AS CHAR),
           CAST(ROUND(agg.col_sum, 1) AS CHAR)
       )
   )
 WHERE tt.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND tgt.ROW_INDEX = 9
   AND tgt.COL_INDEX IN (14, 15, 16);

-- ── 1-3. 합계 행(ROW_INDEX=9)의 소계(R열, COL_INDEX=17)
--         = 방금(1-2에서) 갱신된 합계 행 자신의 기계+전기+생산 가로합
--         ※ 반드시 1-2 다음에 실행해야 한다 (1-2 결과에 의존)
SELECT '=== 1-3. 표5/6 백필: 합계 행 소계 = 방금 계산된 합계 행 기계+전기+생산 ===' AS section;

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
       ROUND(agg.row_sum, 1) = 0, '-',
       IF(
           ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0),
           CAST(ROUND(agg.row_sum, 0) AS CHAR),
           CAST(ROUND(agg.row_sum, 1) AS CHAR)
       )
   )
 WHERE tt.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND tgt.ROW_INDEX = 9
   AND tgt.COL_INDEX = 17;


-- ═══════════════════════════════════════════════
-- 2. 표7(TBL_SAFETY_YEARLY_TREND, 라이브 컬럼=COL_INDEX 11) 백필
--    CellService.SAFETY_TREND_SUM_STEPS["TBL_SAFETY_YEARLY_TREND"]와 동일한
--    순서로 7단계를 그대로 실행한다 (뒷 단계가 앞 단계 결과를 재료로 사용
--    하므로 반드시 이 순서를 지켜야 한다).
-- ═══════════════════════════════════════════════

-- 재사용 매크로 설명: 아래 UPDATE들은 모두
--   "대상행 = 원천행1 + 원천행2 (+ 원천행3)" 형태이며, 같은 COL_INDEX(11)
--   안에서 TABLE_ID 기준으로 값을 더한다.

-- ── 2-1. row3 = row1(공장 재해자수) + row2(공장 사망자수)
SELECT '=== 2-1. 표7 백필: row3(공장 발생건수) = row1+row2 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND'
         AND c.ROW_INDEX IN (1, 2) AND c.COL_INDEX = 11
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND' AND tgt.ROW_INDEX = 3 AND tgt.COL_INDEX = 11;

-- ── 2-2. row6 = row4(협력사 재해자수) + row5(협력사 사망자수)
SELECT '=== 2-2. 표7 백필: row6(협력사 발생건수) = row4+row5 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND'
         AND c.ROW_INDEX IN (4, 5) AND c.COL_INDEX = 11
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND' AND tgt.ROW_INDEX = 6 AND tgt.COL_INDEX = 11;

-- ── 2-3. row7 = row3(공장 발생건수, 방금 계산됨) + row6(협력사 발생건수, 방금 계산됨)
SELECT '=== 2-3. 표7 백필: row7(청주공장 총 발생건수) = row3+row6 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND'
         AND c.ROW_INDEX IN (3, 6) AND c.COL_INDEX = 11
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND' AND tgt.ROW_INDEX = 7 AND tgt.COL_INDEX = 11;

-- ── 2-4. row10 = row8(자회사 재해자수) + row9(자회사 사망자수)
SELECT '=== 2-4. 표7 백필: row10(자회사 발생건수) = row8+row9 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND'
         AND c.ROW_INDEX IN (8, 9) AND c.COL_INDEX = 11
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND' AND tgt.ROW_INDEX = 10 AND tgt.COL_INDEX = 11;

-- ── 2-5. row11(합계 재해자수) = row1+row4+row8
SELECT '=== 2-5. 표7 백필: row11(합계 재해자수) = row1+row4+row8 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND'
         AND c.ROW_INDEX IN (1, 4, 8) AND c.COL_INDEX = 11
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND' AND tgt.ROW_INDEX = 11 AND tgt.COL_INDEX = 11;

-- ── 2-6. row12(합계 사망자수) = row2+row5+row9
SELECT '=== 2-6. 표7 백필: row12(합계 사망자수) = row2+row5+row9 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND'
         AND c.ROW_INDEX IN (2, 5, 9) AND c.COL_INDEX = 11
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND' AND tgt.ROW_INDEX = 12 AND tgt.COL_INDEX = 11;

-- ── 2-7. row13(합계 총 발생건수) = row11(방금 계산됨) + row12(방금 계산됨)
SELECT '=== 2-7. 표7 백필: row13(합계 총 발생건수) = row11+row12 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND'
         AND c.ROW_INDEX IN (11, 12) AND c.COL_INDEX = 11
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND' AND tgt.ROW_INDEX = 13 AND tgt.COL_INDEX = 11;


-- ═══════════════════════════════════════════════
-- 3. 표8(TBL_SAFETY_MONTHLY_TREND, 라이브 컬럼=COL_INDEX 18) 백필
--    CellService.SAFETY_TREND_SUM_STEPS["TBL_SAFETY_MONTHLY_TREND"]와 동일한
--    순서로 6단계를 그대로 실행한다.
-- ═══════════════════════════════════════════════

-- ── 3-1. row4 = row2(공장 재해자수) + row3(공장 사망자수)
SELECT '=== 3-1. 표8 백필: row4(공장 발생건수) = row2+row3 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND'
         AND c.ROW_INDEX IN (2, 3) AND c.COL_INDEX = 18
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND' AND tgt.ROW_INDEX = 4 AND tgt.COL_INDEX = 18;

-- ── 3-2. row7 = row5(협력사 재해자수) + row6(협력사 사망자수)
SELECT '=== 3-2. 표8 백필: row7(협력사 발생건수) = row5+row6 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND'
         AND c.ROW_INDEX IN (5, 6) AND c.COL_INDEX = 18
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND' AND tgt.ROW_INDEX = 7 AND tgt.COL_INDEX = 18;

-- ── 3-3. row10 = row8(자회사 재해자수) + row9(자회사 사망자수)
SELECT '=== 3-3. 표8 백필: row10(자회사 발생건수) = row8+row9 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND'
         AND c.ROW_INDEX IN (8, 9) AND c.COL_INDEX = 18
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND' AND tgt.ROW_INDEX = 10 AND tgt.COL_INDEX = 18;

-- ── 3-4. row11(합계 재해자수) = row2+row5+row8
SELECT '=== 3-4. 표8 백필: row11(합계 재해자수) = row2+row5+row8 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND'
         AND c.ROW_INDEX IN (2, 5, 8) AND c.COL_INDEX = 18
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND' AND tgt.ROW_INDEX = 11 AND tgt.COL_INDEX = 18;

-- ── 3-5. row12(합계 사망자수) = row3+row6+row9
SELECT '=== 3-5. 표8 백필: row12(합계 사망자수) = row3+row6+row9 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND'
         AND c.ROW_INDEX IN (3, 6, 9) AND c.COL_INDEX = 18
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND' AND tgt.ROW_INDEX = 12 AND tgt.COL_INDEX = 18;

-- ── 3-6. row13(합계 총 발생건수) = row11(방금 계산됨) + row12(방금 계산됨)
SELECT '=== 3-6. 표8 백필: row13(합계 총 발생건수) = row11+row12 ===' AS section;

UPDATE daily_report_cell tgt
  JOIN daily_report_table tt ON tt.TABLE_ID = tgt.TABLE_ID
  JOIN (
      SELECT c.TABLE_ID,
             SUM(CASE WHEN c.CELL_VALUE IS NULL OR TRIM(REPLACE(c.CELL_VALUE, ',', '')) IN ('', '-') THEN 0
                      ELSE CAST(REPLACE(TRIM(c.CELL_VALUE), ',', '') AS DECIMAL(18, 4)) END) AS row_sum
        FROM daily_report_cell c
        JOIN daily_report_table t2 ON t2.TABLE_ID = c.TABLE_ID
       WHERE t2.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND'
         AND c.ROW_INDEX IN (11, 12) AND c.COL_INDEX = 18
       GROUP BY c.TABLE_ID
  ) agg ON agg.TABLE_ID = tgt.TABLE_ID
   SET tgt.CELL_VALUE = IF(ROUND(agg.row_sum, 1) = 0, '-',
       IF(ROUND(agg.row_sum, 1) = ROUND(agg.row_sum, 0), CAST(ROUND(agg.row_sum, 0) AS CHAR), CAST(ROUND(agg.row_sum, 1) AS CHAR)))
 WHERE tt.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND' AND tgt.ROW_INDEX = 13 AND tgt.COL_INDEX = 18;


-- ═══════════════════════════════════════════════
-- 4. 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== 4-1. 사후 검증: 표6(손실금액) 최근 10일 합계 행 소계(R열) — 백필 후 값 ===' AS section;

SELECT rp.REPORT_DATE, c.CELL_VALUE AS new_total_subtotal
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report rp ON rp.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_SAFETY_INCIDENT_AMOUNT'
   AND c.ROW_INDEX = 9
   AND c.COL_INDEX = 17
 ORDER BY rp.REPORT_DATE DESC
 LIMIT 10;

SELECT '=== 4-2. 사후 검증: 표5/6 표별 ROW_INDEX/COL_INDEX 14~17 최신 5일 표본 ===' AS section;

SELECT rp.REPORT_DATE, t.TABLE_CODE, c.ROW_INDEX, c.EXCEL_COORD, c.CELL_VALUE
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report rp ON rp.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE IN ('TBL_SAFETY_INCIDENT_COUNT', 'TBL_SAFETY_INCIDENT_AMOUNT')
   AND c.ROW_INDEX = 9
   AND c.COL_INDEX BETWEEN 14 AND 17
 ORDER BY rp.REPORT_DATE DESC, t.TABLE_CODE, c.COL_INDEX
 LIMIT 40;

SELECT '=== 4-3. 사후 검증: 표7 최신 5일 row13(합계 총 발생건수) 표본 ===' AS section;

SELECT rp.REPORT_DATE, c.CELL_VALUE
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report rp ON rp.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_SAFETY_YEARLY_TREND'
   AND c.ROW_INDEX = 13
   AND c.COL_INDEX = 11
 ORDER BY rp.REPORT_DATE DESC
 LIMIT 5;

SELECT '=== 4-4. 사후 검증: 표8 최신 5일 row13(합계 총 발생건수) 표본 ===' AS section;

SELECT rp.REPORT_DATE, c.CELL_VALUE
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report rp ON rp.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_SAFETY_MONTHLY_TREND'
   AND c.ROW_INDEX = 13
   AND c.COL_INDEX = 18
 ORDER BY rp.REPORT_DATE DESC
 LIMIT 5;

SELECT '=== 18_fix_safety_incident_forward_carryover_recompute.sql 실행 완료 ===' AS message;
