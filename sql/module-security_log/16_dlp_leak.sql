-- ============================================================
-- module-security_log : 16_dlp_leak.sql
-- DLP 모니터링 (ISMS 로그 점검과 별도의 독립 메뉴, 월 1회)
--   ① DLP 관리시스템에서 내려받은 '유출 로그' 엑셀/CSV 를 [DLP 모니터링] 화면에서 올린다.
--   ② 올리면 바로 분석한다. (생성형 AI 판정 등. 룰셋 없음 — 룰 기반 점검은 ISMS 로그 점검에서 진행)
--   ③ [보고서 작성] 화면에서 입력 후 DLP 모니터링 보고서(PPT)를 만든다. (17_dlp_report.sql)
--
--   - 1행 = 유출 로그 1건 (sec_log_dlp_leak_event), 원본은 GZIP 보관 (sec_log_dlp_leak_file, 재분석용)
--   - 원본의 '... 샘플 데이터' 열(주민등록번호 등 개인정보 원문 일부)은 저장하지 않는다. (개수 열만 합계)
--   - 같은 달 다시 올리면 가장 최근 파일 1개만 집계 (이전 파일은 보관만)
--   - 재분석·업로드 삭제 시 이벤트는 소프트 삭제
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS)
-- 참고    : 이전 버전에서 만든 sec_log_dlp_rule(DLP 룰셋) 테이블은 더 이상 쓰지 않음.
--           DROP 하지 않고 그대로 둠 (데이터 보존). 이 파일은 새로 만들지 않음.
-- 주의    : DROP / TRUNCATE / DELETE / ALTER 없음, core 테이블 FK 없음
--           (FK 는 모듈 테이블 sec_log_dlp_leak_upload 만 참조)
-- 선행    : 없음 (다른 sec_log 테이블을 참조하지 않음)
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 16-1. DLP 유출 로그 업로드 (월별, 같은 달은 최신 파일만 집계)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_dlp_leak_upload (
    LEAK_UPLOAD_ID       BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'DLP 유출 로그 업로드 ID',
    TARGET_YM            CHAR(6)       NOT NULL                COMMENT '대상 연월 (YYYYMM)',
    ORIGINAL_FILE_NAME   VARCHAR(255)  NOT NULL                COMMENT '원본 파일명',
    FILE_SIZE            BIGINT        NOT NULL                COMMENT '원본 파일 크기 (byte)',
    FILE_HASH            CHAR(64)      NOT NULL                COMMENT '원본 파일 SHA-256 (같은 달 중복 업로드 방지)',
    SHEET_NAME           VARCHAR(100)  NULL                    COMMENT '읽은 시트(탭) 이름 (CSV 는 NULL)',
    HEADER_ROW           INT           NULL                    COMMENT '머리글 행 번호 (원본 기준)',
    EVENT_COUNT          INT           NOT NULL DEFAULT 0      COMMENT '유출 이벤트 수 (데이터 행 수)',
    MATCHED_EVENT_COUNT  INT           NOT NULL DEFAULT 0      COMMENT '(미사용 - 룰셋 삭제 후 0) 이전 버전의 룰 해당 이벤트 수',
    USER_COUNT           INT           NOT NULL DEFAULT 0      COMMENT '사용자 수 (사용자 아이디 기준, 없으면 이름)',
    PERIOD_FROM          DATETIME      NULL                    COMMENT '첫 발생 일시',
    PERIOD_TO            DATETIME      NULL                    COMMENT '마지막 발생 일시',
    DATE_UNKNOWN_COUNT   INT           NOT NULL DEFAULT 0      COMMENT '발생 일시를 읽지 못한 행 수',
    OUT_OF_MONTH_COUNT   INT           NOT NULL DEFAULT 0      COMMENT '대상 연월 밖 발생 일시 행 수',
    APPLIED_RULE_COUNT   INT           NOT NULL DEFAULT 0      COMMENT '(미사용 - 룰셋 삭제 후 0) 이전 버전의 적용 룰 수',
    ANALYSIS_NOTE        VARCHAR(1000) NULL                    COMMENT '분석 참고 (패턴 검사 시간 초과 등)',
    ANALYZED_AT          DATETIME      NULL                    COMMENT '분석 일시',
    REMARK               VARCHAR(1000) NULL                    COMMENT '비고',

    CREATED_AT           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY           BIGINT        NULL                    COMMENT '생성자 ID (core 사용자 ID 값, FK 없음)',
    UPDATED_AT           DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY           BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN           CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제)',
    DELETED_AT           DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY           BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (LEAK_UPLOAD_ID),
    INDEX IDX_SEC_LOG_DLP_LEAK_UP_YM (TARGET_YM, DELETED_YN),
    INDEX IDX_SEC_LOG_DLP_LEAK_UP_HASH (TARGET_YM, FILE_HASH, DELETED_YN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 DLP 모니터링 - 유출 로그 업로드 (월 1회)';

-- ------------------------------------------------------------
-- 16-2. DLP 유출 로그 원본 파일 (GZIP)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_dlp_leak_file (
    LEAK_FILE_ID     BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'DLP 유출 로그 원본 파일 ID',
    LEAK_UPLOAD_ID   BIGINT       NOT NULL                COMMENT 'DLP 유출 로그 업로드 ID',
    COMPRESS_TYPE    VARCHAR(10)  NOT NULL DEFAULT 'GZIP' COMMENT '압축 방식',
    COMPRESSED_SIZE  BIGINT       NOT NULL                COMMENT '압축 후 크기 (byte)',
    FILE_DATA        LONGBLOB     NOT NULL                COMMENT '원본 파일 (GZIP 압축)',

    CREATED_AT       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY       BIGINT       NULL                    COMMENT '생성자 ID',
    UPDATED_AT       DATETIME     NULL                    COMMENT '수정일시',
    UPDATED_BY       BIGINT       NULL                    COMMENT '수정자 ID',
    DELETED_YN       CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제)',
    DELETED_AT       DATETIME     NULL                    COMMENT '삭제일시',
    DELETED_BY       BIGINT       NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (LEAK_FILE_ID),
    INDEX IDX_SEC_LOG_DLP_LEAK_FILE_UP (LEAK_UPLOAD_ID, DELETED_YN),
    CONSTRAINT FK_SEC_LOG_DLP_LEAK_FILE_UP FOREIGN KEY (LEAK_UPLOAD_ID) REFERENCES sec_log_dlp_leak_upload (LEAK_UPLOAD_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 DLP 모니터링 - 유출 로그 원본 파일 (증적·재분석용)';

-- ------------------------------------------------------------
-- 16-3. DLP 유출 이벤트 (원본 1행 = 1건, 생성형 AI 판정은 17번에서 AI_SERVICE 칸 추가)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_dlp_leak_event (
    LEAK_EVENT_ID     BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'DLP 유출 이벤트 ID',
    LEAK_UPLOAD_ID    BIGINT        NOT NULL                COMMENT 'DLP 유출 로그 업로드 ID',
    ROW_NO            INT           NOT NULL                COMMENT '원본 행 번호 (엑셀 행 / CSV 줄)',
    OCCURRED_AT       DATETIME      NULL                    COMMENT '발생 일시 (읽지 못하면 NULL)',
    OCCURRED_AT_TEXT  VARCHAR(50)   NULL                    COMMENT '발생 일시 원문',
    ACTION_NAME       VARCHAR(100)  NULL                    COMMENT '대응 행동 (예: 허용(정책), 차단)',
    DEPT_NAME         VARCHAR(200)  NULL                    COMMENT '부서 이름',
    USER_NAME         VARCHAR(200)  NULL                    COMMENT '사용자 이름',
    USER_ID           VARCHAR(200)  NULL                    COMMENT '사용자 아이디',
    AGENT_IP          VARCHAR(100)  NULL                    COMMENT '에이전트 IP',
    CHANNEL           VARCHAR(100)  NULL                    COMMENT '채널 (예: Upload, USB, 메일, 출력)',
    SUB_CHANNEL       VARCHAR(200)  NULL                    COMMENT '서브 채널',
    FILE_SIZE_TEXT    VARCHAR(50)   NULL                    COMMENT '파일 크기 원문 (예: 196.96KB)',
    FILE_LIST         VARCHAR(1000) NULL                    COMMENT '파일 목록',
    CONTENT           VARCHAR(2000) NULL                    COMMENT '내용',
    DETAIL            VARCHAR(1000) NULL                    COMMENT '상세 내용 (예: 전송 URL)',
    POLICY_NAME       VARCHAR(200)  NULL                    COMMENT '정책 이름',
    EVENT_STATUS      VARCHAR(50)   NULL                    COMMENT '상태 (예: 신규)',
    MEDIA_REGISTERED  VARCHAR(50)   NULL                    COMMENT '이동식 저장 매체 등록 여부',
    COMPUTER_NAME     VARCHAR(200)  NULL                    COMMENT '컴퓨터 이름',
    PII_TOTAL         INT           NOT NULL DEFAULT 0      COMMENT '개인정보 검출 개수 합계 (주민등록번호·핸드폰 번호 등 개수 열 합)',
    PII_DETAIL        VARCHAR(500)  NULL                    COMMENT '개인정보 검출 항목별 개수 (예: 주민 등록 번호 2, 핸드폰 번호 3) - 샘플 데이터는 저장 안 함',
    MATCHED_RULES     VARCHAR(1000) NULL                    COMMENT '(미사용 - 룰셋 삭제 후 NULL) 이전 버전의 해당 룰 코드',
    MAX_SEVERITY      VARCHAR(20)   NULL                    COMMENT '(미사용 - 룰셋 삭제 후 NULL) 이전 버전의 최고 위험도',
    ANALYSIS_LINE     TEXT          NOT NULL                COMMENT '원본 행 내용 (열 이름=값 | ..., 샘플 데이터 열 제외)',

    CREATED_AT        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY        BIGINT        NULL                    COMMENT '생성자 ID',
    UPDATED_AT        DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY        BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN        CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제 - 재분석·업로드 삭제)',
    DELETED_AT        DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY        BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (LEAK_EVENT_ID),
    INDEX IDX_SEC_LOG_DLP_LEAK_EV_UP (LEAK_UPLOAD_ID, DELETED_YN, ROW_NO),
    INDEX IDX_SEC_LOG_DLP_LEAK_EV_USER (LEAK_UPLOAD_ID, USER_ID),
    CONSTRAINT FK_SEC_LOG_DLP_LEAK_EV_UP FOREIGN KEY (LEAK_UPLOAD_ID) REFERENCES sec_log_dlp_leak_upload (LEAK_UPLOAD_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 DLP 모니터링 - 유출 로그 (원본 1행 = 1건)';
