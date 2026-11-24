-- ============================================================================
--  module-personalinfo : 정기 파기확인 (월 / 분기 / 반기)
--  이벤트 대행처럼 계약기간 중에도 주기적으로 개인정보를 파기하는 협력사에게서
--  기간마다 '개인정보 파기 확인서'를 받아 관리한다.
--
--  - pi_contract.PERIODIC_DESTRUCTION_YN    : 정기 파기확인 대상 여부 (Y/N, 기본 N)
--  - pi_contract.PERIODIC_DESTRUCTION_CYCLE : 주기 (MONTH 월 / QUARTER 분기 / HALF 반기, 기본 QUARTER)
--  - pi_contract.PERIODIC_DESTRUCTION_MAIL_YN : 요청·독촉 메일 발송 여부 (Y/N, 기본 Y)
--  - pi_contract.PERIODIC_DESTRUCTION_FROM  : 관리 시작일 (정기 파기확인을 체크한 날).
--      이 날 이후에 끝나는 기간부터 미수취를 따진다. 그 전 기간도 확인서 업로드는 할 수 있다.
--  - pi_contract_file.DESTRUCTION_PERIOD    : 정기 파기 확인서의 대상 기간
--      (예: 2026-07 / 2026-Q3 / 2026-H2). NULL 이면 계약 종료 시 받는 파기 확인서(만료 서류).
--  - 수취 기한(기간 종료 + 20일)은 설정값(personalinfo.periodic-destruction.reply-days)이며 DB 에 저장하지 않는다.
--
--  재실행 안전: ADD COLUMN IF NOT EXISTS (MariaDB 10.0.2+). DROP / DELETE / 기존 데이터 변경 없음.
--  선행: 10_destruction_delay_reason.sql (DOCUMENT_NOTE 컬럼), 11_auto_renew_period.sql
-- ============================================================================

ALTER TABLE pi_contract
    ADD COLUMN IF NOT EXISTS PERIODIC_DESTRUCTION_YN CHAR(1) NOT NULL DEFAULT 'N'
        COMMENT '정기 파기확인 대상 여부 (Y/N). Y 면 주기마다 개인정보 파기 확인서를 받아 관리' AFTER AUTO_RENEW_PERIOD_MONTHS,
    ADD COLUMN IF NOT EXISTS PERIODIC_DESTRUCTION_CYCLE VARCHAR(10) NOT NULL DEFAULT 'QUARTER'
        COMMENT '정기 파기확인 주기 (MONTH 월 / QUARTER 분기 / HALF 반기)' AFTER PERIODIC_DESTRUCTION_YN,
    ADD COLUMN IF NOT EXISTS PERIODIC_DESTRUCTION_MAIL_YN CHAR(1) NOT NULL DEFAULT 'Y'
        COMMENT '정기 파기확인 요청·독촉 메일 발송 여부 (Y/N)' AFTER PERIODIC_DESTRUCTION_CYCLE,
    ADD COLUMN IF NOT EXISTS PERIODIC_DESTRUCTION_FROM DATE NULL
        COMMENT '정기 파기확인 관리 시작일 (체크한 날). 이 날 이후에 끝나는 기간부터 미수취 판정' AFTER PERIODIC_DESTRUCTION_MAIL_YN;

ALTER TABLE pi_contract_file
    ADD COLUMN IF NOT EXISTS DESTRUCTION_PERIOD VARCHAR(10) NULL
        COMMENT '정기 파기 확인서 대상 기간 (예: 2026-07 월 / 2026-Q3 분기 / 2026-H2 반기). NULL 이면 계약 종료 파기 확인서' AFTER DOCUMENT_NOTE;

-- 확인
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ((TABLE_NAME = 'pi_contract' AND COLUMN_NAME LIKE 'PERIODIC_DESTRUCTION%')
     OR (TABLE_NAME = 'pi_contract_file' AND COLUMN_NAME = 'DESTRUCTION_PERIOD'));
