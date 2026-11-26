-- ============================================================
-- module-security_log : 15_pii_download.sql
-- 개인정보 다운로드 검토 (자사몰 SVC_MALL · 임직원몰 SVC_EMP_MALL 만 해당)
--   로그 파일(엑셀)의 '개인정보 다운로드 내역' 탭을 읽어 다운로드 1건 = 1행으로 저장하고,
--   담당자가 건마다 다운로드 사유를 확인해 검토 결과를 남긴다.
--   (개인정보의 안전성 확보조치 기준 제8조: 접속기록 월 1회 이상 점검, 다운로드 확인 시 사유 확인)
--   - 미검토 다운로드가 남아 있으면 최종 검토완료(마감) 불가
--   - 재분석·업로드 삭제 시 해당 업로드의 행은 소프트 삭제 (재분석하면 탭을 다시 읽음)
--   - sec_log_upload 에 다운로드 내역 탭 읽기 결과 컬럼 2개 추가
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS, ADD COLUMN IF NOT EXISTS)
-- 주의    : DROP / TRUNCATE / DELETE / ALTER DROP COLUMN 없음, core 테이블 FK 없음
--           (FK 는 모듈 테이블 sec_log_upload 만 참조)
-- 선행    : 01 ~ 14
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 업로드: 개인정보 다운로드 내역 탭 읽기 결과
-- ------------------------------------------------------------
ALTER TABLE sec_log_upload
    ADD COLUMN IF NOT EXISTS PII_SHEET_STATUS VARCHAR(20) NULL
        COMMENT '개인정보 다운로드 내역 탭 읽기 결과 (FOUND=탭 있음 / NOT_FOUND=탭 없음 / NULL=대상 시스템 아님 또는 15 적용 전 업로드)' AFTER OUT_OF_MONTH_COUNT;

ALTER TABLE sec_log_upload
    ADD COLUMN IF NOT EXISTS PII_SHEET_NAME VARCHAR(100) NULL
        COMMENT '읽은 개인정보 다운로드 내역 탭(시트) 이름' AFTER PII_SHEET_STATUS;

-- ------------------------------------------------------------
-- 15. 개인정보 다운로드 내역 + 검토
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_pii_download (
    PII_DOWNLOAD_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '개인정보 다운로드 ID',
    UPLOAD_ID         BIGINT        NOT NULL                COMMENT '업로드 ID (sec_log_upload.UPLOAD_ID)',
    SHEET_NAME        VARCHAR(100)  NULL                    COMMENT '원본 탭(시트) 이름',
    ROW_NO            INT           NOT NULL                COMMENT '원본 엑셀 행 번호 (텍스트 파일은 줄 번호)',
    DOWNLOAD_AT       DATETIME      NULL                    COMMENT '다운로드(접속) 일시 (읽지 못하면 NULL)',
    DOWNLOAD_AT_TEXT  VARCHAR(50)   NULL                    COMMENT '다운로드(접속) 일시 원문',
    ADMIN_ID          VARCHAR(200)  NULL                    COMMENT '운영자(관리자) ID',
    ACCESS_IP         VARCHAR(100)  NULL                    COMMENT '접속 IP',
    ACCESS_PAGE       VARCHAR(500)  NULL                    COMMENT '접속 페이지(메뉴)',
    TASK_NAME         VARCHAR(500)  NULL                    COMMENT '수행 업무',
    FILE_TARGET       VARCHAR(500)  NULL                    COMMENT '파일 대상 (다운로드한 개인정보 대상)',
    FORM_NAME         VARCHAR(500)  NULL                    COMMENT '다운로드 양식명 (파일명)',
    DOWNLOAD_REASON   VARCHAR(1000) NULL                    COMMENT '원본에 적힌 다운로드 사유 (열이 있을 때만)',
    RAW_CONTENT       VARCHAR(2000) NOT NULL                COMMENT '원본 행 내용 (탭 구분, 증적용 사본)',
    REVIEW_STATUS     VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '검토 상태 (PENDING=미검토 / NORMAL=정상 업무(사유 확인) / CONFIRMED=사유 불명확·조치 필요 / RESOLVED=조치완료)',
    REVIEW_COMMENT    VARCHAR(2000) NULL                    COMMENT '다운로드 사유·확인 내용 (미검토 외 필수)',
    REVIEWED_AT       DATETIME      NULL                    COMMENT '검토일시',
    REVIEWED_BY       BIGINT        NULL                    COMMENT '검토자 ID (core 사용자 ID 값, FK 없음)',

    CREATED_AT        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY        BIGINT        NULL                    COMMENT '생성자 ID (core 사용자 ID 값, FK 없음)',
    UPDATED_AT        DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY        BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN        CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제 - 재분석·업로드 삭제)',
    DELETED_AT        DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY        BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (PII_DOWNLOAD_ID),
    INDEX IDX_SEC_LOG_PII_DL_UPLOAD (UPLOAD_ID, DELETED_YN),
    INDEX IDX_SEC_LOG_PII_DL_REVIEW (UPLOAD_ID, REVIEW_STATUS, DELETED_YN),
    INDEX IDX_SEC_LOG_PII_DL_ADMIN (ADMIN_ID),
    CONSTRAINT FK_SEC_LOG_PII_DL_UPLOAD FOREIGN KEY (UPLOAD_ID) REFERENCES sec_log_upload (UPLOAD_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 개인정보 다운로드 내역 + 다운로드 사유 검토 (자사몰·임직원몰)';
