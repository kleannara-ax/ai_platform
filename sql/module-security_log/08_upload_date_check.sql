-- ============================================================
-- module-security_log : 08_upload_date_check.sql
-- 로그 업로드 현황: 업로드 파일의 로그 일자 점검 결과 저장
--   - 일시를 읽지 못한 줄 수 (시스템마다 로그 형식이 달라 날짜 인식이 안 되는 줄)
--   - 대상 월 밖 일자의 줄 수 (다른 달 파일을 올린 경우 확인)
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (ADD COLUMN IF NOT EXISTS)
-- 주의    : DROP / TRUNCATE / DELETE / ALTER DROP COLUMN 없음, core 테이블 FK 없음
-- 선행    : 01 ~ 07
-- 참고    : 이 스크립트 적용 전 업로드는 두 값이 NULL(점검 전)입니다.
--           대시보드의 [로그 일자 점검 다시 하기](ADMIN) 또는 재분석으로 채울 수 있습니다.
--           (탐지·검토 결과는 바뀌지 않음)
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE sec_log_upload
    ADD COLUMN IF NOT EXISTS DATE_UNKNOWN_COUNT INT NULL
        COMMENT '일시를 읽지 못한 로그 줄 수 (NULL 이면 로그 일자 점검 전)' AFTER LOW_COUNT;

ALTER TABLE sec_log_upload
    ADD COLUMN IF NOT EXISTS OUT_OF_MONTH_COUNT INT NULL
        COMMENT '대상 월 밖 일자의 로그 줄 수 (NULL 이면 로그 일자 점검 전)' AFTER DATE_UNKNOWN_COUNT;
