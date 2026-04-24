-- ============================================================
-- module-ps-insp: API 통신 로그 테이블
-- PS 지분 검사 모듈의 모든 API 호출 이력을 기록
-- Database: MariaDB 10.11+ (utf8mb4)
-- Naming: table = lower_snake_case, column = UPPER_SNAKE_CASE
-- ============================================================

CREATE TABLE IF NOT EXISTS ps_insp_api_log (
    LOG_ID          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT 'PK (자동 증가)',

    -- ── 통신 구분 ──
    DIRECTION       VARCHAR(10)     NOT NULL DEFAULT 'IN'    COMMENT '통신 방향 (IN=수신요청, OUT=외부발신)',
    API_TYPE        VARCHAR(50)     NOT NULL                 COMMENT 'API 유형 (INSPECTION_SAVE, INSPECTION_LIST, MES_SEND 등)',

    -- ── 요청 정보 ──
    HTTP_METHOD     VARCHAR(10)     NOT NULL                 COMMENT 'HTTP 메서드 (GET/POST/DELETE)',
    REQUEST_URI     VARCHAR(1000)   NOT NULL                 COMMENT '요청 URI (/ps-insp-api/...)',
    QUERY_STRING    VARCHAR(2000)   NULL                     COMMENT '쿼리 파라미터 (?page=0&size=20)',
    TARGET_URL      VARCHAR(2000)   NULL                     COMMENT '외부 전송 대상 URL (OUT일 때만)',
    REQUEST_BODY    TEXT            NULL                     COMMENT '요청 Body (JSON, 이미지 바이너리 제외)',

    -- ── 응답 정보 ──
    HTTP_STATUS     INT             NULL                     COMMENT 'HTTP 응답 상태 코드 (200, 401, 500 등)',
    SUCCESS         TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '성공 여부 (1=성공, 0=실패)',
    RESPONSE_BODY   TEXT            NULL                     COMMENT '응답 Body 요약 (JSON, 최대 64KB)',
    ERROR_MESSAGE   VARCHAR(2000)   NULL                     COMMENT '실패 시 에러 메시지',

    -- ── 검사 연관 정보 (빠른 필터링용) ──
    IND_BCD         VARCHAR(200)    NULL                     COMMENT '개별바코드',
    MATNR           VARCHAR(100)    NULL                     COMMENT '자재코드',
    LOTNR           VARCHAR(200)    NULL                     COMMENT 'LOT 번호',

    -- ── 사용자/클라이언트 ──
    USER_ID         VARCHAR(200)    NULL                     COMMENT '요청 사용자 ID (USERID 파라미터 또는 JWT 사용자)',
    CLIENT_IP       VARCHAR(100)    NULL                     COMMENT '클라이언트 IP',

    -- ── 성능 ──
    ELAPSED_MS      BIGINT          NULL DEFAULT 0           COMMENT '처리 시간 (밀리초)',

    -- ── 시각 ──
    REQUESTED_AT    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '요청 시각 (밀리초 정밀도)',
    CREATED_AT      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '레코드 생성일시',

    PRIMARY KEY (LOG_ID)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci
  COMMENT='[module-ps-insp] PS 지분검사 API 통신 로그';

-- ── 인덱스 ──
CREATE INDEX IDX_API_LOG_REQUESTED_AT  ON ps_insp_api_log (REQUESTED_AT DESC);
CREATE INDEX IDX_API_LOG_API_TYPE      ON ps_insp_api_log (API_TYPE, REQUESTED_AT DESC);
CREATE INDEX IDX_API_LOG_DIRECTION     ON ps_insp_api_log (DIRECTION, REQUESTED_AT DESC);
CREATE INDEX IDX_API_LOG_IND_BCD      ON ps_insp_api_log (IND_BCD);
CREATE INDEX IDX_API_LOG_USER_ID      ON ps_insp_api_log (USER_ID, REQUESTED_AT DESC);
CREATE INDEX IDX_API_LOG_SUCCESS      ON ps_insp_api_log (SUCCESS, REQUESTED_AT DESC);
