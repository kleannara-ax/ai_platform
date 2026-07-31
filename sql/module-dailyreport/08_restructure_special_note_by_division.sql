-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 08_restructure_special_note_by_division.sql
-- 설명: ★ "특이사항"을 리포트당 1건(자유 텍스트)에서 "사업부별 5행"
--       (제지/화장지/패드/사고·안전사고/기타) 구조로 재구성한다.
--
--       [배경]
--       - 기존: daily_report_remark에 TABLE_CODE='SPECIAL', CATEGORY='특이사항'로
--         리포트당 딱 1건, CONTENT에 자유 텍스트 전체가 들어감.
--       - 변경: TABLE_CODE='TBL_SPECIAL_NOTE' (신규 가상 표코드)로 바꾸고,
--         CATEGORY를 사업부 코드(PAPER/TISSUE/PAD/SAFETY/ETC)로 세분화하여
--         리포트당 최대 5건(사업부별 1건)이 되도록 한다.
--       - 셀 시스템(daily_report_cell_auth)과 동일한 담당자 배정 방식을 쓰기
--         위해 CellAuth의 TABLE_CODE='TBL_SPECIAL_NOTE', CELL_COORDS에
--         사업부 코드(예: ["PAPER"])를 담아 재사용한다 (좌표 대신 사업부 코드).
--       - "누가 언제 저장했는지" 추적을 위해 UPDATED_BY 컬럼을 신규 추가한다
--         (기존에는 CREATED_BY만 있어 최초 작성자만 알 수 있었음).
--
--       [기존 데이터 처리]
--       - 기존에는 프론트가 항상 remarks[0](SORT_ORDER=1)만 표시/수정했고,
--         이전 버그로 SORT_ORDER=2 이상의 중복 레코드가 쌓여 있었다(사용되지
--         않는 고아 데이터). 이 스크립트는:
--         1) 리포트별 SORT_ORDER=1 레코드만 남기고 TABLE_CODE/CATEGORY를
--            새 체계(TBL_SPECIAL_NOTE / ETC)로 변경 — 기존 자유 텍스트는
--            일단 "기타(ETC)" 행으로 이전하여 데이터 손실 없이 보존한다.
--         2) 옛 TABLE_CODE='SPECIAL'로 남아있는 SORT_ORDER>=2 고아 레코드는
--            삭제한다(화면에 노출된 적 없는 버그성 중복 데이터).
--
-- 실행 순서:
--   1) "0. 사전 점검" — 현재 daily_report_remark 데이터 확인
--   2) "1. ALTER 실행" — UPDATED_BY 컬럼 추가
--   3) "2. 데이터 이전" — SPECIAL → TBL_SPECIAL_NOTE/ETC 로 이전, 고아 삭제
--   4) "3. 사후 검증" — 결과 확인
--
-- 주의: 운영 반영 전 반드시 dev/staging DB에서 먼저 실행 후 검증할 것.
--       재실행해도 안전(idempotent) — 이미 TBL_SPECIAL_NOTE로 바뀐 행은
--       WHERE TABLE_CODE='SPECIAL' 조건에서 자동 제외됨.
-- ============================================================


-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — 현재 daily_report_remark 데이터 확인
-- ═══════════════════════════════════════════════
SELECT '=== 0. 사전 점검: 현재 daily_report_remark 전체 ===' AS section;

SELECT REMARK_ID, REPORT_ID, SORT_ORDER, TABLE_CODE, CATEGORY, CREATED_BY, CREATED_AT
  FROM daily_report_remark
 ORDER BY REPORT_ID, SORT_ORDER;

SELECT '=== 0-1. UPDATED_BY 컬럼 존재 여부 확인 ===' AS section;
SELECT COLUMN_NAME, DATA_TYPE
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_report_remark'
   AND COLUMN_NAME = 'UPDATED_BY';


-- ═══════════════════════════════════════════════
-- 1. ALTER 실행 — UPDATED_BY 컬럼 추가 (최종 수정자, "누가 저장했는지" 추적용)
-- ═══════════════════════════════════════════════
SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'daily_report_remark'
     AND COLUMN_NAME = 'UPDATED_BY'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE daily_report_remark ADD COLUMN UPDATED_BY BIGINT NULL COMMENT ''최종 수정자 (core_user FK)'' AFTER CREATED_BY',
  'SELECT ''UPDATED_BY 컬럼이 이미 존재합니다 (스킵)'' AS skip_message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ═══════════════════════════════════════════════
-- 2. 데이터 이전 — SPECIAL(자유 텍스트) → TBL_SPECIAL_NOTE/ETC (사업부별 구조)
-- ═══════════════════════════════════════════════
SELECT '=== 2-1. 리포트별 SORT_ORDER=1 레코드를 ETC(기타) 행으로 이전 ===' AS section;

UPDATE daily_report_remark
   SET TABLE_CODE = 'TBL_SPECIAL_NOTE',
       CATEGORY = 'ETC',
       SORT_ORDER = 1
 WHERE TABLE_CODE = 'SPECIAL'
   AND SORT_ORDER = 1;

SELECT '=== 2-2. 옛 SPECIAL 고아 레코드(SORT_ORDER>=2, 화면 미노출) 삭제 ===' AS section;

DELETE FROM daily_report_remark
 WHERE TABLE_CODE = 'SPECIAL'
   AND SORT_ORDER >= 2;


-- ═══════════════════════════════════════════════
-- 3. 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== 3-1. UPDATED_BY 컬럼 확인 ===' AS section;
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_report_remark'
   AND COLUMN_NAME = 'UPDATED_BY';

SELECT '=== 3-2. 이전된 daily_report_remark 데이터 확인 (TABLE_CODE=SPECIAL이 더는 없어야 함) ===' AS section;
SELECT REMARK_ID, REPORT_ID, SORT_ORDER, TABLE_CODE, CATEGORY, CREATED_BY, UPDATED_BY, CREATED_AT
  FROM daily_report_remark
 ORDER BY REPORT_ID, SORT_ORDER;

SELECT '=== 다음 단계 ===' AS section;
SELECT '위 3-2 결과에 TABLE_CODE=SPECIAL이 없고, 전부 TBL_SPECIAL_NOTE면 성공.' AS step1,
       '이제 애플리케이션(app.jar)을 재빌드/재기동한 뒤,' AS step2,
       'cell-auth-admin 화면에서 특이사항(TBL_SPECIAL_NOTE) 표에 사업부별 담당자를' AS step3,
       '등록하고 세부공장일보 화면에서 저장이 정상 동작하는지 확인할 것.' AS step4;
