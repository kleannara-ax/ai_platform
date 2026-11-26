-- ============================================================
-- module-security_log : 18_dlp_kakao_approval.sql
-- DLP 모니터링 - PC 카카오톡 사용 승인 대장
--   전자결재로 승인된 카카오톡 사용 신청을 기존 엑셀 대장 대신 시스템에서 관리한다.
--   - 칸: 사업부 / 부서 / 이름 / 신청 시작일 / 신청 종료일 / 신청 상태 / 전자결재 문서번호 / 사유
--   - 신청 상태는 '신청중' 한 가지만 사용
--   - 상태(사용 중·만료·시작 전)와 최신여부는 저장하지 않고 날짜로 계산
--   - 보고서 02장·카카오톡 사용자 현황의 '사전승인'을 이 대장으로 자동 판정
--     (사람 맞추기 = 부서 + 이름, 같은 이름이 다른 부서에만 있으면 '확인 필요')
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS)
-- 주의    : DROP / TRUNCATE / DELETE / ALTER 없음, FK 없음 (core 테이블 참조 없음)
-- 선행    : 없음
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sec_log_dlp_kakao_approval (
    KAKAO_APPROVAL_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '카카오톡 사용 승인 ID',
    DIVISION_NAME      VARCHAR(200)  NULL                    COMMENT '사업부',
    DEPT_NAME          VARCHAR(200)  NOT NULL                COMMENT '부서 (DLP 유출 로그의 부서 이름과 맞춤)',
    USER_NAME          VARCHAR(200)  NOT NULL                COMMENT '이름 (DLP 유출 로그의 사용자 이름과 맞춤)',
    START_DATE         DATE          NOT NULL                COMMENT '신청 시작일',
    END_DATE           DATE          NOT NULL                COMMENT '신청 종료일',
    APPLY_STATUS       VARCHAR(20)   NOT NULL DEFAULT '신청중' COMMENT '신청 상태 (신청중 만 사용)',
    APPROVAL_DOC_NO    VARCHAR(100)  NULL                    COMMENT '전자결재 문서번호',
    REASON             VARCHAR(500)  NULL                    COMMENT '사용 사유 (보고서 02장 사유에 자동 표시)',

    CREATED_AT         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY         BIGINT        NULL                    COMMENT '생성자 ID (core 사용자 ID 값, FK 없음)',
    UPDATED_AT         DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY         BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN         CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제)',
    DELETED_AT         DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY         BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (KAKAO_APPROVAL_ID),
    INDEX IDX_SEC_LOG_DLP_KAKAO_APPR_USER (DELETED_YN, DEPT_NAME, USER_NAME),
    INDEX IDX_SEC_LOG_DLP_KAKAO_APPR_END (DELETED_YN, END_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 DLP 모니터링 - PC 카카오톡 사용 승인 대장 (전자결재 승인 건)';
