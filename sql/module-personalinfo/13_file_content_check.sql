-- ============================================================================
--  module-personalinfo : 첨부 업로드 내용 확인 결과
--  업로드할 때 서버가 파일 내용을 읽어 서류 종류(위수탁 계약서·재위탁 동의서·확약서·파기 확인서),
--  업체명, 서류 일자, 중복 파일을 확인한다. 맞지 않으면 저장하지 않는다.
--  글자를 읽을 수 없는 파일(스캔 PDF·이미지·zip)은 담당자가 '내용 직접 확인했습니다'에 체크해야 저장된다.
--
--  - pi_contract_file.CONTENT_CHECK : AUTO 자동 확인 통과 / MANUAL 담당자 직접 확인 / NULL 이 기능 이전에 올린 파일
--
--  재실행 안전: ADD COLUMN IF NOT EXISTS (MariaDB 10.0.2+). DROP / DELETE / 기존 데이터 변경 없음.
--  선행: 12_periodic_destruction.sql
-- ============================================================================

ALTER TABLE pi_contract_file
    ADD COLUMN IF NOT EXISTS CONTENT_CHECK VARCHAR(10) NULL
        COMMENT '업로드 내용 확인 결과 (AUTO 자동 확인 통과 / MANUAL 담당자 직접 확인 / NULL 확인 기능 이전 파일)' AFTER DESTRUCTION_PERIOD;

-- 확인
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pi_contract_file' AND COLUMN_NAME = 'CONTENT_CHECK';
