-- ============================================================
--  module-personalinfo : 계약명 (한 업체 여러 계약 구분)
--  MariaDB 10.11+ (utf8mb4)
--  선행: 01_schema.sql
--
--  변경 내용
--    pi_contract 에 CONTRACT_NAME(계약명) 컬럼 추가
--      - 한 수탁업체가 본사·지사 등과 날짜나 업무 범위가 조금씩 다른 계약을 여러 건 맺을 수 있다.
--      - 업체명(VENDOR_NAME)은 같게, 계약명으로 각 계약을 구분한다.
--        예) 본사 물류 위탁계약 / 부산지사 물류 위탁계약 / 대전지사 반품 처리 계약
--      - 같은 업체 판단은 기존과 같이 업체명 정규화 규칙(㈜·(주)·주식회사·공백·대소문자 무시)을 쓴다.
--
--  참고
--    - 기존 계약은 계약명이 비어 있다. 화면에서는 계약명이 없으면 관련 서비스명으로 대신 보여준다.
--    - 기존 데이터는 바꾸지 않는다.
--
--  재실행 안전: ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS
--  DROP / DELETE / TRUNCATE 없음, core 테이블 FK 없음
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE pi_contract
    ADD COLUMN IF NOT EXISTS CONTRACT_NAME VARCHAR(200) NULL
        COMMENT '계약명 (한 업체의 여러 계약 구분. 예: 본사 물류 위탁계약, 부산지사 물류 위탁계약)' AFTER VENDOR_NAME;

CREATE INDEX IF NOT EXISTS IDX_PI_CONTRACT_VENDOR_NAME_CONTRACT
    ON pi_contract (VENDOR_NAME, CONTRACT_NAME);

-- 확인용
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pi_contract' AND COLUMN_NAME = 'CONTRACT_NAME';
