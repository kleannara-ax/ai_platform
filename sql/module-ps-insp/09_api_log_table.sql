-- ============================================================
-- module-ps-insp: API 통신 로그 테이블
-- PS 지분 검사 모듈의 모든 API 호출 이력을 기록
-- Database: MariaDB 10.11+ (utf8mb4)
-- Naming: table = lower_snake_case, column = UPPER_SNAKE_CASE
-- ============================================================

-- 기존 테이블 존재 시 DROP (최초 설치 또는 재생성 시)
DROP TABLE IF EXISTS ps_insp_api_log;

CREATE TABLE ps_insp_api_log (
    LOG_ID          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT 'PK (자동 증가)',

    -- ── API 식별 ──
    API             VARCHAR(50)     NOT NULL                 COMMENT 'API 유형 (INSPECTION_SAVE, INSPECTION_LIST, MES_SEND 등)',
    IP              VARCHAR(100)    NULL                     COMMENT '클라이언트 IP',

    -- ── 통신 상태 ──
    COMSTAT         CHAR(1)         NOT NULL DEFAULT 'S'     COMMENT '통신 상태 (S=Success, E=Error, P=Processing)',
    ERRTXT          VARCHAR(2000)   NULL                     COMMENT '에러 메시지 (COMSTAT=E 일 때)',

    -- ── 생성 정보 ──
    CREDAT          DATE            NOT NULL                 COMMENT '생성 날짜 (YYYY-MM-DD)',
    CRETIM          TIME            NOT NULL                 COMMENT '생성 시간 (HH:MM:SS)',
    CREUSR          VARCHAR(200)    NULL                     COMMENT '생성자 (요청 사용자 ID)',

    -- ── 비고 ──
    REMARK          VARCHAR(2000)   NULL                     COMMENT '비고/메모',

    -- ── 내부 에러 ──
    INERRAT         VARCHAR(50)     NULL                     COMMENT '내부 에러 코드 (AUTH_FAIL, DB_ERROR, MES_TIMEOUT 등)',
    INERRTXT        VARCHAR(2000)   NULL                     COMMENT '내부 에러 상세 메시지',

    -- ── 파라미터 ──
    IN_PARAMETER    TEXT            NULL                     COMMENT 'INPUT 파라미터 (요청 데이터, JSON)',
    OUT_PARAMETER   TEXT            NULL                     COMMENT 'OUTPUT 파라미터 (응답 데이터, JSON)',

    PRIMARY KEY (LOG_ID)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci
  COMMENT='[module-ps-insp] PS 지분검사 API 통신 로그';

-- ── 인덱스 ──
CREATE INDEX IDX_API_LOG_CREDAT    ON ps_insp_api_log (CREDAT DESC, CRETIM DESC);
CREATE INDEX IDX_API_LOG_API       ON ps_insp_api_log (API, CREDAT DESC);
CREATE INDEX IDX_API_LOG_COMSTAT   ON ps_insp_api_log (COMSTAT, CREDAT DESC);
CREATE INDEX IDX_API_LOG_CREUSR    ON ps_insp_api_log (CREUSR, CREDAT DESC);
