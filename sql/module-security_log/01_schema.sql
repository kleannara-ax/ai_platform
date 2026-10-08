-- ============================================================
-- module-security_log : 01_schema.sql
-- ISMS 보안 로그 이상징후 탐지/검토 모듈 DDL
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS)
-- 주의    : DROP / TRUNCATE / DELETE / ALTER DROP COLUMN 없음
--           core 테이블(core_user, core_menu, core_role 등) FK 없음
--           사용자 ID 는 값만 저장 (CREATED_BY, UPDATED_BY, DELETED_BY, REVIEWED_BY)
-- 생성순서: sec_log_rule → sec_log_upload → sec_log_upload_file → sec_log_detection
-- ============================================================

-- 클라이언트 문자셋을 utf8mb4 로 고정 (한글 COMMENT/데이터 깨짐 방지)
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 이상징후 탐지 룰셋
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_rule (
    RULE_ID          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '룰 ID',
    RULE_CODE        VARCHAR(50)   NOT NULL                COMMENT '룰 코드 (영문대문자/숫자/_ , 변경 불가)',
    RULE_NAME        VARCHAR(200)  NOT NULL                COMMENT '룰명',
    DESCRIPTION      VARCHAR(1000) NULL                    COMMENT '룰 설명 및 탐지 근거',
    LOG_TYPE         VARCHAR(20)   NOT NULL DEFAULT 'ALL'  COMMENT '적용 로그 유형 (ALL/LINUX/WINDOWS/WEB/WAS/DB/NETWORK/APP/ETC)',
    MATCH_TYPE       VARCHAR(20)   NOT NULL DEFAULT 'KEYWORD' COMMENT '매칭 방식 (KEYWORD: 쉼표구분 키워드 포함, REGEX: 정규식)',
    PATTERN          VARCHAR(1000) NOT NULL                COMMENT '탐지 패턴 (키워드 목록 또는 정규식)',
    SEVERITY         VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM' COMMENT '위험도 (LOW/MEDIUM/HIGH/CRITICAL)',
    THRESHOLD_COUNT  INT           NOT NULL DEFAULT 1      COMMENT '임계 건수 (파일 내 매칭 건수가 이 값 이상일 때 탐지)',
    USE_YN           CHAR(1)       NOT NULL DEFAULT 'Y'    COMMENT '사용 여부 (Y/N)',
    SORT_ORDER       INT           NOT NULL DEFAULT 0      COMMENT '정렬 순서',

    CREATED_AT       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY       BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT       DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY       BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN       CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT       DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY       BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (RULE_ID),
    CONSTRAINT UK_SEC_LOG_RULE_CODE UNIQUE (RULE_CODE),
    INDEX IDX_SEC_LOG_RULE_ACTIVE (DELETED_YN, USE_YN, LOG_TYPE, SORT_ORDER),
    INDEX IDX_SEC_LOG_RULE_NAME (RULE_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 이상징후 탐지 룰셋';

-- ------------------------------------------------------------
-- 2. 로그 업로드 이력 (분석 결과 요약)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_upload (
    UPLOAD_ID           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '업로드 ID',
    TARGET_YM           CHAR(6)       NOT NULL                COMMENT '대상 연월 (YYYYMM, 월별 조회 기준)',
    SYSTEM_NAME         VARCHAR(100)  NOT NULL                COMMENT '대상 시스템명',
    LOG_TYPE            VARCHAR(20)   NOT NULL                COMMENT '로그 유형 (LINUX/WINDOWS/WEB/WAS/DB/NETWORK/APP/ETC)',
    ORIGINAL_FILE_NAME  VARCHAR(255)  NOT NULL                COMMENT '원본 파일명',
    FILE_SIZE           BIGINT        NOT NULL DEFAULT 0      COMMENT '원본 파일 크기 (byte)',
    FILE_HASH           CHAR(64)      NOT NULL                COMMENT '원본 파일 SHA-256 해시 (무결성/중복 확인)',
    FILE_CHARSET        VARCHAR(20)   NOT NULL DEFAULT 'UTF-8' COMMENT '파일 인코딩 (UTF-8/EUC-KR/MS949)',
    TOTAL_LINE_COUNT    INT           NOT NULL DEFAULT 0      COMMENT '전체 로그 라인 수',
    DETECTION_COUNT     INT           NOT NULL DEFAULT 0      COMMENT '탐지 건수 합계',
    CRITICAL_COUNT      INT           NOT NULL DEFAULT 0      COMMENT '심각(CRITICAL) 탐지 건수',
    HIGH_COUNT          INT           NOT NULL DEFAULT 0      COMMENT '높음(HIGH) 탐지 건수',
    MEDIUM_COUNT        INT           NOT NULL DEFAULT 0      COMMENT '보통(MEDIUM) 탐지 건수',
    LOW_COUNT           INT           NOT NULL DEFAULT 0      COMMENT '낮음(LOW) 탐지 건수',
    MAX_SEVERITY        VARCHAR(20)   NULL                    COMMENT '최고 위험도 (탐지 없으면 NULL)',
    STATUS              VARCHAR(20)   NOT NULL DEFAULT 'ANALYZED' COMMENT '처리 상태 (ANALYZED: 검토대기, REVIEWED: 검토완료)',
    ANALYZED_AT         DATETIME      NULL                    COMMENT '분석 일시',
    REMARK              VARCHAR(1000) NULL                    COMMENT '업로드 비고',
    REVIEW_COMMENT      VARCHAR(2000) NULL                    COMMENT '정보보안팀 최종 검토 의견',
    REVIEWED_AT         DATETIME      NULL                    COMMENT '최종 검토 일시',
    REVIEWED_BY         BIGINT        NULL                    COMMENT '최종 검토자 사용자 ID',

    CREATED_AT          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY          BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT          DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY          BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN          CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT          DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY          BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (UPLOAD_ID),
    INDEX IDX_SEC_LOG_UPLOAD_YM (DELETED_YN, TARGET_YM, STATUS),
    INDEX IDX_SEC_LOG_UPLOAD_SYSTEM (TARGET_YM, SYSTEM_NAME),
    INDEX IDX_SEC_LOG_UPLOAD_HASH (FILE_HASH)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 업로드 이력 및 분석 요약';

-- ------------------------------------------------------------
-- 3. 업로드 원본 파일 (GZIP 압축 보관 - ISMS 증적 / 재분석용)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_upload_file (
    UPLOAD_FILE_ID   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '업로드 파일 ID',
    UPLOAD_ID        BIGINT       NOT NULL                COMMENT '업로드 ID',
    COMPRESS_TYPE    VARCHAR(10)  NOT NULL DEFAULT 'GZIP' COMMENT '압축 방식',
    COMPRESSED_SIZE  BIGINT       NOT NULL DEFAULT 0      COMMENT '압축 후 크기 (byte)',
    FILE_DATA        LONGBLOB     NOT NULL                COMMENT '압축된 원본 로그 데이터',

    CREATED_AT       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY       BIGINT       NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT       DATETIME     NULL COMMENT '수정일시',
    UPDATED_BY       BIGINT       NULL COMMENT '수정자 사용자 ID',
    DELETED_YN       CHAR(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT       DATETIME     NULL COMMENT '삭제일시',
    DELETED_BY       BIGINT       NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (UPLOAD_FILE_ID),
    INDEX IDX_SEC_LOG_UPLOAD_FILE_UPLOAD (UPLOAD_ID, DELETED_YN),
    CONSTRAINT FK_SEC_LOG_UPLOAD_FILE_UPLOAD FOREIGN KEY (UPLOAD_ID) REFERENCES sec_log_upload (UPLOAD_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 업로드 원본 파일';

-- ------------------------------------------------------------
-- 4. 이상징후 탐지 결과
--    RULE_CODE/RULE_NAME/SEVERITY 는 탐지 시점 스냅샷
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_detection (
    DETECTION_ID     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '탐지 ID',
    UPLOAD_ID        BIGINT        NOT NULL                COMMENT '업로드 ID',
    RULE_ID          BIGINT        NOT NULL                COMMENT '탐지 룰 ID',
    RULE_CODE        VARCHAR(50)   NOT NULL                COMMENT '탐지 시점 룰 코드',
    RULE_NAME        VARCHAR(200)  NOT NULL                COMMENT '탐지 시점 룰명',
    SEVERITY         VARCHAR(20)   NOT NULL                COMMENT '탐지 시점 위험도',
    LINE_NUMBER      INT           NOT NULL                COMMENT '로그 라인 번호',
    LOG_CONTENT      VARCHAR(2000) NOT NULL                COMMENT '탐지된 로그 원문 (최대 2000자)',
    REVIEW_STATUS    VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '검토 상태 (PENDING/CONFIRMED/RESOLVED/FALSE_POSITIVE)',
    ACTION_CONTENT   VARCHAR(2000) NULL                    COMMENT '검토 의견 및 조치 내용',
    REVIEWED_AT      DATETIME      NULL                    COMMENT '검토 일시',
    REVIEWED_BY      BIGINT        NULL                    COMMENT '검토자 사용자 ID',

    CREATED_AT       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY       BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT       DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY       BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN       CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT       DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY       BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (DETECTION_ID),
    INDEX IDX_SEC_LOG_DETECTION_UPLOAD (UPLOAD_ID, DELETED_YN, SEVERITY, REVIEW_STATUS),
    INDEX IDX_SEC_LOG_DETECTION_RULE (RULE_ID),
    CONSTRAINT FK_SEC_LOG_DETECTION_UPLOAD FOREIGN KEY (UPLOAD_ID) REFERENCES sec_log_upload (UPLOAD_ID),
    CONSTRAINT FK_SEC_LOG_DETECTION_RULE   FOREIGN KEY (RULE_ID)   REFERENCES sec_log_rule (RULE_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 이상징후 탐지 결과';
