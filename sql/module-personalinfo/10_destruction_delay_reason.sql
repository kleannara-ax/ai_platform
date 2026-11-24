-- ============================================================================
--  module-personalinfo : 파기 지연 사유
--  - pi_contract_file.DOCUMENT_NOTE : 서류 일자 관련 메모
--      (개인정보 파기 확인서의 파기 완료일이 파기 기한(계약 종료 + 5일)보다 늦을 때 입력한 지연 사유)
--  - 파기 기한 5일 = 표준 개인정보 보호지침 제10조 ①, 확인서 회신 기한 30일은 설정값이며 DB 에 저장하지 않음
--
--  재실행 안전: ADD COLUMN IF NOT EXISTS (MariaDB 10.0.2+). DROP / DELETE / 데이터 변경 없음.
--  선행: 09_security_followup.sql (DOCUMENT_DATE 컬럼)
-- ============================================================================

ALTER TABLE pi_contract_file
    ADD COLUMN IF NOT EXISTS DOCUMENT_NOTE VARCHAR(500) NULL
        COMMENT '서류 일자 관련 메모 (개인정보 파기 확인서: 파기 기한(계약 종료 + 5일)보다 늦게 파기한 사유)' AFTER DOCUMENT_DATE;

-- 확인
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pi_contract_file' AND COLUMN_NAME = 'DOCUMENT_NOTE';
