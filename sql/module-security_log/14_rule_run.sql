-- ============================================================
-- module-security_log : 14_rule_run.sql
-- 룰셋별 분석 결과 - 룰별 분석 실행 기록
--   로그 분석(업로드·재분석) 때 적용한 룰마다 1행 저장 (탐지 0건 룰 포함).
--   - 행이 있으면 그 룰로 분석함: RUN_STATUS = DONE(분석 완료) / FAILED(분석 실패)
--   - 행이 없으면 '미분석' (분석 이후 추가·사용 전환한 룰 등) → 화면에서 '검토 대상 0건'으로 표시하지 않음
--   - 분석 실패: 패턴 검사 시간 초과 줄 발생(판정 불가 줄 존재) 또는 업로드당 탐지 저장 상한 초과로 결과 미저장
--   - 재분석·업로드 삭제 시 해당 업로드의 행은 소프트 삭제
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS)
-- 주의    : DROP / TRUNCATE / DELETE / ALTER DROP COLUMN 없음, core 테이블 FK 없음
--           (FK 는 모듈 테이블 sec_log_upload 만 참조)
-- 선행    : 01 ~ 13
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 14. 룰별 분석 실행 기록
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_rule_run (
    RULE_RUN_ID     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '룰 분석 실행 ID',
    UPLOAD_ID       BIGINT        NOT NULL                COMMENT '업로드 ID (sec_log_upload.UPLOAD_ID)',
    RULE_ID         BIGINT        NOT NULL                COMMENT '룰 ID (sec_log_rule.RULE_ID 값, 룰 삭제 후에도 기록 보존 위해 FK 없음)',
    RULE_CODE       VARCHAR(50)   NOT NULL                COMMENT '분석 시점 룰 코드',
    RULE_NAME       VARCHAR(200)  NOT NULL                COMMENT '분석 시점 룰명',
    SEVERITY        VARCHAR(20)   NOT NULL                COMMENT '분석 시점 위험도 (LOW/MEDIUM/HIGH/CRITICAL)',
    RUN_STATUS      VARCHAR(20)   NOT NULL                COMMENT '분석 상태 (DONE=분석 완료 / FAILED=분석 실패)',
    MATCH_COUNT     INT           NOT NULL DEFAULT 0      COMMENT '패턴 일치 줄 수 (임계 건수 적용 전)',
    STORED_COUNT    INT           NOT NULL DEFAULT 0      COMMENT '저장한 탐지 건수 (임계 미만이면 0)',
    TIMEOUT_LINES   INT           NOT NULL DEFAULT 0      COMMENT '패턴 검사 시간 초과로 판정하지 못한 줄 수',
    FAIL_REASON     VARCHAR(500)  NULL                    COMMENT '분석 실패 사유 (분석 완료면 NULL)',

    CREATED_AT      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시 (= 분석일시)',
    CREATED_BY      BIGINT        NULL                    COMMENT '생성자 ID (core 사용자 ID 값, FK 없음)',
    UPDATED_AT      DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY      BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN      CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제 - 재분석·업로드 삭제)',
    DELETED_AT      DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY      BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (RULE_RUN_ID),
    INDEX IDX_SEC_LOG_RULE_RUN_UPLOAD (UPLOAD_ID, DELETED_YN),
    INDEX IDX_SEC_LOG_RULE_RUN_RULE (RULE_CODE),
    CONSTRAINT FK_SEC_LOG_RULE_RUN_UPLOAD FOREIGN KEY (UPLOAD_ID) REFERENCES sec_log_upload (UPLOAD_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 룰별 분석 실행 기록 (룰셋별 분석 결과의 분석 완료·실패·미분석 판정)';
