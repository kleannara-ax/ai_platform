-- ============================================================
-- module-security_log : 20_dlp_org.sql
-- DLP 관리 > 사업부·부서 관리 - 카카오톡 사용 승인 대장 입력 드롭다운 목록
--   - 사업부 = PARENT_ORG_ID 0, 부서 = PARENT_ORG_ID 사업부 ID
--   - 안 쓰는 항목은 지우지 않고 USE_YN = 'N' (사용 안 함) - 드롭다운에서만 빠짐
--   - 처음 목록 = 지금 승인 대장에 들어 있는 사업부·부서 (이름 그대로)
--   - 부서 이름은 DLP 유출 로그의 부서 이름과 글자까지 같아야 보고서에서 같은 사람으로 봄
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS, INSERT IGNORE + UK_SEC_LOG_DLP_ORG_NAME)
--           화면에서 바꾼 목록은 덮어쓰지 않음
-- 주의    : DROP / TRUNCATE / DELETE / ALTER 없음, FK 없음 (core 테이블 참조 안 함)
-- 선행    : 18_dlp_kakao_approval.sql (처음 목록을 승인 대장에서 가져옴)
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sec_log_dlp_org (
    ORG_ID          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '사업부·부서 ID',
    PARENT_ORG_ID   BIGINT        NOT NULL DEFAULT 0      COMMENT '상위 사업부 ID (0 = 사업부, 그 외 = 이 사업부 아래 부서)',
    ORG_NAME        VARCHAR(200)  NOT NULL                COMMENT '사업부 또는 부서 이름 (DLP 유출 로그의 부서 이름과 같게)',
    USE_YN          CHAR(1)       NOT NULL DEFAULT 'Y'    COMMENT '사용 여부 (Y = 드롭다운에 표시 / N = 사용 안 함, 기존 기록은 그대로)',

    CREATED_AT      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY      BIGINT        NULL                    COMMENT '생성자 ID (core 사용자 ID 값, FK 없음)',
    UPDATED_AT      DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY      BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN      CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제 - 화면에서는 삭제 대신 사용 안 함)',
    DELETED_AT      DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY      BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (ORG_ID),
    UNIQUE KEY UK_SEC_LOG_DLP_ORG_NAME (PARENT_ORG_ID, ORG_NAME),
    INDEX IDX_SEC_LOG_DLP_ORG_USE (DELETED_YN, USE_YN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 DLP 관리 - 사업부·부서 목록 (카카오톡 승인 대장 드롭다운)';

-- 처음 목록: 승인 대장에 있는 사업부
INSERT IGNORE INTO sec_log_dlp_org (PARENT_ORG_ID, ORG_NAME, USE_YN)
SELECT DISTINCT 0, TRIM(a.DIVISION_NAME), 'Y'
  FROM sec_log_dlp_kakao_approval a
 WHERE a.DELETED_YN = 'N' AND a.DIVISION_NAME IS NOT NULL AND TRIM(a.DIVISION_NAME) <> '';

-- 처음 목록: 승인 대장에 있는 부서 (그 사업부 아래)
INSERT IGNORE INTO sec_log_dlp_org (PARENT_ORG_ID, ORG_NAME, USE_YN)
SELECT DISTINCT d.ORG_ID, TRIM(a.DEPT_NAME), 'Y'
  FROM sec_log_dlp_kakao_approval a
  JOIN sec_log_dlp_org d ON d.PARENT_ORG_ID = 0 AND d.ORG_NAME = TRIM(a.DIVISION_NAME) AND d.DELETED_YN = 'N'
 WHERE a.DELETED_YN = 'N' AND a.DEPT_NAME IS NOT NULL AND TRIM(a.DEPT_NAME) <> '';
