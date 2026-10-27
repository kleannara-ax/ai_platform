-- ============================================================
--  module-personalinfo : 개인정보 위수탁 협력사 관리 - DDL
--  MariaDB 10.11+ (utf8mb4)
--
--  생성 테이블 (모두 업무 테이블, core 테이블 FK 없음)
--    1) pi_contract         위수탁 계약 (외부위탁 현황 리스트)
--    2) pi_contract_file    위수탁 계약서/증빙 첨부파일 메타데이터
--    3) pi_inspection       위수탁 점검 이력 (체크리스트 업로드 1건)
--    4) pi_inspection_item  위수탁 점검 체크리스트 항목별 결과
--    5) pi_alert_log        계약 만료 알림 메일 발송 이력
--
--  재실행 안전: CREATE TABLE IF NOT EXISTS 만 사용한다.
--    DROP / TRUNCATE / DELETE / ALTER ... DROP 없음.
--  사용자 컬럼(CREATED_BY 등)은 core_user.USER_ID 값만 저장하며 FK 를 만들지 않는다.
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1) 위수탁 계약
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pi_contract (
    CONTRACT_ID             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '위수탁 계약 ID',
    ISMS_SCOPE_YN           CHAR(1)       NOT NULL DEFAULT 'Y'    COMMENT 'ISMS 인증 범위 여부 (Y/N)',
    VENDOR_NAME             VARCHAR(200)  NOT NULL                COMMENT '수탁업체명',
    SERVICE_NAME            VARCHAR(200)  NULL                    COMMENT '관련 서비스 (예: 자사몰, 임직원몰, OMS)',
    ENTRUSTED_TASK          VARCHAR(1000) NOT NULL                COMMENT '위탁업무 내용',
    DEPT_NAME               VARCHAR(100)  NULL                    COMMENT '당사 담당부서',
    MANAGER_NAME            VARCHAR(100)  NULL                    COMMENT '당사 계약 담당자명',
    MANAGER_EMAIL           VARCHAR(200)  NULL                    COMMENT '당사 계약 담당자 이메일 (만료 알림 수신)',
    VENDOR_CONTACT_NAME     VARCHAR(100)  NULL                    COMMENT '수탁사 담당자명',
    VENDOR_CONTACT_EMAIL    VARCHAR(200)  NULL                    COMMENT '수탁사 담당자 이메일',
    VENDOR_CONTACT_PHONE    VARCHAR(50)   NULL                    COMMENT '수탁사 담당자 연락처',
    CONTRACT_START_DATE     DATE          NULL                    COMMENT '계약 시작일',
    CONTRACT_END_DATE       DATE          NULL                    COMMENT '계약 종료일 (만료 알림 기준일)',
    CONTRACT_PERIOD_NOTE    VARCHAR(500)  NULL                    COMMENT '계약기간 비고 (예: 체결일로부터 1년, 이용약관)',
    AUTO_RENEW_YN           CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '자동연장 여부 (Y/N)',
    AUTO_RENEW_NOTE         VARCHAR(200)  NULL                    COMMENT '자동연장 비고 (예: 1개월 단위)',
    CONTRACT_DOC_YN         CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '위탁 계약서 작성 여부 (Y/N)',
    CONTRACT_SIGNED_DATE    DATE          NULL                    COMMENT '위수탁 계약 체결일',
    SUBCONTRACT_YN          CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '재위탁 여부 (Y/N)',
    SUBCONTRACTOR_NAME      VARCHAR(500)  NULL                    COMMENT '재위탁 업체명',
    SUBCONTRACT_TASK        VARCHAR(1000) NULL                    COMMENT '재위탁 업무',
    SUBCONTRACT_CONSENT_YN  CHAR(1)       NULL                    COMMENT '재위탁 동의서 작성 여부 (Y/N)',
    ALERT_YN                CHAR(1)       NOT NULL DEFAULT 'Y'    COMMENT '만료 알림 사용 여부 (Y/N)',
    ALERT_DAYS_BEFORE       INT           NOT NULL DEFAULT 30     COMMENT '만료 알림 시작 기준 (만료 N일 전)',
    ALERT_EXTRA_EMAILS      VARCHAR(1000) NULL                    COMMENT '추가 알림 수신자 이메일 (쉼표 구분)',
    TERMINATED_YN           CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '계약 종료 처리 여부 (Y/N)',
    TERMINATED_DATE         DATE          NULL                    COMMENT '계약 종료 처리일',
    TERMINATION_REASON      VARCHAR(500)  NULL                    COMMENT '계약 종료 사유',
    LAST_INSPECTION_ID      BIGINT        NULL                    COMMENT '최근 점검 ID (pi_inspection, 목록 표시용)',
    LAST_INSPECTION_DATE    DATE          NULL                    COMMENT '최근 점검일 (목록 표시용)',
    LAST_INSPECTION_RESULT  VARCHAR(20)   NULL                    COMMENT '최근 점검 결과 (PASS/NEED_ACTION/INCOMPLETE)',
    REMARK                  VARCHAR(2000) NULL                    COMMENT '비고',

    CREATED_AT              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY              BIGINT        NULL                    COMMENT '생성자 사용자 ID',
    UPDATED_AT              DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY              BIGINT        NULL                    COMMENT '수정자 사용자 ID',
    DELETED_YN              CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부',
    DELETED_AT              DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY              BIGINT        NULL                    COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (CONTRACT_ID),
    INDEX IDX_PI_CONTRACT_END_DATE (DELETED_YN, TERMINATED_YN, CONTRACT_END_DATE),
    INDEX IDX_PI_CONTRACT_VENDOR (VENDOR_NAME),
    INDEX IDX_PI_CONTRACT_DEPT (DEPT_NAME),
    INDEX IDX_PI_CONTRACT_ALERT (ALERT_YN, CONTRACT_END_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='개인정보 위수탁 계약 (외부위탁 현황)';

-- ------------------------------------------------------------
-- 2) 위수탁 계약서/증빙 첨부파일
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pi_contract_file (
    FILE_ID        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '첨부파일 ID',
    CONTRACT_ID    BIGINT        NOT NULL                COMMENT '위수탁 계약 ID (pi_contract)',
    FILE_TYPE      VARCHAR(30)   NOT NULL DEFAULT 'CONTRACT' COMMENT '파일 구분 (CONTRACT/SUBCONTRACT_CONSENT/SECURITY_PLEDGE/DESTRUCTION_CONFIRM/ETC)',
    ORIGINAL_NAME  VARCHAR(255)  NOT NULL                COMMENT '원본 파일명',
    STORED_PATH    VARCHAR(500)  NOT NULL                COMMENT '저장 경로 (업로드 루트 기준 상대경로)',
    CONTENT_TYPE   VARCHAR(150)  NULL                    COMMENT 'MIME 타입',
    FILE_SIZE      BIGINT        NOT NULL DEFAULT 0      COMMENT '파일 크기 (byte)',
    FILE_HASH      VARCHAR(64)   NULL                    COMMENT '파일 SHA-256 해시 (위변조 확인용)',
    DESCRIPTION    VARCHAR(500)  NULL                    COMMENT '파일 설명',

    CREATED_AT     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY     BIGINT        NULL                    COMMENT '생성자 사용자 ID',
    UPDATED_AT     DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY     BIGINT        NULL                    COMMENT '수정자 사용자 ID',
    DELETED_YN     CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부',
    DELETED_AT     DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY     BIGINT        NULL                    COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (FILE_ID),
    INDEX IDX_PI_CONTRACT_FILE_CONTRACT (CONTRACT_ID, DELETED_YN),
    CONSTRAINT FK_PI_CONTRACT_FILE_CONTRACT FOREIGN KEY (CONTRACT_ID) REFERENCES pi_contract (CONTRACT_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='개인정보 위수탁 계약서/증빙 첨부파일';

-- ------------------------------------------------------------
-- 3) 위수탁 점검 이력
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pi_inspection (
    INSPECTION_ID          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '위수탁 점검 ID',
    CONTRACT_ID            BIGINT        NOT NULL                COMMENT '위수탁 계약 ID (pi_contract)',
    INSPECTION_TYPE        VARCHAR(20)   NOT NULL DEFAULT 'REGULAR' COMMENT '점검 구분 (REGULAR:정기 / TERMINATION:계약변경·만료)',
    INSPECTION_YEAR        INT           NOT NULL                COMMENT '점검 연도',
    INSPECTION_DATE        DATE          NOT NULL                COMMENT '점검일 (평가일)',
    INSPECTOR_NAME         VARCHAR(100)  NULL                    COMMENT '평가자',
    SHEET_COMPANY_NAME     VARCHAR(200)  NULL                    COMMENT '체크리스트 기재 회사명',
    SHEET_TASK_NAME        VARCHAR(500)  NULL                    COMMENT '체크리스트 기재 수행(수탁) 업무',
    SYSTEM_OWNED_YN        CHAR(1)       NULL                    COMMENT '개인정보처리시스템 보유 여부 (Y/N)',
    SYSTEM_NAME            VARCHAR(200)  NULL                    COMMENT '개인정보처리시스템명',
    TOTAL_COUNT            INT           NOT NULL DEFAULT 0      COMMENT '평가 항목 수',
    Y_COUNT                INT           NOT NULL DEFAULT 0      COMMENT '충족(Y) 항목 수',
    N_COUNT                INT           NOT NULL DEFAULT 0      COMMENT '미충족(N) 항목 수',
    NA_COUNT               INT           NOT NULL DEFAULT 0      COMMENT '해당없음(NA) 항목 수',
    BLANK_COUNT            INT           NOT NULL DEFAULT 0      COMMENT '평가 미기재 항목 수',
    RESULT                 VARCHAR(20)   NOT NULL                COMMENT '점검 결과 (PASS:적합 / NEED_ACTION:조치 필요 / INCOMPLETE:평가 미완료)',
    ACTION_DUE_DATE        DATE          NULL                    COMMENT '조치 예정일',
    ACTION_COMPLETED_YN    CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '조치 완료 여부 (Y/N)',
    ACTION_COMPLETED_DATE  DATE          NULL                    COMMENT '조치 완료일',
    ORIGINAL_NAME          VARCHAR(255)  NOT NULL                COMMENT '업로드 체크리스트 원본 파일명',
    STORED_PATH            VARCHAR(500)  NOT NULL                COMMENT '업로드 체크리스트 저장 경로 (상대경로)',
    CONTENT_TYPE           VARCHAR(150)  NULL                    COMMENT 'MIME 타입',
    FILE_SIZE              BIGINT        NOT NULL DEFAULT 0      COMMENT '파일 크기 (byte)',
    FILE_HASH              VARCHAR(64)   NULL                    COMMENT '파일 SHA-256 해시',
    REMARK                 VARCHAR(2000) NULL                    COMMENT '비고',
    UPLOAD_GROUP_ID        VARCHAR(36)   NULL                    COMMENT '업체별 일괄 업로드 묶음 ID (같은 체크리스트로 여러 계약에 등록 시 동일, 개별 업로드는 NULL)',

    CREATED_AT             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY             BIGINT        NULL                    COMMENT '생성자 사용자 ID',
    UPDATED_AT             DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY             BIGINT        NULL                    COMMENT '수정자 사용자 ID',
    DELETED_YN             CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부',
    DELETED_AT             DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY             BIGINT        NULL                    COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (INSPECTION_ID),
    INDEX IDX_PI_INSPECTION_CONTRACT (CONTRACT_ID, DELETED_YN, INSPECTION_DATE),
    INDEX IDX_PI_INSPECTION_YEAR (INSPECTION_YEAR, DELETED_YN),
    INDEX IDX_PI_INSPECTION_UPLOAD_GROUP (UPLOAD_GROUP_ID),
    CONSTRAINT FK_PI_INSPECTION_CONTRACT FOREIGN KEY (CONTRACT_ID) REFERENCES pi_contract (CONTRACT_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='개인정보 위수탁 점검 이력 (체크리스트 업로드)';

-- ------------------------------------------------------------
-- 4) 위수탁 점검 체크리스트 항목별 결과
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pi_inspection_item (
    ITEM_ID         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '점검 항목 결과 ID',
    INSPECTION_ID   BIGINT        NOT NULL                COMMENT '위수탁 점검 ID (pi_inspection)',
    SHEET_TYPE      VARCHAR(20)   NOT NULL                COMMENT '시트 구분 (MANAGEMENT:관리체계 / SYSTEM:개인정보처리시스템 / TERMINATION:계약변경만료)',
    ROW_NO          INT           NOT NULL                COMMENT '엑셀 행 번호',
    SORT_ORDER      INT           NOT NULL DEFAULT 0      COMMENT '정렬 순서',
    CATEGORY        VARCHAR(100)  NULL                    COMMENT '구분 (예: 관리 조직, 접근 권한)',
    IMPORTANT_YN    CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '중요 항목 여부 (Y/N)',
    QUESTION        VARCHAR(1000) NOT NULL                COMMENT '평가 항목',
    CHECK_METHOD    VARCHAR(2000) NULL                    COMMENT '점검 방법',
    CURRENT_STATUS  VARCHAR(4000) NULL                    COMMENT '현황 (협력사 작성)',
    EVALUATION      VARCHAR(10)   NULL                    COMMENT '평가 (Y/N/NA, 미기재 NULL)',
    EVALUATION_RAW  VARCHAR(100)  NULL                    COMMENT '평가 원문 값',
    ACTION_PLAN     VARCHAR(2000) NULL                    COMMENT '조치 계획',
    ACTION_SCHEDULE VARCHAR(200)  NULL                    COMMENT '조치 일정',
    CONFIRM_DATE    DATE          NULL                    COMMENT '확인일자 (계약변경·만료 점검)',
    CONFIRMER_NAME  VARCHAR(100)  NULL                    COMMENT '확인 담당자 (계약변경·만료 점검)',
    ITEM_REMARK     VARCHAR(1000) NULL                    COMMENT '항목 비고',

    CREATED_AT      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY      BIGINT        NULL                    COMMENT '생성자 사용자 ID',
    UPDATED_AT      DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY      BIGINT        NULL                    COMMENT '수정자 사용자 ID',
    DELETED_YN      CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부',
    DELETED_AT      DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY      BIGINT        NULL                    COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (ITEM_ID),
    INDEX IDX_PI_INSPECTION_ITEM_INSPECTION (INSPECTION_ID, DELETED_YN, SORT_ORDER),
    CONSTRAINT FK_PI_INSPECTION_ITEM_INSPECTION FOREIGN KEY (INSPECTION_ID) REFERENCES pi_inspection (INSPECTION_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='개인정보 위수탁 점검 체크리스트 항목별 결과';

-- ------------------------------------------------------------
-- 5) 계약 만료 알림 메일 발송 이력
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pi_alert_log (
    ALERT_ID        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '알림 ID',
    CONTRACT_ID     BIGINT        NOT NULL                COMMENT '위수탁 계약 ID (pi_contract)',
    TRIGGER_TYPE    VARCHAR(10)   NOT NULL                COMMENT '발송 경로 (AUTO:자동 / MANUAL:수동)',
    ALERT_STAGE     VARCHAR(20)   NOT NULL                COMMENT '알림 단계 (D-30, D-7, D-1, D-DAY, EXPIRED, MANUAL)',
    TARGET_END_DATE DATE          NULL                    COMMENT '알림 당시 계약 종료일',
    DEDUP_KEY       VARCHAR(100)  NULL                    COMMENT '자동 알림 중복 방지 키 (계약ID:단계:종료일, 수동은 NULL)',
    RECIPIENTS      VARCHAR(2000) NULL                    COMMENT '수신자 이메일 (쉼표 구분)',
    SUBJECT         VARCHAR(500)  NULL                    COMMENT '메일 제목',
    SEND_STATUS     VARCHAR(10)   NOT NULL DEFAULT 'PENDING' COMMENT '발송 상태 (PENDING/SENT/FAILED/SKIPPED)',
    SENT_AT         DATETIME      NULL                    COMMENT '발송 완료일시',
    RETRY_COUNT     INT           NOT NULL DEFAULT 0      COMMENT '재시도 횟수',
    ERROR_MESSAGE   VARCHAR(1000) NULL                    COMMENT '실패/미발송 사유',

    CREATED_AT      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY      BIGINT        NULL                    COMMENT '생성자 사용자 ID (자동 발송은 NULL)',
    UPDATED_AT      DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY      BIGINT        NULL                    COMMENT '수정자 사용자 ID',
    DELETED_YN      CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부',
    DELETED_AT      DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY      BIGINT        NULL                    COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (ALERT_ID),
    CONSTRAINT UK_PI_ALERT_LOG_DEDUP UNIQUE (DEDUP_KEY),
    INDEX IDX_PI_ALERT_LOG_CONTRACT (CONTRACT_ID, DELETED_YN, ALERT_ID),
    CONSTRAINT FK_PI_ALERT_LOG_CONTRACT FOREIGN KEY (CONTRACT_ID) REFERENCES pi_contract (CONTRACT_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='개인정보 위수탁 계약 만료 알림 발송 이력';
