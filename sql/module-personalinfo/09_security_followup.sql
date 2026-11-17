-- ============================================================
--  module-personalinfo : 보안팀 후속 관리 (만료 서류 추적, 조치 지연, 정기 점검 알림, 접근 기록, 서류 일자)
--  MariaDB 10.11+ (utf8mb4)
--  선행: 01_schema.sql ~ 08_form_template.sql
--
--  변경 내용
--    1) pi_contract
--       - EXPIRY_DOCS_NOTE       : 만료 서류(점검 체크리스트·확약서·파기 확인서)를 다 받지 못한 채 종료 처리한 사유
--       - EXPIRY_DOCS_REMIND_YN  : 미수취 만료 서류 독촉 메일 계속 발송 여부 (기본 Y)
--    2) pi_contract_file.DOCUMENT_DATE : 받은 서류의 일자 (확약서 서명일 / 파기 확인서 파기 완료일)
--    3) pi_alert_log
--       - ALERT_TYPE  : 알림 종류 (EXPIRY 만료 알림 / EXPIRY_DOCS 만료 서류 독촉 / ACTION_OVERDUE 조치 지연 /
--                       REGULAR_INSPECTION 정기 점검 요청 / REGULAR_SUMMARY 정기 점검 요약(관리자))
--       - CONTRACT_ID : NULL 허용으로 변경 (관리자 요약 메일처럼 특정 계약이 없는 알림). 기존 값은 그대로이며 FK 도 유지된다.
--    4) pi_access_log 신규 : 계약 상세·점검 상세 조회, 계약서·점검 결과·엑셀 다운로드 기록 (누가·언제·어디서·무엇을·사유)
--
--  참고
--    - 기존 데이터는 바꾸지 않는다. 기존 알림 이력은 ALERT_TYPE 기본값(EXPIRY)이 된다.
--
--  재실행 안전: CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS / MODIFY COLUMN(같은 정의 반복) / CREATE INDEX IF NOT EXISTS
--  DROP / DELETE / TRUNCATE 없음, core 테이블 FK 없음 (사용자 컬럼은 core_user.USER_ID 값만 저장)
-- ============================================================

SET NAMES utf8mb4;

-- 1) 계약: 만료 서류 미수취 종료 사유 / 독촉 여부
ALTER TABLE pi_contract
    ADD COLUMN IF NOT EXISTS EXPIRY_DOCS_NOTE VARCHAR(500) NULL
        COMMENT '만료 서류(만료 점검 체크리스트·확약서·파기 확인서)를 다 받지 못한 채 종료 처리한 사유' AFTER TERMINATION_REASON,
    ADD COLUMN IF NOT EXISTS EXPIRY_DOCS_REMIND_YN CHAR(1) NOT NULL DEFAULT 'Y'
        COMMENT '미수취 만료 서류 독촉 메일 계속 발송 여부 (Y/N)' AFTER EXPIRY_DOCS_NOTE;

-- 2) 첨부: 서류 일자
ALTER TABLE pi_contract_file
    ADD COLUMN IF NOT EXISTS DOCUMENT_DATE DATE NULL
        COMMENT '받은 서류의 일자 (정보보호 및 비밀유지 확약서 서명일 / 개인정보 파기 확인서 파기 완료일)' AFTER SIGNED_DATE;

-- 3) 알림 이력: 알림 종류, 계약 없는 알림 허용
ALTER TABLE pi_alert_log
    ADD COLUMN IF NOT EXISTS ALERT_TYPE VARCHAR(30) NOT NULL DEFAULT 'EXPIRY'
        COMMENT '알림 종류 (EXPIRY 만료 알림 / EXPIRY_DOCS 만료 서류 독촉 / ACTION_OVERDUE 조치 지연 / REGULAR_INSPECTION 정기 점검 요청 / REGULAR_SUMMARY 정기 점검 요약)'
        AFTER CONTRACT_ID;

ALTER TABLE pi_alert_log
    MODIFY COLUMN CONTRACT_ID BIGINT NULL COMMENT '위수탁 계약 ID (pi_contract, 관리자 요약 메일처럼 특정 계약이 없으면 NULL)';

CREATE INDEX IF NOT EXISTS IDX_PI_ALERT_LOG_TYPE ON pi_alert_log (ALERT_TYPE, CREATED_AT);

-- 4) 접근 기록
CREATE TABLE IF NOT EXISTS pi_access_log (
    ACCESS_ID        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '접근 기록 ID',
    ACTION_TYPE      VARCHAR(30)   NOT NULL                COMMENT '행위 (VIEW_CONTRACT 계약 상세 조회 / VIEW_INSPECTION 점검 상세 조회 / DOWNLOAD_FILE 계약서·증빙 다운로드 / DOWNLOAD_INSPECTION 점검 결과 다운로드 / DOWNLOAD_EXCEL 계약 목록 엑셀 / DOWNLOAD_ACCESS_LOG 접근 기록 엑셀)',
    CONTRACT_ID      BIGINT        NULL                    COMMENT '대상 위수탁 계약 ID (pi_contract, 목록 엑셀 등은 NULL)',
    VENDOR_NAME      VARCHAR(200)  NULL                    COMMENT '대상 수탁업체명 (기록 시점)',
    CONTRACT_NAME    VARCHAR(200)  NULL                    COMMENT '대상 계약명 (기록 시점)',
    TARGET_ID        BIGINT        NULL                    COMMENT '대상 ID (첨부파일 ID / 점검 ID)',
    TARGET_NAME      VARCHAR(500)  NULL                    COMMENT '대상 이름 (파일명, 엑셀 검색 조건 등)',
    RECORD_COUNT     INT           NULL                    COMMENT '내려받은 행 수 (엑셀)',
    REASON           VARCHAR(500)  NULL                    COMMENT '다운로드 사유 (엑셀 다운로드 시 필수)',
    USER_ID          BIGINT        NULL                    COMMENT '사용자 ID (core_user.USER_ID 값, FK 없음)',
    USER_LOGIN_ID    VARCHAR(100)  NULL                    COMMENT '사용자 로그인 ID (기록 시점)',
    USER_NAME        VARCHAR(100)  NULL                    COMMENT '사용자명 (기록 시점)',
    CLIENT_IP        VARCHAR(64)   NULL                    COMMENT '접속 IP (X-Forwarded-For 첫 값 또는 접속 주소)',
    USER_AGENT       VARCHAR(300)  NULL                    COMMENT '브라우저 정보',

    CREATED_AT       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '기록 일시',
    CREATED_BY       BIGINT        NULL                    COMMENT '생성자 사용자 ID',
    UPDATED_AT       DATETIME      NULL                    COMMENT '수정일시 (기록은 수정하지 않음)',
    UPDATED_BY       BIGINT        NULL                    COMMENT '수정자 사용자 ID',
    DELETED_YN       CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (기록은 삭제하지 않음)',
    DELETED_AT       DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY       BIGINT        NULL                    COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (ACCESS_ID),
    INDEX IDX_PI_ACCESS_LOG_CREATED (CREATED_AT),
    INDEX IDX_PI_ACCESS_LOG_CONTRACT (CONTRACT_ID, CREATED_AT),
    INDEX IDX_PI_ACCESS_LOG_USER (USER_ID, CREATED_AT),
    INDEX IDX_PI_ACCESS_LOG_ACTION (ACTION_TYPE, CREATED_AT)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='개인정보 위수탁 접근 기록 (조회·다운로드)';

-- 확인용
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ((TABLE_NAME = 'pi_contract' AND COLUMN_NAME IN ('EXPIRY_DOCS_NOTE', 'EXPIRY_DOCS_REMIND_YN'))
     OR (TABLE_NAME = 'pi_contract_file' AND COLUMN_NAME = 'DOCUMENT_DATE')
     OR (TABLE_NAME = 'pi_alert_log' AND COLUMN_NAME IN ('ALERT_TYPE', 'CONTRACT_ID')));
SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pi_access_log';
