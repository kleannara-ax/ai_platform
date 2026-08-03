-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 10_remark_carry_over_and_propagation.sql
-- 설명: ★ 특이사항(daily_report_remark)에 셀(daily_report_cell)과 동일한
--       "값 이어받기(carry-over) + 값 전파(forward propagation)" 기능을
--       추가하기 위한 스키마 변경 (2026-08).
--
--       [배경]
--       기존 특이사항은 일보 생성 시 5개 사업부(제지/화장지/패드/사고·안전
--       사고/기타) 행이 미리 만들어지지 않고, 사용자가 실제로 작성해야만
--       그 시점에 행이 생성되는 구조였다 — 즉, 전일 값을 이어받는 로직이
--       전혀 없었다. 이번에 셀과 동일하게:
--         1) 일보 생성 시 5개 행을 미리 만들고, 직전 일보의 내용을 이어받는다.
--         2) 저장 시, 이미 만들어져 있는 미래 일보 중 아직 사람이 손대지
--            않은(=이어받기 상태) 동일 사업부 행에도 값을 전파한다.
--
--       [수정 내용]
--       1) DB: daily_report_remark.CREATED_BY 컬럼의 NOT NULL 제약을 제거.
--          CREATED_BY가 null이면 "이어받기 상태, 아직 아무도 직접 입력한
--          적 없음"을 뜻한다(셀의 LAST_EDITOR_ID null과 동일한 개념). 기존
--          데이터는 전부 CREATED_BY가 이미 채워져 있으므로 영향 없음
--          (컬럼 제약만 완화, 데이터 UPDATE는 없음).
--       2) App(Java, 별도 배포): DailyReportRemark.updateContent()가 최초
--          저장 시 CREATED_BY를 채우도록 변경, carryOverContent() 신규 추가,
--          DailyReportService에 findPreviousRemarkValues/ensureDefaultRemarks/
--          propagateRemarkForward 신규 추가, RemarkRequest.content의
--          @NotBlank 제거(빈 이어받기 값 재전송 허용).
--
-- 실행 순서:
--   1) "0. 사전 점검" — 현재 CREATED_BY 컬럼의 NULL 허용 여부 확인
--   2) "1. ALTER 실행" — CREATED_BY NOT NULL 제약 제거
--   3) "2. 사후 검증" — 컬럼이 NULL을 허용하는지 재확인
--
-- 주의: 데이터는 전혀 변경하지 않는 순수 스키마 변경(컬럼 제약 완화)이다.
--       기존 행의 CREATED_BY 값은 그대로 유지되며, 새로 이어받기 상태로
--       만들어지는 행만 CREATED_BY=NULL로 저장된다.
--       운영 DB 반영은 담당자가 직접 검토 후 수동으로 실행해야 함
--       (이 스크립트는 sandbox/dev DB 적용을 우선 검증하는 목적으로 작성됨).
--       재실행해도 안전(idempotent) — 이미 NULL 허용 상태면 다시 실행해도
--       결과가 달라지지 않는다.
-- ============================================================


-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — CREATED_BY 컬럼의 현재 NULL 허용 여부 확인
-- ═══════════════════════════════════════════════
SELECT '=== 0. 사전 점검: daily_report_remark.CREATED_BY 컬럼 정의 ===' AS section;
SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_TYPE, COLUMN_COMMENT
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_report_remark'
   AND COLUMN_NAME = 'CREATED_BY';


-- ═══════════════════════════════════════════════
-- 1. ALTER 실행 — CREATED_BY NOT NULL 제약 제거 (NULL 허용으로 변경)
-- ═══════════════════════════════════════════════
SELECT '=== 1. ALTER 실행: CREATED_BY NULL 허용으로 변경 ===' AS section;

SET @is_nullable = (
  SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'daily_report_remark'
     AND COLUMN_NAME = 'CREATED_BY'
);
SET @sql = IF(@is_nullable = 'NO',
  'ALTER TABLE daily_report_remark MODIFY COLUMN CREATED_BY BIGINT NULL COMMENT ''최초 작성자 (core_user FK) - NULL이면 이어받기 상태(아직 사람이 직접 입력한 적 없음)''',
  'SELECT ''CREATED_BY가 이미 NULL을 허용합니다 (스킵)'' AS skip_message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ═══════════════════════════════════════════════
-- 2. 사후 검증 — CREATED_BY가 NULL을 허용하는지 재확인 (YES가 나와야 정상)
-- ═══════════════════════════════════════════════
SELECT '=== 2. 사후 검증: daily_report_remark.CREATED_BY 컬럼 정의 ===' AS section;
SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_TYPE, COLUMN_COMMENT
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_report_remark'
   AND COLUMN_NAME = 'CREATED_BY';

-- 기존 데이터는 전혀 바뀌지 않았어야 함 (참고용 — 전부 CREATED_BY가 채워져 있어야 정상)
SELECT '=== 참고: 기존 데이터 중 CREATED_BY가 NULL인 행 수 (배포 전이므로 0건이어야 정상) ===' AS section;
SELECT COUNT(*) AS null_created_by_count
  FROM daily_report_remark
 WHERE CREATED_BY IS NULL;

-- 다음 단계 안내
SELECT '=== 다음 단계 ===' AS section;
SELECT '위 2번 결과에 IS_NULLABLE=YES가 나오면 성공.' AS step1,
       '앱(app.jar)이 이미 특이사항 값 이어받기/전파 기능을 포함하도록 배포되어 있는지 확인할 것.' AS step2,
       '미래 날짜 일보를 열어 특이사항 5개 사업부 행에 전일 값이 미리 채워지는지 화면으로 확인할 것.' AS step3;
