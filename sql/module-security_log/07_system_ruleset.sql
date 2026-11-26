-- ============================================================
-- module-security_log : 07_system_ruleset.sql
-- 시스템별 룰셋: 공통 룰셋 + 시스템 전용 룰셋, 시스템마다 룰셋 사용/미사용
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (ADD COLUMN IF NOT EXISTS, CREATE INDEX IF NOT EXISTS)
-- 주의    : DROP / TRUNCATE / DELETE / ALTER DROP COLUMN 없음, core 테이블 FK 없음
--           로그 수집은 월 1회 파일 업로드 방식 (API 수집 사용 안 함)
--           → 05 의 sec_log_collector 테이블은 더 이상 쓰지 않음 (데이터 보존, 삭제하지 않음)
-- 선행    : 01 ~ 06
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 룰: 시스템 전용 룰 구분 (NULL = 공통 룰, 값 = 해당 시스템 전용 룰)
-- ------------------------------------------------------------
ALTER TABLE sec_log_rule
    ADD COLUMN IF NOT EXISTS SYSTEM_ID BIGINT NULL
        COMMENT '시스템 전용 룰의 점검 대상 시스템 ID (NULL 이면 공통 룰: 로그 유형으로 적용)' AFTER RULE_ID;

CREATE INDEX IF NOT EXISTS IDX_SEC_LOG_RULE_SYSTEM ON sec_log_rule (SYSTEM_ID, DELETED_YN, USE_YN);

-- ------------------------------------------------------------
-- 2. 시스템: 룰셋 사용 여부
-- ------------------------------------------------------------
ALTER TABLE sec_log_system
    ADD COLUMN IF NOT EXISTS COMMON_RULE_YN CHAR(1) NOT NULL DEFAULT 'Y'
        COMMENT '공통 룰셋 적용 여부 (Y/N)' AFTER INACTIVE_DAYS;
ALTER TABLE sec_log_system
    ADD COLUMN IF NOT EXISTS SYSTEM_RULE_YN CHAR(1) NOT NULL DEFAULT 'Y'
        COMMENT '시스템 전용 룰셋 적용 여부 (Y/N)' AFTER COMMON_RULE_YN;
