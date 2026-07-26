-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 07_merge_inventory_remark_header.sql
-- 설명: ★ "2. 제지 재공품 및 야적현황" 표의 "비고" 헤더를 아래 행과 세로 병합
--
--       DefaultCellTemplate.java에서 N19("비고", Row0)와 N20(빈 헤더, Row1)을
--       각각 별도 1×1 헤더 셀로 생성하던 것을, N19를 rowSpan=2로 병합하고
--       N20은 생성하지 않도록 코드를 변경했다.
--
--       ※ 이 스크립트는 코드 변경(DefaultCellTemplate.java)과 짝을 이룬다.
--         코드 변경은 "새로 생성되는 일보"에만 적용되므로, 이미 DB에 존재하는
--         과거/현재 날짜의 TBL_INVENTORY 표에 대해서는 이 스크립트로 동일하게
--         맞춰야 한다 (과거 날짜 포함 전체 일괄 적용).
--
-- 대상: daily_report_cell 중 TABLE_CODE='TBL_INVENTORY' AND EXCEL_COORD IN ('N19','N20')
--   - N19: ROW_SPAN 1 → 2 로 UPDATE (텍스트 값 "비 고"는 그대로 유지)
--   - N20: DELETE (원래 항상 빈 값(NULL)이었던 헤더 자리표시용 셀 — 데이터 손실 없음)
--
-- 영향 없음: 사용자가 입력한 DATA 셀 값은 이 스크립트에서 전혀 건드리지 않음
--           (WHERE 조건이 HEADER 타입 좌표 N19/N20으로만 정확히 한정됨)
--
-- 실행 순서:
--   1) "0. 사전 점검" — 변경 대상 셀 현재 상태 확인
--   2) "1. UPDATE 실행" — N19의 ROW_SPAN을 2로 변경
--   3) "2. DELETE 실행" — N20 삭제
--   4) "3. 사후 검증" — 변경 결과 확인
--
-- 주의: 운영 반영 전 반드시 dev/staging DB에서 먼저 실행 후 검증할 것.
--       재실행해도 안전 (idempotent) — 이미 ROW_SPAN=2로 바뀐 N19나 이미
--       삭제된 N20은 WHERE 조건에서 자동 제외됨.
-- ============================================================


-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — 변경 대상 셀 현재 상태 확인
-- ═══════════════════════════════════════════════
SELECT '=== 0. 사전 점검: 변경 대상 셀 현재 상태 ===' AS section;

SELECT c.CELL_ID, t.TABLE_CODE, r.REPORT_DATE, c.EXCEL_COORD,
       c.ROW_INDEX, c.COL_INDEX, c.ROW_SPAN, c.CELL_VALUE, c.CELL_TYPE
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_INVENTORY'
   AND c.EXCEL_COORD IN ('N19', 'N20')
 ORDER BY r.REPORT_DATE DESC, c.EXCEL_COORD;


-- ═══════════════════════════════════════════════
-- 1. UPDATE 실행 — N19 ROW_SPAN 1 → 2
-- ═══════════════════════════════════════════════
SELECT '=== 1. UPDATE 실행: N19 ROW_SPAN → 2 ===' AS section;

UPDATE daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
   SET c.ROW_SPAN = 2
 WHERE t.TABLE_CODE = 'TBL_INVENTORY'
   AND c.EXCEL_COORD = 'N19'
   AND c.ROW_SPAN <> 2;


-- ═══════════════════════════════════════════════
-- 2. DELETE 실행 — N20 삭제 (N19에 병합되어 흡수됨)
-- ═══════════════════════════════════════════════
SELECT '=== 2. DELETE 실행: N20 삭제 ===' AS section;

DELETE c
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
 WHERE t.TABLE_CODE = 'TBL_INVENTORY'
   AND c.EXCEL_COORD = 'N20';


-- ═══════════════════════════════════════════════
-- 3. 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== 3. 변경 결과 확인 (N20은 조회되지 않아야 함) ===' AS section;

SELECT c.CELL_ID, t.TABLE_CODE, r.REPORT_DATE, c.EXCEL_COORD,
       c.ROW_INDEX, c.COL_INDEX, c.ROW_SPAN, c.CELL_VALUE, c.CELL_TYPE
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_INVENTORY'
   AND c.EXCEL_COORD IN ('N19', 'N20')
 ORDER BY r.REPORT_DATE DESC, c.EXCEL_COORD;

SELECT '=== 다음 단계 ===' AS section;
SELECT '위 결과에서 N19만 조회되고 ROW_SPAN=2이면 성공.' AS step1,
       'N20 행이 하나도 조회되지 않아야 정상.' AS step2;
