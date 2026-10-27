-- ============================================================
--  module-personalinfo : 업체별 점검 체크리스트 업로드 지원
--  MariaDB 10.11+ (utf8mb4)
--
--  이미 01_schema.sql 을 실행한 DB 에 컬럼/인덱스를 추가한다.
--  (신규 설치는 01_schema.sql 에 포함되어 있어 이 스크립트를 실행해도 아무 변화 없음)
--
--  변경 내용
--    pi_inspection.UPLOAD_GROUP_ID  : 같은 체크리스트 1건으로 여러 계약에 등록한 묶음 ID
--    IDX_PI_INSPECTION_UPLOAD_GROUP : 묶음 조회용 인덱스
--
--  재실행 안전: ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS (MariaDB 10.0.2+)
--  기존 데이터 영향 없음: NULL 허용 컬럼 추가만 함. DROP/DELETE 없음.
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE pi_inspection
    ADD COLUMN IF NOT EXISTS UPLOAD_GROUP_ID VARCHAR(36) NULL
        COMMENT '업체별 일괄 업로드 묶음 ID (같은 체크리스트로 여러 계약에 등록 시 동일, 개별 업로드는 NULL)'
        AFTER REMARK;

CREATE INDEX IF NOT EXISTS IDX_PI_INSPECTION_UPLOAD_GROUP ON pi_inspection (UPLOAD_GROUP_ID);

-- 확인용
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pi_inspection' AND COLUMN_NAME = 'UPLOAD_GROUP_ID';
