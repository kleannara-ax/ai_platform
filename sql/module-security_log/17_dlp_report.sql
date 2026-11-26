-- ============================================================
-- module-security_log : 17_dlp_report.sql
-- DLP 모니터링 보고서 (회사 보고서 양식 8장 기준)
--   ① 유출 로그 1건마다 생성형 AI 서비스 이름 저장 (sec_log_dlp_leak_event.AI_SERVICE)
--   ② Privacy-I 재설치 / 장기 미접속 목록 파일 (DLP 관리시스템에서 받은 원본) 1행 = 1명
--   ③ 보고서에 사람이 적는 값 (표지 정보, 승인 여부·검토 결과·사전승인·사유·조치 내용·부서 인원·보안 위험도)
--
--   - 보고서 PPT 는 저장하지 않고 내려받을 때마다 위 자료로 생성
--   - 부서 인원은 다음 달에도 그대로 이어서 사용 (그 달 값이 없으면 가장 최근 달 값)
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS, ADD COLUMN IF NOT EXISTS)
-- 주의    : DROP / TRUNCATE / DELETE 없음, core 테이블 FK 없음
-- 선행    : 16_dlp_leak.sql
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 17-1. 유출 로그 생성형 AI 서비스 (URL 주소로 판정, 없으면 원본 'AI' 열 값)
-- ------------------------------------------------------------
ALTER TABLE sec_log_dlp_leak_event
    ADD COLUMN IF NOT EXISTS AI_SERVICE VARCHAR(100) NULL
        COMMENT '생성형 AI 서비스 이름 (예: ChatGPT, Gemini - 생성형 AI 사용이 아니면 NULL)' AFTER MAX_SEVERITY;

-- ------------------------------------------------------------
-- 17-2. 보고서 표지 정보 (월별 1행)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_dlp_report (
    DLP_REPORT_ID     BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'DLP 모니터링 보고서 ID',
    TARGET_YM         CHAR(6)       NOT NULL                COMMENT '대상 연월 (YYYYMM)',
    REPORT_TYPE       VARCHAR(100)  NULL                    COMMENT '보고 구분 (예: 월간 정기 보고)',
    TARGET_SYSTEM     VARCHAR(200)  NULL                    COMMENT '대상 시스템 (예: DLP (Privacy-I))',
    WRITER_DEPT       VARCHAR(200)  NULL                    COMMENT '작성 부서',
    COMPANY_KEYWORDS  VARCHAR(500)  NULL                    COMMENT '회사 식별 단어 (쉼표 구분, 생성형 AI 업로드 파일 보안 위험도 자동 제안용)',

    CREATED_AT        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY        BIGINT        NULL                    COMMENT '생성자 ID (core 사용자 ID 값, FK 없음)',
    UPDATED_AT        DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY        BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN        CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제)',
    DELETED_AT        DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY        BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (DLP_REPORT_ID),
    UNIQUE KEY UK_SEC_LOG_DLP_REPORT_YM (TARGET_YM)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 DLP 모니터링 - 보고서 표지 정보 (월별)';

-- ------------------------------------------------------------
-- 17-3. 보고서 항목별 입력값 (사람이 적는 칸)
--   SECTION: USB_UNREG(등록 안 된 외장하드 인원: VALUE1 승인여부, VALUE2 검토 결과)
--            KAKAO_USER(카카오톡 사용자: VALUE1 사전승인, VALUE2 사유)
--            DEPT_HEADCOUNT(부서 인원: VALUE1 인원 수, 다음 달에도 이어서 사용)
--            PI_REINSTALL(Privacy-I 재설치: VALUE2 검토내용)
--            PI_OFFLINE(Privacy-I 장기 미접속: VALUE1 조치 구분, VALUE2 조치 내용)
--            AI_USER(생성형 AI 업로드 사용자: VALUE1 보안 위험도 상/중/하)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_dlp_report_item (
    DLP_REPORT_ITEM_ID BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'DLP 보고서 입력값 ID',
    TARGET_YM          CHAR(6)       NOT NULL                COMMENT '대상 연월 (YYYYMM)',
    SECTION            VARCHAR(30)   NOT NULL                COMMENT '보고서 항목 (USB_UNREG/KAKAO_USER/DEPT_HEADCOUNT/PI_REINSTALL/PI_OFFLINE/AI_USER)',
    ITEM_KEY           VARCHAR(300)  NOT NULL                COMMENT '대상 (사용자 아이디·이름, 부서 이름, 목록 행 식별값)',
    VALUE1             VARCHAR(100)  NULL                    COMMENT '선택 값 (승인여부·사전승인·조치 구분·보안 위험도·부서 인원)',
    VALUE2             VARCHAR(1000) NULL                    COMMENT '글 값 (검토 결과·사유·검토내용·조치 내용)',

    CREATED_AT         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY         BIGINT        NULL                    COMMENT '생성자 ID',
    UPDATED_AT         DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY         BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN         CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제)',
    DELETED_AT         DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY         BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (DLP_REPORT_ITEM_ID),
    UNIQUE KEY UK_SEC_LOG_DLP_REPORT_ITEM (TARGET_YM, SECTION, ITEM_KEY),
    INDEX IDX_SEC_LOG_DLP_REPORT_ITEM_SEC (SECTION, TARGET_YM)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 DLP 모니터링 - 보고서 항목별 입력값';

-- ------------------------------------------------------------
-- 17-4. Privacy-I 재설치 / 장기 미접속 목록 (파일 1행 = 1명, 다시 올리면 이전 행 소프트 삭제)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_dlp_privacyi_row (
    PRIVACYI_ROW_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Privacy-I 목록 행 ID',
    TARGET_YM         CHAR(6)       NOT NULL                COMMENT '대상 연월 (YYYYMM)',
    KIND              VARCHAR(20)   NOT NULL                COMMENT '목록 종류 (REINSTALL 재설치 / OFFLINE 장기 미접속)',
    FILE_NAME         VARCHAR(255)  NOT NULL                COMMENT '올린 파일명',
    ROW_NO            INT           NOT NULL                COMMENT '원본 행 번호',
    DEPT_NAME         VARCHAR(200)  NULL                    COMMENT '부서 이름',
    USER_NAME         VARCHAR(200)  NULL                    COMMENT '사용자 이름',
    USER_ID           VARCHAR(200)  NULL                    COMMENT '사용자 아이디',
    EVENT_AT          DATETIME      NULL                    COMMENT '설치 일시(재설치) / 최종 오프라인 일시(장기 미접속)',
    EVENT_AT_TEXT     VARCHAR(50)   NULL                    COMMENT '일시 원문',
    OFFLINE_DAYS      INT           NULL                    COMMENT '미접속일 (장기 미접속만, 파일 값 또는 월말 기준 계산)',

    CREATED_AT        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY        BIGINT        NULL                    COMMENT '생성자 ID',
    UPDATED_AT        DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY        BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN        CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제 - 다시 올리기)',
    DELETED_AT        DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY        BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (PRIVACYI_ROW_ID),
    INDEX IDX_SEC_LOG_DLP_PRIVACYI (TARGET_YM, KIND, DELETED_YN, ROW_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 DLP 모니터링 - Privacy-I 재설치·장기 미접속 목록';
