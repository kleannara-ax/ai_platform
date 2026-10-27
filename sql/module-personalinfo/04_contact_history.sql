-- ============================================================
--  module-personalinfo : 협력사 담당자 변경 이력
--  MariaDB 10.11+ (utf8mb4)
--
--  변경 내용
--    pi_contact_history : 계약별 담당자(당사 담당부서·담당자·이메일, 수탁사 담당자·이메일·연락처)
--                         변경 이력. 계약 등록 시 1건, 담당자 항목이 바뀔 때마다 변경 후 값 1건 추가.
--    기존 계약 이관      : 이력이 하나도 없는 계약에 한해 현재 담당자 값을 '기존 데이터 이관'(INITIAL)으로 1건 기록
--
--  재실행 안전: CREATE TABLE IF NOT EXISTS / INSERT ... WHERE NOT EXISTS
--  기존 데이터 영향 없음: 신규 테이블 추가 + 신규 행 추가만 함. DROP/DELETE/ALTER 없음.
--  core 테이블 FK 없음 (CREATED_BY 는 core_user ID 값만 저장, 변경자 이름은 CHANGED_BY_NAME 에 저장)
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS pi_contact_history (
    HISTORY_ID            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '담당자 이력 ID',
    CONTRACT_ID           BIGINT        NOT NULL                COMMENT '위수탁 계약 ID (pi_contract)',
    CHANGE_TYPE           VARCHAR(20)   NOT NULL DEFAULT 'UPDATE' COMMENT '이력 구분 (CREATE:계약 등록 / UPDATE:담당자 변경 / INITIAL:기존 데이터 이관)',
    VENDOR_NAME           VARCHAR(200)  NULL                    COMMENT '기록 시점 수탁업체명',
    SERVICE_NAME          VARCHAR(200)  NULL                    COMMENT '기록 시점 관련 서비스',
    DEPT_NAME             VARCHAR(100)  NULL                    COMMENT '당사 담당부서 (변경 후)',
    MANAGER_NAME          VARCHAR(100)  NULL                    COMMENT '당사 계약 담당자명 (변경 후)',
    MANAGER_EMAIL         VARCHAR(200)  NULL                    COMMENT '당사 계약 담당자 이메일 (변경 후)',
    VENDOR_CONTACT_NAME   VARCHAR(100)  NULL                    COMMENT '수탁사 담당자명 (변경 후)',
    VENDOR_CONTACT_EMAIL  VARCHAR(200)  NULL                    COMMENT '수탁사 담당자 이메일 (변경 후)',
    VENDOR_CONTACT_PHONE  VARCHAR(50)   NULL                    COMMENT '수탁사 담당자 연락처 (변경 후)',
    CHANGED_FIELDS        VARCHAR(300)  NULL                    COMMENT '바뀐 항목 코드 (쉼표 구분: DEPT_NAME,MANAGER_NAME,MANAGER_EMAIL,VENDOR_CONTACT_NAME,VENDOR_CONTACT_EMAIL,VENDOR_CONTACT_PHONE)',
    CHANGED_BY_NAME       VARCHAR(100)  NULL                    COMMENT '변경자 이름 (기록 시점 로그인 사용자 표시명)',

    CREATED_AT            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시 (변경일시)',
    CREATED_BY            BIGINT        NULL                    COMMENT '생성자 사용자 ID (변경자)',
    UPDATED_AT            DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY            BIGINT        NULL                    COMMENT '수정자 사용자 ID',
    DELETED_YN            CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부',
    DELETED_AT            DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY            BIGINT        NULL                    COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (HISTORY_ID),
    INDEX IDX_PI_CONTACT_HISTORY_CONTRACT (CONTRACT_ID, DELETED_YN, CREATED_AT),
    CONSTRAINT FK_PI_CONTACT_HISTORY_CONTRACT FOREIGN KEY (CONTRACT_ID) REFERENCES pi_contract (CONTRACT_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='개인정보 위수탁 협력사 담당자 변경 이력';

-- 기존 계약: 이력이 없는 계약만 현재 담당자 값을 1건 이관 (재실행해도 중복 생성 안 됨)
INSERT INTO pi_contact_history
    (CONTRACT_ID, CHANGE_TYPE, VENDOR_NAME, SERVICE_NAME, DEPT_NAME, MANAGER_NAME, MANAGER_EMAIL,
     VENDOR_CONTACT_NAME, VENDOR_CONTACT_EMAIL, VENDOR_CONTACT_PHONE, CHANGED_BY_NAME, CREATED_AT, CREATED_BY)
SELECT c.CONTRACT_ID, 'INITIAL', c.VENDOR_NAME, c.SERVICE_NAME, c.DEPT_NAME, c.MANAGER_NAME, c.MANAGER_EMAIL,
       c.VENDOR_CONTACT_NAME, c.VENDOR_CONTACT_EMAIL, c.VENDOR_CONTACT_PHONE, NULL,
       COALESCE(c.UPDATED_AT, c.CREATED_AT), COALESCE(c.UPDATED_BY, c.CREATED_BY)
  FROM pi_contract c
 WHERE c.DELETED_YN = 'N'
   AND NOT EXISTS (SELECT 1 FROM pi_contact_history h WHERE h.CONTRACT_ID = c.CONTRACT_ID);

-- 확인용
SELECT COUNT(*) AS contact_history_rows FROM pi_contact_history;
