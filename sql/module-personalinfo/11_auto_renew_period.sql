-- ============================================================================
--  module-personalinfo : 자동연장 기간
--  - pi_contract.AUTO_RENEW_PERIOD_MONTHS : 자동연장 기간 (개월, 기본 12 = 1년 단위)
--      원계약 종료일이 지나도 자동연장 조건(AUTO_RENEW_YN = 'Y')에 따라 추가 계약 없이 이 기간 단위로
--      계약기간이 이어진다. 자동연장 Y + 미종료 계약은 만료임박·만료·만료 서류·만료 알림 대상에서 빠지고,
--      종료 처리해야 만료 대상이 된다. (현재 종료일 = 원계약 종료일 + 연장 횟수 × 이 기간, 화면에서 계산)
--  - 기존 계약은 기본값 12(1년)로 채워진다. 업무 데이터 변경·삭제 없음.
--
--  재실행 안전: ADD COLUMN IF NOT EXISTS (MariaDB 10.0.2+). DROP / DELETE 없음.
-- ============================================================================

ALTER TABLE pi_contract
    ADD COLUMN IF NOT EXISTS AUTO_RENEW_PERIOD_MONTHS INT NOT NULL DEFAULT 12
        COMMENT '자동연장 기간 (개월, 기본 12 = 1년 단위). 자동연장 Y 이면 원계약 종료일 후 이 기간 단위로 계약기간이 자동 연장됨' AFTER AUTO_RENEW_NOTE;

-- 확인
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pi_contract' AND COLUMN_NAME = 'AUTO_RENEW_PERIOD_MONTHS';
