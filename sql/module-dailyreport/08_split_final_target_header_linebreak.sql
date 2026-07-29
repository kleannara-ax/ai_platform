-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 08_split_final_target_header_linebreak.sql
-- 설명: ★ "1. 주요 생산 지표 현황" 표의 병합된 "최종목표"(E5, rowSpan=2)
--       헤더 텍스트를 셀 안에서 "최종" / "목표" 두 줄로 줄바꿈되도록 변경
--
--       이전 작업(07번 이전 라운드)에서 E5/E6 두 개의 별도 헤더 셀
--       ("최종", "목표")을 E5 하나(rowSpan=2, 텍스트 "최종목표")로 병합했었음.
--       이번 요청: 병합은 유지하되, 셀 안에서 "최종"과 "목표"가 줄바꿈되어
--       2줄로 보이도록 값 자체를 "최종\n목표"(개행 문자 포함)로 변경.
--       프론트엔드(index.html)는 이미 CELL_VALUE 안의 \n을 <br>로 치환해
--       렌더링하는 로직(replace(/\n/g, '<br>'))을 갖고 있으므로 별도 프론트 수정 불필요.
--
--       ※ 이 스크립트는 코드 변경(DefaultCellTemplate.java)과 짝을 이룬다.
--         코드 변경은 "새로 생성되는 일보"에만 적용되므로, 이미 DB에 존재하는
--         과거/현재 날짜의 TBL_PRODUCTION_INDEX 표에 대해서는 이 스크립트로
--         동일하게 맞춰야 한다 (과거 날짜 포함 전체 일괄 적용).
--
-- 대상: daily_report_cell 중 TABLE_CODE='TBL_PRODUCTION_INDEX' AND EXCEL_COORD='E5'
--   - CELL_VALUE: '최종목표' → '최종\n목표' (개행 문자 포함, MySQL에서는 CHAR(10) 사용)
--   - CELL_LABEL도 동일하게 변경 (E5의 CELL_LABEL도 헤더 표시 텍스트와 동일하게 유지되어 왔음)
--   - ROW_SPAN은 이미 2로 병합되어 있으므로 변경하지 않음
--
-- 영향 없음: 사용자가 입력한 DATA 셀 값은 이 스크립트에서 전혀 건드리지 않음
--           (WHERE 조건이 HEADER 타입 좌표 E5로만 정확히 한정됨)
--
-- 실행 순서:
--   1) "0. 사전 점검" — 변경 대상 셀 현재 상태 확인
--   2) "1. UPDATE 실행" — E5의 CELL_VALUE/CELL_LABEL에 개행 문자 삽입
--   3) "2. 사후 검증" — 변경 결과 확인 (HEX로 개행 문자 포함 여부 확인)
--
-- 주의: 운영 반영 전 반드시 dev/staging DB에서 먼저 실행 후 검증할 것.
--       재실행해도 안전 (idempotent) — 이미 개행이 포함된 값은 WHERE 조건에서 자동 제외됨.
-- ============================================================


-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — 변경 대상 셀 현재 상태 확인
-- ═══════════════════════════════════════════════
SELECT '=== 0. 사전 점검: 변경 대상 셀 현재 상태 ===' AS section;

SELECT c.CELL_ID, t.TABLE_CODE, r.REPORT_DATE, c.EXCEL_COORD,
       c.ROW_INDEX, c.COL_INDEX, c.ROW_SPAN, c.CELL_VALUE, HEX(c.CELL_VALUE) AS hex_value
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.EXCEL_COORD = 'E5'
 ORDER BY r.REPORT_DATE DESC;


-- ═══════════════════════════════════════════════
-- 1. UPDATE 실행 — E5 CELL_VALUE/CELL_LABEL에 개행 문자 삽입
-- ═══════════════════════════════════════════════
SELECT '=== 1. UPDATE 실행: E5 텍스트 "최종\n목표"로 변경 ===' AS section;

UPDATE daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
   SET c.CELL_VALUE = CONCAT('최종', CHAR(10), '목표'),
       c.CELL_LABEL = CONCAT('최종', CHAR(10), '목표')
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.EXCEL_COORD = 'E5'
   AND c.CELL_VALUE NOT LIKE CONCAT('%', CHAR(10), '%');


-- ═══════════════════════════════════════════════
-- 2. 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== 2. 사후 검증: 변경 결과 확인 ===' AS section;

SELECT c.CELL_ID, t.TABLE_CODE, r.REPORT_DATE, c.EXCEL_COORD,
       c.ROW_SPAN, c.CELL_VALUE, HEX(c.CELL_VALUE) AS hex_value
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE t.TABLE_CODE = 'TBL_PRODUCTION_INDEX'
   AND c.EXCEL_COORD = 'E5'
 ORDER BY r.REPORT_DATE DESC;
