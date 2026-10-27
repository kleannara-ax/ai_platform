-- ============================================================
--  module-personalinfo : 협력사 담당자 이력 — 위탁사 담당자 기준 + 계약기간 표시
--  MariaDB 10.11+ (utf8mb4)
--  선행: 04_contact_history.sql
--
--  변경 내용
--    1) pi_contact_history 에 이력 기록 시점의 계약기간 컬럼 3개 추가
--         CONTRACT_START_DATE / CONTRACT_END_DATE / CONTRACT_PERIOD_NOTE
--    2) 계약기간이 비어 있는 기존 이력 행은 현재 계약의 계약기간으로 채움
--       (04 로 만든 '기존 데이터 이관'·이전 이력은 기록 당시 계약기간이 없으므로 현재 값으로 보정)
--
--  참고
--    - 이력 대상은 위탁사(당사) 담당부서·담당자·이메일 뿐이다. 수탁사 담당자·연락처 변경은 이력을 남기지 않는다.
--    - 04 의 VENDOR_CONTACT_NAME / VENDOR_CONTACT_EMAIL / VENDOR_CONTACT_PHONE 컬럼은 더 이상 기록하지 않는다.
--      (DROP COLUMN 금지 규칙에 따라 컬럼은 남겨두며, 기존 값은 조회 화면에 노출하지 않음)
--    - 수탁사 항목만 바뀌어 기록되었던 이전 '담당자 변경' 행은 화면/API 에서 자동으로 제외된다(삭제하지 않음).
--
--  재실행 안전: ADD COLUMN IF NOT EXISTS / UPDATE 는 값이 NULL 인 행만 대상
--  DROP / DELETE / TRUNCATE 없음
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE pi_contact_history
    ADD COLUMN IF NOT EXISTS CONTRACT_START_DATE  DATE         NULL COMMENT '이력 기록 시점 계약 시작일' AFTER SERVICE_NAME,
    ADD COLUMN IF NOT EXISTS CONTRACT_END_DATE    DATE         NULL COMMENT '이력 기록 시점 계약 종료일' AFTER CONTRACT_START_DATE,
    ADD COLUMN IF NOT EXISTS CONTRACT_PERIOD_NOTE VARCHAR(500) NULL COMMENT '이력 기록 시점 계약기간 비고' AFTER CONTRACT_END_DATE;

-- 계약기간이 하나도 없는 기존 이력만 현재 계약 값으로 보정 (재실행 시 이미 채워진 행은 건드리지 않음)
UPDATE pi_contact_history h
  JOIN pi_contract c ON c.CONTRACT_ID = h.CONTRACT_ID
   SET h.CONTRACT_START_DATE  = c.CONTRACT_START_DATE,
       h.CONTRACT_END_DATE    = c.CONTRACT_END_DATE,
       h.CONTRACT_PERIOD_NOTE = c.CONTRACT_PERIOD_NOTE
 WHERE h.CONTRACT_START_DATE IS NULL
   AND h.CONTRACT_END_DATE IS NULL
   AND h.CONTRACT_PERIOD_NOTE IS NULL;

-- 확인용
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pi_contact_history'
   AND COLUMN_NAME IN ('CONTRACT_START_DATE', 'CONTRACT_END_DATE', 'CONTRACT_PERIOD_NOTE');
