-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 05_convert_source_label_cells_to_data.sql
-- 설명: ★ 데이터 출처 라벨(READONLY) → 담당자 입력 가능(DATA) 전환
--
--       DefaultCellTemplate.java의 ro() 헬퍼로 "MES"/"WMS"/"DRS"/"EIS"/"SAP"
--       문자열이 하드코딩되어 있던 21개 좌표(READONLY, 잠금)를 DATA 타입으로
--       바꾸고, 담당자(OWNER_IDS)·주기(FREQ_CODE) 모두 다른 DATA 셀과 동일하게
--       미배정(NULL) 상태로 초기화한다. 이후 관리자가 '컬럼관리' 화면에서
--       CellAuth를 생성해야만 담당자/주기가 배정되고 편집 가능해진다
--       (CellService.isCellEditableForUser()가 CellAuth를 단일 판단 기준으로
--       사용하므로, 이 마이그레이션 이후에도 CellAuth 없이는 편집 불가).
--
--       ※ 이 스크립트는 코드 변경(DefaultCellTemplate.java: ro→d)과 짝을 이룬다.
--         코드 변경은 "내일부터 새로 생성되는 일보"에만 적용되므로, 이미
--         DB에 존재하는 과거/현재 날짜의 일보에 대해서는 이 UPDATE로
--         동일하게 전환해야 한다 (과거 날짜 포함 전체 일괄 적용).
--
-- 대상 좌표 (표코드 + 좌표로 정확히 특정, 총 21개):
--   TBL_PRODUCTION_INDEX : O7(DRS), O8(SAP), O12(EIS)                         → 3개
--   TBL_INVENTORY        : M21,M22,M23,M24(MES), M27,M28(WMS)                → 6개
--   TBL_ENERGY            : E36,E37,E38,E39,E40,E41,G36,G37,G38,G39,G40,G41(EIS) → 12개
--
-- 실행 순서:
--   1) "0. 사전 점검" 실행 — 전환 대상 셀 현재 상태 확인
--   2) "1. UPDATE 실행" — CELL_TYPE/IS_LOCKED/CELL_VALUE/FREQ_CODE/FREQ_LABEL/
--      OWNER_IDS/OWNER_NAMES 초기화
--   3) "2. 사후 검증" — 전환 결과 확인 (CELL_TYPE='DATA', IS_LOCKED=0 등)
--
-- 주의: 운영 반영 전 반드시 dev/staging DB에서 먼저 실행 후 검증할 것.
--       재실행해도 안전 (idempotent) — 이미 DATA로 전환된 행은 WHERE 조건에서
--       자동 제외됨 (CELL_TYPE='READONLY' 조건).
-- ============================================================


-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — 전환 대상 셀 현재 상태 확인
-- ═══════════════════════════════════════════════
SELECT '=== 0. 사전 점검: 전환 대상 셀 현재 상태 ===' AS section;

SELECT c.CELL_ID, t.TABLE_CODE, r.REPORT_DATE, c.EXCEL_COORD,
       c.CELL_TYPE, c.CELL_VALUE, c.IS_LOCKED, c.FREQ_CODE, c.OWNER_IDS
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE c.CELL_TYPE = 'READONLY'
   AND (
        (t.TABLE_CODE = 'TBL_PRODUCTION_INDEX' AND c.EXCEL_COORD IN ('O7','O8','O12'))
     OR (t.TABLE_CODE = 'TBL_INVENTORY'        AND c.EXCEL_COORD IN ('M21','M22','M23','M24','M27','M28'))
     OR (t.TABLE_CODE = 'TBL_ENERGY'           AND c.EXCEL_COORD IN ('E36','E37','E38','E39','E40','E41',
                                                                       'G36','G37','G38','G39','G40','G41'))
       )
 ORDER BY r.REPORT_DATE DESC, t.TABLE_CODE, c.EXCEL_COORD;


-- ═══════════════════════════════════════════════
-- 1. UPDATE 실행 — READONLY → DATA 전환
--    (표코드 + 좌표로 정확히 특정하여 다른 표의 동일 좌표가 잘못 걸리지 않도록 함)
-- ═══════════════════════════════════════════════
SELECT '=== 1. UPDATE 실행 ===' AS section;

UPDATE daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
   SET c.CELL_TYPE   = 'DATA',
       c.IS_LOCKED   = 0,
       c.CELL_VALUE  = '',
       c.FREQ_CODE   = NULL,
       c.FREQ_LABEL  = NULL,
       c.OWNER_IDS   = NULL,
       c.OWNER_NAMES = NULL
 WHERE c.CELL_TYPE = 'READONLY'
   AND (
        (t.TABLE_CODE = 'TBL_PRODUCTION_INDEX' AND c.EXCEL_COORD IN ('O7','O8','O12'))
     OR (t.TABLE_CODE = 'TBL_INVENTORY'        AND c.EXCEL_COORD IN ('M21','M22','M23','M24','M27','M28'))
     OR (t.TABLE_CODE = 'TBL_ENERGY'           AND c.EXCEL_COORD IN ('E36','E37','E38','E39','E40','E41',
                                                                       'G36','G37','G38','G39','G40','G41'))
       );


-- ═══════════════════════════════════════════════
-- 2. 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== 2. 전환 결과 확인 ===' AS section;

SELECT c.CELL_ID, t.TABLE_CODE, r.REPORT_DATE, c.EXCEL_COORD,
       c.CELL_TYPE, c.CELL_VALUE, c.IS_LOCKED, c.FREQ_CODE, c.OWNER_IDS
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE (
        (t.TABLE_CODE = 'TBL_PRODUCTION_INDEX' AND c.EXCEL_COORD IN ('O7','O8','O12'))
     OR (t.TABLE_CODE = 'TBL_INVENTORY'        AND c.EXCEL_COORD IN ('M21','M22','M23','M24','M27','M28'))
     OR (t.TABLE_CODE = 'TBL_ENERGY'           AND c.EXCEL_COORD IN ('E36','E37','E38','E39','E40','E41',
                                                                       'G36','G37','G38','G39','G40','G41'))
       )
 ORDER BY r.REPORT_DATE DESC, t.TABLE_CODE, c.EXCEL_COORD;

SELECT '=== 요약: CELL_TYPE별 건수 (전환 대상 좌표 기준) ===' AS section;

SELECT t.TABLE_CODE, c.CELL_TYPE, COUNT(*) AS cnt
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
 WHERE (
        (t.TABLE_CODE = 'TBL_PRODUCTION_INDEX' AND c.EXCEL_COORD IN ('O7','O8','O12'))
     OR (t.TABLE_CODE = 'TBL_INVENTORY'        AND c.EXCEL_COORD IN ('M21','M22','M23','M24','M27','M28'))
     OR (t.TABLE_CODE = 'TBL_ENERGY'           AND c.EXCEL_COORD IN ('E36','E37','E38','E39','E40','E41',
                                                                       'G36','G37','G38','G39','G40','G41'))
       )
 GROUP BY t.TABLE_CODE, c.CELL_TYPE
 ORDER BY t.TABLE_CODE, c.CELL_TYPE;

-- 다음 단계 안내
SELECT '=== 다음 단계 ===' AS section;
SELECT '위 결과에서 CELL_TYPE=DATA, IS_LOCKED=0, CELL_VALUE=(빈문자열),' AS step1,
       'FREQ_CODE=NULL, OWNER_IDS=NULL 이면 전환 성공.' AS step2,
       '이제 관리자가 컬럼관리(CellAuth) 화면에서 담당자와 주기를 배정해야' AS step3,
       '해당 셀이 실제로 편집 가능해진다 (다른 DATA 셀과 동일한 방식).' AS step4;
