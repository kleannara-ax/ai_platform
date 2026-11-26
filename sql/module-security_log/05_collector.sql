-- ============================================================
-- module-security_log : 05_collector.sql
-- 시스템별 로그 수집 연결(API/SFTP/수동) 설정·상태 + 수집/업로드 이력
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS, INSERT IGNORE)
-- 주의    : DROP / TRUNCATE / DELETE / ALTER DROP COLUMN 없음, core 테이블 FK 없음
--           API 비밀번호/토큰 등 비밀값은 DB 에 저장하지 않음 (서버 환경변수 이름만 저장)
-- 선행    : 01 ~ 04
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 9. 시스템별 수집 연결 설정 및 최근 상태 (시스템당 1건)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_collector (
    COLLECTOR_ID          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '수집 연결 ID',
    SYSTEM_ID             BIGINT        NOT NULL                COMMENT '점검 대상 시스템 ID',
    COLLECT_MODE          VARCHAR(20)   NOT NULL DEFAULT 'MANUAL' COMMENT '수집 방식 (API: API 연동, SFTP: 파일 서버, MANUAL: 수동 업로드)',
    CONNECTOR_TYPE        VARCHAR(30)   NULL                    COMMENT '연동 유형 (PRIVACY_I, AWS_CLOUDTRAIL, GENERIC_REST, SFTP 등)',
    ENDPOINT_URL          VARCHAR(500)  NULL                    COMMENT 'API 주소 또는 SFTP 주소 (http/https/sftp)',
    AUTH_TYPE             VARCHAR(20)   NOT NULL DEFAULT 'NONE' COMMENT '인증 방식 (NONE/API_KEY/BEARER/BASIC)',
    CREDENTIAL_ENV        VARCHAR(100)  NULL                    COMMENT '인증값을 보관한 서버 환경변수 이름 (비밀값 자체는 저장하지 않음)',
    COLLECT_ACCESS_YN     CHAR(1)       NOT NULL DEFAULT 'Y'    COMMENT '접속/감사 로그 수집 여부 (Y/N)',
    COLLECT_ACCOUNT_YN    CHAR(1)       NOT NULL DEFAULT 'Y'    COMMENT '계정 목록 수집 여부 (Y/N)',
    COLLECT_INTERVAL      VARCHAR(20)   NOT NULL DEFAULT 'DAILY' COMMENT '수집 주기 (DAILY: 매일, HOURLY: 매시간)',
    COLLECT_TIME          CHAR(5)       NOT NULL DEFAULT '01:00' COMMENT '매일 수집 시각 (HH:mm, DAILY 일 때)',
    ENABLED_YN            CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '자동 수집 사용 여부 (Y/N)',
    LAST_TEST_AT          DATETIME      NULL                    COMMENT '마지막 연결 테스트 일시',
    LAST_TEST_RESULT      VARCHAR(20)   NULL                    COMMENT '마지막 연결 테스트 결과 (SUCCESS/FAIL)',
    LAST_TEST_MESSAGE     VARCHAR(500)  NULL                    COMMENT '마지막 연결 테스트 메시지',
    LAST_COLLECT_AT       DATETIME      NULL                    COMMENT '마지막 수집 시도 일시',
    LAST_COLLECT_STATUS   VARCHAR(20)   NULL                    COMMENT '마지막 수집 결과 (SUCCESS/FAIL)',
    LAST_COLLECT_MESSAGE  VARCHAR(500)  NULL                    COMMENT '마지막 수집 메시지 (실패 사유 등)',
    LAST_COLLECT_COUNT    INT           NULL                    COMMENT '마지막 수집 건수 (로그 라인/계정 수)',
    LAST_SUCCESS_AT       DATETIME      NULL                    COMMENT '마지막 수집 성공 일시',
    COLLECTED_UNTIL       DATETIME      NULL                    COMMENT '접속 로그 수집 완료 시점 (다음 수집은 이 시각부터, 중복·누락 방지)',
    FAIL_COUNT            INT           NOT NULL DEFAULT 0      COMMENT '연속 수집 실패 횟수',
    REMARK                VARCHAR(500)  NULL                    COMMENT '비고 (제조사 문의 현황 등)',

    CREATED_AT            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY            BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT            DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY            BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN            CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT            DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY            BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (COLLECTOR_ID),
    CONSTRAINT UK_SEC_LOG_COLLECTOR_SYSTEM UNIQUE (SYSTEM_ID),
    INDEX IDX_SEC_LOG_COLLECTOR_ACTIVE (DELETED_YN, ENABLED_YN, COLLECT_MODE),
    CONSTRAINT FK_SEC_LOG_COLLECTOR_SYSTEM FOREIGN KEY (SYSTEM_ID) REFERENCES sec_log_system (SYSTEM_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 시스템별 수집 연결 설정 및 상태';

-- ------------------------------------------------------------
-- 10. 수집 / 업로드 이력 (API 자동 수집, 연결 테스트, 수동 업로드 모두 기록)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_collect_history (
    HISTORY_ID      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '수집 이력 ID',
    SYSTEM_ID       BIGINT        NOT NULL                COMMENT '점검 대상 시스템 ID',
    SOURCE          VARCHAR(20)   NOT NULL                COMMENT '수집 경로 (API/SFTP/MANUAL)',
    TRIGGER_TYPE    VARCHAR(20)   NOT NULL                COMMENT '실행 계기 (SCHEDULE: 자동, RUN_NOW: 지금 수집, TEST: 연결 테스트, UPLOAD: 수동 업로드)',
    DATA_KIND       VARCHAR(20)   NOT NULL                COMMENT '데이터 종류 (ACCESS_LOG: 접속/감사 로그, ACCOUNT_LIST: 계정 목록, CONNECTION: 연결 확인)',
    STATUS          VARCHAR(20)   NOT NULL                COMMENT '결과 (SUCCESS/FAIL)',
    TARGET_YM       CHAR(6)       NULL                    COMMENT '대상 연월 (YYYYMM)',
    RECORD_COUNT    INT           NULL                    COMMENT '처리 건수 (로그 라인/계정 수)',
    UPLOAD_ID       BIGINT        NULL                    COMMENT '생성된 로그 업로드 ID (sec_log_upload)',
    CHECK_ID        BIGINT        NULL                    COMMENT '생성된 계정 점검 ID (sec_log_account_check)',
    FILE_NAME       VARCHAR(255)  NULL                    COMMENT '파일명 (수동 업로드/파일 수집)',
    MESSAGE         VARCHAR(1000) NULL                    COMMENT '결과 메시지 (실패 사유 등)',
    STARTED_AT      DATETIME      NOT NULL                COMMENT '시작 일시',
    ENDED_AT        DATETIME      NULL                    COMMENT '종료 일시',

    CREATED_AT      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY      BIGINT        NULL COMMENT '생성자 사용자 ID (자동 수집은 NULL)',
    UPDATED_AT      DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY      BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN      CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT      DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY      BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (HISTORY_ID),
    INDEX IDX_SEC_LOG_COLLECT_HIST_SYSTEM (SYSTEM_ID, DELETED_YN, STARTED_AT),
    INDEX IDX_SEC_LOG_COLLECT_HIST_RECENT (DELETED_YN, STARTED_AT),
    CONSTRAINT FK_SEC_LOG_COLLECT_HIST_SYSTEM FOREIGN KEY (SYSTEM_ID) REFERENCES sec_log_system (SYSTEM_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 수집/업로드 이력';

-- ------------------------------------------------------------
-- 초기 데이터: 모든 점검 대상 시스템을 '수동 업로드' 로 등록
--   API 연동이 준비되면 [수집 연결 관리] 화면에서 시스템별로 변경
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_collector (SYSTEM_ID, COLLECT_MODE, REMARK, CREATED_BY)
SELECT s.SYSTEM_ID, 'MANUAL', '초기 등록 - API 연동 전 수동 업로드', NULL
  FROM sec_log_system s
 WHERE s.DELETED_YN = 'N';
