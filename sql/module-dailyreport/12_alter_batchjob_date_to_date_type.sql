-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 12_alter_batchjob_date_to_date_type.sql
-- 설명: ★ daily_batchjob.BATCH_DATE 컬럼 타입 통일 (2026-08)
--
--       [배경]
--       daily_batchjob은 11_add_daily_batchjob.sql에서 최초 생성 시
--       BATCH_DATE를 VARCHAR(8)('YYYYMMDD' 문자열)로 만들었다. 그런데
--       이 값의 원본인 daily_report.REPORT_DATE는 DATE 타입이다.
--       daily_batchjob의 스키마(엑셀 스펙)는 담당자가 임의로 정한 것이라
--       배치 시스템과의 고정 계약이 아니므로, 내부 컨벤션과 통일하기 위해
--       BATCH_DATE를 VARCHAR(8) → DATE로 변경한다.
--
--       ※ 이 스크립트는 11_add_daily_batchjob.sql로 이미 테이블을 생성해
--         실행한 환경(예: 이미 BATCH_DATE='20260812' 같은 값이 들어있는
--         경우)에 적용하는 "마이그레이션" 스크립트다. 아직 daily_batchjob을
--         한 번도 만든 적이 없는 환경이라면 이 스크립트는 필요 없고,
--         01_schema.sql / 11_add_daily_batchjob.sql이 이미 DATE 타입으로
--         테이블을 생성하므로 그냥 그걸 실행하면 된다.
--
-- 실행 순서:
--   1) "0. 사전 점검" — 현재 BATCH_DATE 컬럼 타입 및 데이터 확인
--   2) "1. ALTER TABLE" — VARCHAR(8) 'YYYYMMDD' 값을 DATE로 변환하며 타입 변경
--   3) "2. 사후 검증" — 변경된 컬럼 타입 및 데이터 확인
--
-- 주의: MySQL/MariaDB의 ALTER TABLE ... MODIFY COLUMN은 기존 데이터를
--       새 타입으로 자동 변환한다. VARCHAR(8) 'YYYYMMDD' 형식은
--       STR_TO_DATE로 명시적으로 변환해야 안전하므로, 아래 스크립트는
--       "임시 컬럼 추가 → 값 변환 복사 → 기존 컬럼 삭제 → 컬럼명 변경"
--       방식을 사용한다(운영 데이터 안전 우선).
-- ============================================================

-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — 현재 BATCH_DATE 컬럼 타입 및 데이터 확인
-- ═══════════════════════════════════════════════
SELECT '=== 0. 사전 점검: 현재 BATCH_DATE 컬럼 타입 ===' AS section;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_batchjob'
   AND COLUMN_NAME = 'BATCH_DATE';

SELECT SEQ_NO, BATCH_DATE, BATCH_TYPE, CREATE_YN, CREATED_AT
  FROM daily_batchjob
 ORDER BY SEQ_NO;


-- ═══════════════════════════════════════════════
-- 1. ALTER TABLE — BATCH_DATE VARCHAR(8) 'YYYYMMDD' → DATE
-- ═══════════════════════════════════════════════
SELECT '=== 1. ALTER TABLE 실행: BATCH_DATE → DATE ===' AS section;

-- 1-1) 임시 컬럼 추가
ALTER TABLE daily_batchjob
    ADD COLUMN BATCH_DATE_NEW DATE NULL COMMENT '일자 (일보 대상 날짜, daily_report.REPORT_DATE와 동일 규칙)' AFTER BATCH_DATE;

-- 1-2) 기존 'YYYYMMDD' 문자열 값을 DATE로 변환하여 복사
UPDATE daily_batchjob
   SET BATCH_DATE_NEW = STR_TO_DATE(BATCH_DATE, '%Y%m%d')
 WHERE BATCH_DATE IS NOT NULL;

-- 1-3) 변환 결과에 NULL이 없는지 확인 (혹시 형식이 다른 값이 있었다면 여기서 걸러짐)
SELECT '=== 1-3. 변환 실패(NULL) 행 확인 — 0건이어야 정상 ===' AS section;
SELECT SEQ_NO, BATCH_DATE, BATCH_DATE_NEW
  FROM daily_batchjob
 WHERE BATCH_DATE_NEW IS NULL;

-- 1-4) 기존 컬럼 삭제 후 새 컬럼을 BATCH_DATE로 이름 변경 + NOT NULL 확정
ALTER TABLE daily_batchjob
    DROP COLUMN BATCH_DATE;

ALTER TABLE daily_batchjob
    CHANGE COLUMN BATCH_DATE_NEW BATCH_DATE DATE NOT NULL COMMENT '일자 (일보 대상 날짜, daily_report.REPORT_DATE와 동일 규칙)';

-- 1-5) 인덱스 재생성 (컬럼 삭제 시 함께 삭제되었을 수 있으므로 존재 여부 확인 후 생성)
-- MariaDB는 DROP COLUMN 시 그 컬럼만 포함된 인덱스를 자동 삭제하므로 재생성 필요
ALTER TABLE daily_batchjob
    ADD INDEX IDX_BATCHJOB_DATE (BATCH_DATE);


-- ═══════════════════════════════════════════════
-- 2. 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== 2. 사후 검증: 변경된 BATCH_DATE 컬럼 타입 ===' AS section;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_batchjob'
   AND COLUMN_NAME = 'BATCH_DATE';

SELECT SEQ_NO, BATCH_DATE, BATCH_TYPE, CREATE_YN, CREATED_AT
  FROM daily_batchjob
 ORDER BY SEQ_NO;

SELECT INDEX_NAME, COLUMN_NAME
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_batchjob'
 ORDER BY INDEX_NAME, SEQ_IN_INDEX;

SELECT '=== 12_alter_batchjob_date_to_date_type.sql 실행 완료 ===' AS message;
