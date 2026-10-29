-- ============================================================
-- module-security_log : 03_system_access_account.sql
-- 점검 대상 시스템 마스터 / 접속 행위 분석 / 장기 미접속 계정 점검
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS, ADD COLUMN IF NOT EXISTS,
--           CREATE INDEX IF NOT EXISTS, INSERT IGNORE)
-- 주의    : DROP / TRUNCATE / DELETE / ALTER DROP COLUMN 없음
--           core 테이블 FK 없음 (사용자 ID 는 값만 저장)
-- 선행    : 01_schema.sql, 02_seed_data.sql
-- 생성순서: sec_log_system → (sec_log_upload.SYSTEM_ID 추가)
--           → sec_log_access_summary → sec_log_account_check → sec_log_account
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 5. 점검 대상 시스템 마스터
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_system (
    SYSTEM_ID         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '시스템 ID',
    SYSTEM_CODE       VARCHAR(50)   NOT NULL                COMMENT '시스템 코드 (영문대문자/숫자/_ , 변경 불가)',
    SYSTEM_NAME       VARCHAR(100)  NOT NULL                COMMENT '시스템명',
    CATEGORY          VARCHAR(50)   NOT NULL                COMMENT '구분 (서비스/정보보호시스템/네트워크/DBMS 등)',
    DEFAULT_LOG_TYPE  VARCHAR(20)   NOT NULL DEFAULT 'ETC'  COMMENT '기본 로그 유형 (LINUX/WINDOWS/WEB/WAS/DB/NETWORK/APP/SECURITY/CLOUD/ETC)',
    MANAGER_NAME      VARCHAR(100)  NULL                    COMMENT '시스템 담당자명 (표시용)',
    INACTIVE_DAYS     INT           NOT NULL DEFAULT 90     COMMENT '장기 미접속 판단 기준 일수',
    DESCRIPTION       VARCHAR(500)  NULL                    COMMENT '시스템 설명 / 로그 추출 방법',
    USE_YN            CHAR(1)       NOT NULL DEFAULT 'Y'    COMMENT '사용 여부 (Y/N)',
    SORT_ORDER        INT           NOT NULL DEFAULT 0      COMMENT '정렬 순서',

    CREATED_AT        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY        BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT        DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY        BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN        CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT        DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY        BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (SYSTEM_ID),
    CONSTRAINT UK_SEC_LOG_SYSTEM_CODE UNIQUE (SYSTEM_CODE),
    INDEX IDX_SEC_LOG_SYSTEM_ACTIVE (DELETED_YN, USE_YN, SORT_ORDER)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 점검 대상 시스템 마스터';

-- ------------------------------------------------------------
-- 2-1. sec_log_upload 에 시스템 ID 연결 컬럼 추가 (기존 데이터는 NULL)
-- ------------------------------------------------------------
ALTER TABLE sec_log_upload
    ADD COLUMN IF NOT EXISTS SYSTEM_ID BIGINT NULL COMMENT '점검 대상 시스템 ID (sec_log_system, NULL 이면 직접 입력 시스템)' AFTER TARGET_YM;

CREATE INDEX IF NOT EXISTS IDX_SEC_LOG_UPLOAD_SYSTEM_ID ON sec_log_upload (TARGET_YM, SYSTEM_ID, DELETED_YN);

-- ------------------------------------------------------------
-- 6. 접속 행위 분석 결과 (업로드별 계정 단위 집계)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_access_summary (
    SUMMARY_ID         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '접속 집계 ID',
    UPLOAD_ID          BIGINT        NOT NULL                COMMENT '업로드 ID',
    ACCOUNT_ID         VARCHAR(200)  NOT NULL                COMMENT '접속 계정 ID',
    SUCCESS_COUNT      INT           NOT NULL DEFAULT 0      COMMENT '로그인 성공 건수',
    FAIL_COUNT         INT           NOT NULL DEFAULT 0      COMMENT '로그인 실패 건수',
    OFF_HOUR_COUNT     INT           NOT NULL DEFAULT 0      COMMENT '업무시간 외(22~06시) 로그인 성공 건수',
    EXTERNAL_IP_COUNT  INT           NOT NULL DEFAULT 0      COMMENT '외부(비사설) IP 접속 건수',
    IP_COUNT           INT           NOT NULL DEFAULT 0      COMMENT '접속 IP 종류 수',
    IP_LIST            VARCHAR(1000) NULL                    COMMENT '접속 IP 목록 (쉼표 구분, 최대 1000자)',
    FIRST_SEEN         VARCHAR(50)   NULL                    COMMENT '최초 접속 시각 (로그 원문 표기)',
    LAST_SEEN          VARCHAR(50)   NULL                    COMMENT '최종 접속 시각 (로그 원문 표기)',
    RISK_FLAGS         VARCHAR(200)  NULL                    COMMENT '위험 표시 (FAIL_MANY/OFF_HOUR/EXTERNAL_IP/MULTI_IP/PRIV_ACCOUNT, 쉼표 구분)',

    CREATED_AT         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY         BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT         DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY         BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN         CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT         DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY         BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (SUMMARY_ID),
    INDEX IDX_SEC_LOG_ACCESS_UPLOAD (UPLOAD_ID, DELETED_YN),
    INDEX IDX_SEC_LOG_ACCESS_ACCOUNT (ACCOUNT_ID),
    CONSTRAINT FK_SEC_LOG_ACCESS_UPLOAD FOREIGN KEY (UPLOAD_ID) REFERENCES sec_log_upload (UPLOAD_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 접속 행위 분석 결과 (계정별 집계)';

-- ------------------------------------------------------------
-- 7. 장기 미접속 계정 점검 (시스템 계정 목록 업로드 단위)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_account_check (
    CHECK_ID            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '계정 점검 ID',
    TARGET_YM           CHAR(6)       NOT NULL                COMMENT '대상 연월 (YYYYMM)',
    SYSTEM_ID           BIGINT        NOT NULL                COMMENT '점검 대상 시스템 ID',
    SYSTEM_NAME         VARCHAR(100)  NOT NULL                COMMENT '점검 시점 시스템명 스냅샷',
    BASE_DATE           DATE          NOT NULL                COMMENT '미접속 일수 산정 기준일',
    INACTIVE_DAYS       INT           NOT NULL DEFAULT 90     COMMENT '장기 미접속 판단 기준 일수 (점검 시점 스냅샷)',
    ORIGINAL_FILE_NAME  VARCHAR(255)  NOT NULL                COMMENT '계정 목록 원본 파일명',
    FILE_HASH           CHAR(64)      NOT NULL                COMMENT '계정 목록 파일 SHA-256 해시',
    TOTAL_COUNT         INT           NOT NULL DEFAULT 0      COMMENT '전체 계정 수',
    ACTIVE_COUNT        INT           NOT NULL DEFAULT 0      COMMENT '정상 사용 계정 수',
    INACTIVE_COUNT      INT           NOT NULL DEFAULT 0      COMMENT '장기 미접속 계정 수',
    NEVER_COUNT         INT           NOT NULL DEFAULT 0      COMMENT '접속 이력 없는 계정 수',
    DISABLED_COUNT      INT           NOT NULL DEFAULT 0      COMMENT '이미 비활성(잠금) 계정 수',
    STATUS              VARCHAR(20)   NOT NULL DEFAULT 'ANALYZED' COMMENT '처리 상태 (ANALYZED: 검토대기, REVIEWED: 검토완료)',
    REMARK              VARCHAR(1000) NULL                    COMMENT '업로드 비고',
    REVIEW_COMMENT      VARCHAR(2000) NULL                    COMMENT '최종 검토 의견',
    REVIEWED_AT         DATETIME      NULL                    COMMENT '최종 검토 일시',
    REVIEWED_BY         BIGINT        NULL                    COMMENT '최종 검토자 사용자 ID',

    CREATED_AT          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY          BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT          DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY          BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN          CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT          DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY          BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (CHECK_ID),
    INDEX IDX_SEC_LOG_ACCOUNT_CHECK_YM (DELETED_YN, TARGET_YM, SYSTEM_ID),
    INDEX IDX_SEC_LOG_ACCOUNT_CHECK_HASH (FILE_HASH),
    CONSTRAINT FK_SEC_LOG_ACCOUNT_CHECK_SYSTEM FOREIGN KEY (SYSTEM_ID) REFERENCES sec_log_system (SYSTEM_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 장기 미접속 계정 점검 이력';

-- ------------------------------------------------------------
-- 8. 장기 미접속 계정 점검 상세 (계정 단위 판정 및 조치)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_account (
    ACCOUNT_ROW_ID      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '점검 계정 행 ID',
    CHECK_ID            BIGINT        NOT NULL                COMMENT '계정 점검 ID',
    ACCOUNT_ID          VARCHAR(200)  NOT NULL                COMMENT '시스템 계정 ID',
    ACCOUNT_NAME        VARCHAR(100)  NULL                    COMMENT '계정 사용자명',
    DEPT_NAME           VARCHAR(100)  NULL                    COMMENT '소속 부서명',
    ACCOUNT_ROLE        VARCHAR(100)  NULL                    COMMENT '계정 권한/역할',
    CREATED_DATE        DATE          NULL                    COMMENT '계정 생성일',
    LAST_LOGIN_DATE     DATE          NULL                    COMMENT '최종 접속일 (없으면 NULL)',
    INACTIVE_DAY_COUNT  INT           NULL                    COMMENT '기준일 대비 미접속 일수',
    ENABLED_YN          CHAR(1)       NOT NULL DEFAULT 'Y'    COMMENT '계정 활성 여부 (Y/N)',
    ACCOUNT_STATUS      VARCHAR(20)   NOT NULL                COMMENT '판정 결과 (ACTIVE/INACTIVE/NEVER_LOGGED_IN/ALREADY_DISABLED)',
    ACTION_STATUS       VARCHAR(20)   NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT '조치 상태 (PENDING/LOCKED/DISABLED/DELETED/EXCEPTION/NOT_APPLICABLE)',
    ACTION_CONTENT      VARCHAR(1000) NULL                    COMMENT '조치 내용 / 예외 사유',
    ACTION_AT           DATETIME      NULL                    COMMENT '조치 등록 일시',
    ACTION_BY           BIGINT        NULL                    COMMENT '조치 등록자 사용자 ID',

    CREATED_AT          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY          BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT          DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY          BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN          CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT          DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY          BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (ACCOUNT_ROW_ID),
    INDEX IDX_SEC_LOG_ACCOUNT_CHECK (CHECK_ID, DELETED_YN, ACCOUNT_STATUS, ACTION_STATUS),
    INDEX IDX_SEC_LOG_ACCOUNT_ID (ACCOUNT_ID),
    CONSTRAINT FK_SEC_LOG_ACCOUNT_CHECK FOREIGN KEY (CHECK_ID) REFERENCES sec_log_account_check (CHECK_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 장기 미접속 계정 점검 상세';

-- ------------------------------------------------------------
-- 점검 대상 시스템 초기 데이터 (ISMS 중점 점검 14개)
--   재실행 시 UK_SEC_LOG_SYSTEM_CODE 로 기존 데이터 유지
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_system
    (SYSTEM_CODE, SYSTEM_NAME, CATEGORY, DEFAULT_LOG_TYPE, INACTIVE_DAYS, DESCRIPTION, USE_YN, SORT_ORDER, CREATED_BY)
VALUES
('SVC_HOMEPAGE',  '홈페이지',               '서비스',         'WEB',      90, '관리자 페이지 접속 로그 / 관리자 계정 목록', 'Y', 10,  NULL),
('SVC_MALL',      '자사몰(깨끗한나라몰)',   '서비스',         'WEB',      90, '쇼핑몰 관리자(어드민) 접속 로그 / 관리자 계정 목록', 'Y', 20,  NULL),
('SVC_EMP_MALL',  '임직원몰',               '서비스',         'WEB',      90, '임직원몰 관리자 접속 로그 / 관리자 계정 목록', 'Y', 30,  NULL),
('SVC_KN_OMS',    'KN-OMS',                 '서비스',         'APP',      90, 'OMS 사용자 로그인 이력 / 사용자 계정 목록', 'Y', 40,  NULL),
('SEC_AWS_FW',    'AWS방화벽',              '정보보호시스템', 'NETWORK',  90, 'AWS Network Firewall / 보안그룹 관리 이력', 'Y', 50,  NULL),
('SEC_UTM',       'UTM',                    '정보보호시스템', 'NETWORK',  90, 'UTM 관리자 접속 로그 / 관리자 계정 목록', 'Y', 60,  NULL),
('SEC_SSL_VPN',   'SSL VPN',                '정보보호시스템', 'SECURITY', 90, 'VPN 사용자 접속(인증) 로그 / VPN 계정 목록', 'Y', 70,  NULL),
('SEC_GIT',       'GIT',                    '정보보호시스템', 'APP',      90, 'Git 저장소 로그인/감사 로그 / 사용자 목록', 'Y', 80,  NULL),
('SEC_V3_EPP',    'V3 EPP',                 '정보보호시스템', 'SECURITY', 90, 'V3 EPP 관리 콘솔 접속 로그 / 관리자 계정 목록', 'Y', 90,  NULL),
('SEC_AWS_CONSOLE','AWS console',           '정보보호시스템', 'CLOUD',    90, 'CloudTrail ConsoleLogin 이벤트 / IAM 자격증명 보고서', 'Y', 100, NULL),
('SEC_WAF',       'WAF',                    '정보보호시스템', 'SECURITY', 90, 'WAF 관리자 접속 로그 / 관리자 계정 목록', 'Y', 110, NULL),
('SEC_DLP',       'DLP',                    '정보보호시스템', 'SECURITY', 90, 'DLP 관리 콘솔 접속 로그 / 관리자 계정 목록', 'Y', 120, NULL),
('NET_AP_SWITCH', 'AP/스위치',              '네트워크',       'NETWORK',  90, '네트워크 장비 관리자 접속(SSH/Console) 로그 / 로컬 계정 목록', 'Y', 130, NULL),
('DB_OMS_HOME',   'DBMS(OMS, 홈페이지)',    'DBMS',           'DB',       90, 'DB 접속 감사 로그 / DB 계정 목록', 'Y', 140, NULL);
