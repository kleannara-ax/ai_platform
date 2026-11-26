-- ============================================================
-- module-security_log : 12_account_review.sql
-- 접속 행위 분석 > 계정별 검토 (담당자 소명·조치 기록)
--   업로드(로그 파일) 1건의 접속 계정마다 검토 결과를 남긴다.
--   - 위험 표시가 있는 계정(로그인 실패 5회 이상, 업무시간 외, 외부 IP, 허용 외 IP, 특권/공용 계정, 다중 IP)은
--     모두 검토해야 '분석 결과 검토 완료'와 최종 검토완료(마감)를 할 수 있다.
--   - 위험 표시가 없는 계정은 원하면 기록한다(선택).
--   - 검토 행이 없으면 '미검토'. 재분석·업로드 삭제 시 해당 업로드의 검토 행은 소프트 삭제된다.
--   - 검토자: ROLE_ADMIN, ROLE_MANAGER (모든 시스템, 권한 기준 - 매핑안 A)
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS)
-- 주의    : DROP / TRUNCATE / DELETE / ALTER DROP COLUMN 없음, core 테이블 FK 없음
--           (FK 는 모듈 테이블 sec_log_upload 만 참조)
-- 선행    : 01 ~ 11
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 13. 접속 계정 검토
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_account_review (
    ACCOUNT_REVIEW_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '계정 검토 ID',
    UPLOAD_ID          BIGINT        NOT NULL                COMMENT '업로드 ID (sec_log_upload.UPLOAD_ID)',
    ACCOUNT_ID         VARCHAR(200)  NOT NULL                COMMENT '접속 계정 ID (sec_log_access_summary.ACCOUNT_ID 와 같은 값)',
    REVIEW_STATUS      VARCHAR(20)   NOT NULL                COMMENT '검토 상태 (NORMAL=정상 확인 / CONFIRMED=이상 확인(조치 필요) / RESOLVED=조치완료)',
    REVIEW_COMMENT     VARCHAR(2000) NULL                    COMMENT '소명·조치 내용 (이상 확인·조치완료는 필수)',
    RISK_FLAGS         VARCHAR(200)  NULL                    COMMENT '검토 당시 위험 표시 (쉼표 구분, 증적용 사본)',
    REVIEWED_AT        DATETIME      NOT NULL                COMMENT '검토일시',
    REVIEWED_BY        BIGINT        NULL                    COMMENT '검토자 ID (core 사용자 ID 값, FK 없음)',

    CREATED_AT         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY         BIGINT        NULL                    COMMENT '생성자 ID (core 사용자 ID 값, FK 없음)',
    UPDATED_AT         DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY         BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN         CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제 - 미검토로 되돌리기·재분석·업로드 삭제)',
    DELETED_AT         DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY         BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (ACCOUNT_REVIEW_ID),
    INDEX IDX_SEC_LOG_ACC_REVIEW_UPLOAD (UPLOAD_ID, DELETED_YN),
    INDEX IDX_SEC_LOG_ACC_REVIEW_ACCOUNT (ACCOUNT_ID),
    CONSTRAINT FK_SEC_LOG_ACC_REVIEW_UPLOAD FOREIGN KEY (UPLOAD_ID) REFERENCES sec_log_upload (UPLOAD_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 접속 계정 검토 (접속 행위 분석의 계정별 소명·조치 기록)';
