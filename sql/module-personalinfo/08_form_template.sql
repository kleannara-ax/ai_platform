-- ============================================================
--  module-personalinfo : 양식 관리 (보안팀 양식 버전 관리)
--  MariaDB 10.11+ (utf8mb4)
--  선행: 01_schema.sql
--
--  변경 내용
--    1) pi_form_template 신규
--       - 보안팀이 관리하는 양식 3종의 버전 이력
--           CHECKLIST           위수탁 점검 체크리스트 (.xlsx, 정기·만료 점검 공용)
--           SECURITY_PLEDGE     정보보호 및 비밀유지 확약서 (.docx/.doc/.hwp/.hwpx/.pdf)
--           DESTRUCTION_CONFIRM 개인정보 파기 확인서 (.docx/.doc/.hwp/.hwpx/.pdf)
--       - 종류별로 사용 중(ACTIVE_YN='Y') 버전은 최대 1건. 없으면 모듈에 내장된 기본 양식(버전 0)을 쓴다.
--       - 이전 버전은 지우지 않고 남겨 두어 되돌리기·심사 증적에 쓴다. 삭제는 소프트 삭제.
--       - 실제 파일은 업로드 디렉토리(form-template/yyyy/MM/UUID.확장자)에 저장한다.
--    2) pi_inspection.FORM_VERSION      : 점검 업로드 시점에 사용 중이던 체크리스트 양식 버전
--    3) pi_alert_log.FORM_VERSIONS      : 만료 알림 메일에 첨부한 양식 버전
--
--  참고
--    - 기존 데이터는 바꾸지 않는다. 기존 점검·알림 이력의 양식 버전은 비어 있다.
--
--  재실행 안전: CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS
--  DROP / DELETE / TRUNCATE 없음, core 테이블 FK 없음 (사용자 컬럼은 core_user.USER_ID 값만 저장)
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS pi_form_template (
    FORM_TEMPLATE_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '양식 버전 ID',
    FORM_TYPE          VARCHAR(30)   NOT NULL                COMMENT '양식 종류 (CHECKLIST/SECURITY_PLEDGE/DESTRUCTION_CONFIRM)',
    VERSION_NO         INT           NOT NULL                COMMENT '양식 버전 번호 (종류별 1부터 증가, 0은 모듈 내장 기본 양식이라 저장하지 않음)',
    ORIGINAL_NAME      VARCHAR(255)  NOT NULL                COMMENT '업로드 원본 파일명',
    STORED_PATH        VARCHAR(500)  NOT NULL                COMMENT '저장 경로 (업로드 루트 기준 상대경로)',
    CONTENT_TYPE       VARCHAR(150)  NULL                    COMMENT 'MIME 타입',
    FILE_EXT           VARCHAR(10)   NOT NULL                COMMENT '파일 확장자 (xlsx/docx/doc/hwp/hwpx/pdf)',
    FILE_SIZE          BIGINT        NOT NULL DEFAULT 0      COMMENT '파일 크기 (byte)',
    FILE_HASH          VARCHAR(64)   NULL                    COMMENT '파일 SHA-256 해시',
    CHANGE_NOTE        VARCHAR(1000) NULL                    COMMENT '변경 메모 (무엇을 바꿨는지)',
    VALIDATION_NOTE    VARCHAR(2000) NULL                    COMMENT '등록 시 양식 검사 결과 요약·경고',
    ACTIVE_YN          CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '사용 중 여부 (종류별 최대 1건 Y)',
    ACTIVATED_AT       DATETIME      NULL                    COMMENT '마지막으로 사용 지정한 일시',
    ACTIVATED_BY       BIGINT        NULL                    COMMENT '마지막으로 사용 지정한 사용자 ID',
    ACTIVATED_BY_NAME  VARCHAR(100)  NULL                    COMMENT '마지막으로 사용 지정한 사용자명',
    CREATED_BY_NAME    VARCHAR(100)  NULL                    COMMENT '등록자명',

    CREATED_AT         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY         BIGINT        NULL                    COMMENT '생성자 사용자 ID',
    UPDATED_AT         DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY         BIGINT        NULL                    COMMENT '수정자 사용자 ID',
    DELETED_YN         CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부',
    DELETED_AT         DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY         BIGINT        NULL                    COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (FORM_TEMPLATE_ID),
    CONSTRAINT UK_PI_FORM_TEMPLATE_VERSION UNIQUE (FORM_TYPE, VERSION_NO),
    INDEX IDX_PI_FORM_TEMPLATE_ACTIVE (FORM_TYPE, ACTIVE_YN, DELETED_YN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='개인정보 위수탁 양식 관리 (보안팀 양식 버전 이력)';

ALTER TABLE pi_inspection
    ADD COLUMN IF NOT EXISTS FORM_VERSION VARCHAR(50) NULL
        COMMENT '점검 업로드 시점에 사용 중이던 체크리스트 양식 버전 (예: v2, 기본)' AFTER UPLOAD_GROUP_ID;

ALTER TABLE pi_alert_log
    ADD COLUMN IF NOT EXISTS FORM_VERSIONS VARCHAR(300) NULL
        COMMENT '만료 알림 메일에 첨부한 양식 버전 (예: 체크리스트 v2 / 확약서 기본 / 파기 확인서 v1)' AFTER ERROR_MESSAGE;

-- 확인용
SELECT TABLE_NAME, TABLE_COMMENT
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pi_form_template';
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ((TABLE_NAME = 'pi_inspection' AND COLUMN_NAME = 'FORM_VERSION')
     OR (TABLE_NAME = 'pi_alert_log' AND COLUMN_NAME = 'FORM_VERSIONS'));
