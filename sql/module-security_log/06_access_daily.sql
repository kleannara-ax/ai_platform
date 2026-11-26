-- ============================================================
-- module-security_log : 06_access_daily.sql
-- 업로드(수집)별 일자 단위 집계 - 대시보드 일별 수집 현황 달력 / 일별 추이 / 월 점검 결과 내보내기
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS)
-- 주의    : DROP / TRUNCATE / DELETE / ALTER DROP COLUMN 없음, core 테이블 FK 없음
-- 선행    : 01 ~ 05
-- 참고    : 이 스크립트 적용 전에 등록된 업로드는 일별 집계가 없습니다.
--           대시보드의 [일별 집계 다시 만들기](ADMIN) 또는 재분석으로 채울 수 있습니다.
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 11. 일자별 로그 집계 (업로드 1건 x 로그 일자 1일 = 1행)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_access_daily (
    DAILY_ID              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '일별 집계 ID',
    UPLOAD_ID             BIGINT        NOT NULL                COMMENT '업로드 ID',
    SYSTEM_ID             BIGINT        NULL                    COMMENT '점검 대상 시스템 ID (조회 성능용 복사값, NULL 이면 직접 입력 시스템)',
    LOG_DATE              DATE          NOT NULL                COMMENT '로그 일자 (한국시간 기준)',
    LINE_COUNT            INT           NOT NULL DEFAULT 0      COMMENT '해당 일자 로그 라인 수 (일시를 읽을 수 있는 줄)',
    LOGIN_SUCCESS_COUNT   INT           NOT NULL DEFAULT 0      COMMENT '로그인 성공 건수',
    LOGIN_FAIL_COUNT      INT           NOT NULL DEFAULT 0      COMMENT '로그인 실패 건수',
    OFF_HOUR_COUNT        INT           NOT NULL DEFAULT 0      COMMENT '업무시간 외(22~06시) 로그인 성공 건수',
    PRIV_LOGIN_COUNT      INT           NOT NULL DEFAULT 0      COMMENT '특권/공용 계정 로그인 성공 건수',
    DETECTION_COUNT       INT           NOT NULL DEFAULT 0      COMMENT '탐지 룰 매칭 건수',

    CREATED_AT            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY            BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT            DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY            BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN            CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT            DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY            BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (DAILY_ID),
    INDEX IDX_SEC_LOG_ACCESS_DAILY_UPLOAD (UPLOAD_ID, DELETED_YN),
    INDEX IDX_SEC_LOG_ACCESS_DAILY_SYSTEM_DATE (SYSTEM_ID, LOG_DATE, DELETED_YN),
    CONSTRAINT FK_SEC_LOG_ACCESS_DAILY_UPLOAD FOREIGN KEY (UPLOAD_ID) REFERENCES sec_log_upload (UPLOAD_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 업로드별 일자 단위 집계 (일별 수집 현황·추이·증적)';
