-- ============================================================
--  module-personalinfo : 위수탁 계약서 파일별 체결일
--  MariaDB 10.11+ (utf8mb4)
--  선행: 01_schema.sql
--
--  변경 내용
--    pi_contract_file 에 SIGNED_DATE(계약서상 체결일) 컬럼 추가
--      - 위수탁 계약서(FILE_TYPE = 'CONTRACT')는 체결일자별로 여러 건 등록될 수 있다.
--      - 업로드 시 계약서 글자에서 계약일자를 읽어 저장한다
--        (체결일 표기가 없으면 계약기간 시작일). 화면에서 직접 입력·수정도 가능.
--      - 계약의 '개인정보처리 위탁계약 체결일'(pi_contract.CONTRACT_SIGNED_DATE)은
--        삭제되지 않은 위수탁 계약서 중 SIGNED_DATE 가 가장 최근인 날짜로 표시한다.
--
--  참고
--    - 이미 올라가 있는 계약서 파일은 SIGNED_DATE 가 비어 있다(SQL 로 파일 내용을 읽을 수 없음).
--      필요하면 상세 화면 첨부 목록에서 체결일을 입력한다.
--    - 기존 pi_contract.CONTRACT_SIGNED_DATE 값은 바꾸지 않는다.
--
--  재실행 안전: ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS
--  DROP / DELETE / TRUNCATE 없음, core 테이블 FK 없음
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE pi_contract_file
    ADD COLUMN IF NOT EXISTS SIGNED_DATE DATE NULL
        COMMENT '계약서상 체결일 (위수탁 계약서만 사용. 체결일 표기가 없으면 계약기간 시작일)' AFTER DESCRIPTION;

CREATE INDEX IF NOT EXISTS IDX_PI_CONTRACT_FILE_SIGNED
    ON pi_contract_file (CONTRACT_ID, FILE_TYPE, DELETED_YN, SIGNED_DATE);

-- 확인용
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pi_contract_file' AND COLUMN_NAME = 'SIGNED_DATE';
