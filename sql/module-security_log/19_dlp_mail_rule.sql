-- ============================================================
-- module-security_log : 19_dlp_mail_rule.sql
-- DLP 관리 > 메일 알림 관리 - 카카오톡 승인 대장 신청 종료일 기준 메일 알림 규칙 (여러 개)
--   - 규칙마다: 신청 종료일 + N일(음수 = 종료일 전), 매일 보내는 시각, 받는 사람·참조, 제목·본문(자리표시 사용)
--   - 대상: 같은 사람(부서+이름)의 최신 '신청중' 건 (연장 등록하면 이전 건은 보내지 않음)
--   - 규칙 1개 x 승인 건 1개당 한 번만 보냄 (보낸 기록 = sec_log_dlp_mail_send_log), 실패는 다음 실행 때 다시 시도
--   - 규칙의 보내기 시작일(START_DATE) 전에 이미 날짜가 지난 건은 보내지 않음 (처음 켤 때 한꺼번에 나가지 않도록)
--   - 메일 서버(SMTP) 정보는 DB·설정 파일에 넣지 않고 서버 환경 변수로 넣음 (README 8-2)
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS, INSERT IGNORE + UK_SEC_LOG_DLP_MAIL_RULE_KEY)
--           화면에서 바꾼 규칙은 덮어쓰지 않음
-- 주의    : DROP / TRUNCATE / DELETE / ALTER 없음, core 테이블 FK 없음
--           (FK 는 모듈 테이블 sec_log_dlp_mail_rule, sec_log_dlp_kakao_approval 만 참조)
-- 선행    : 18_dlp_kakao_approval.sql
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 19-1. 메일 알림 규칙
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_dlp_mail_rule (
    MAIL_RULE_ID      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '메일 알림 규칙 ID',
    RULE_KEY          VARCHAR(50)   NULL                    COMMENT '기본 규칙 구분값 (SQL 로 넣은 기본 규칙만 값 있음, 화면에서 만든 규칙은 NULL)',
    RULE_NAME         VARCHAR(100)  NOT NULL                COMMENT '규칙 이름',
    OFFSET_DAYS       INT           NOT NULL                COMMENT '보내는 날 = 신청 종료일 + N일 (음수 = 종료일 전, 0 = 종료일 당일)',
    SEND_TIME         CHAR(5)       NOT NULL                COMMENT '매일 보내는 시각 (HH:MM, 한국시간)',
    TO_ADDRS          VARCHAR(1000) NOT NULL                COMMENT '받는 사람 (쉼표 구분, 회사 메일만 - 비어 있으면 규칙을 켤 수 없음)',
    CC_ADDRS          VARCHAR(1000) NULL                    COMMENT '참조 (쉼표 구분)',
    SUBJECT_TPL       VARCHAR(300)  NOT NULL                COMMENT '메일 제목 (자리표시 {부서} {이름} 등 사용)',
    BODY_TPL          VARCHAR(2000) NOT NULL                COMMENT '메일 본문 (자리표시 {부서} {이름} 등 사용)',
    USE_YN            CHAR(1)       NOT NULL DEFAULT 'Y'    COMMENT '사용 여부 (Y/N)',
    START_DATE        DATE          NOT NULL                COMMENT '보내기 시작일 (보내는 날이 이 날 이후인 건만 발송, 규칙을 만든 날)',
    LAST_RUN_AT       DATETIME      NULL                    COMMENT '마지막 실행 일시 (자동·지금 보내기)',
    LAST_RUN_RESULT   VARCHAR(500)  NULL                    COMMENT '마지막 실행 결과 한 줄',

    CREATED_AT        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY        BIGINT        NULL                    COMMENT '생성자 ID (core 사용자 ID 값, FK 없음)',
    UPDATED_AT        DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY        BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN        CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제)',
    DELETED_AT        DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY        BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (MAIL_RULE_ID),
    UNIQUE KEY UK_SEC_LOG_DLP_MAIL_RULE_KEY (RULE_KEY),
    INDEX IDX_SEC_LOG_DLP_MAIL_RULE_USE (DELETED_YN, USE_YN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 DLP 관리 - 메일 알림 규칙 (카카오톡 승인 대장 신청 종료일 기준)';

-- ------------------------------------------------------------
-- 19-2. 메일 알림 보낸 기록 (규칙 x 승인 건당 1행, 재시도 시 같은 행을 고침)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_dlp_mail_send_log (
    MAIL_SEND_LOG_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '메일 알림 보낸 기록 ID',
    MAIL_RULE_ID       BIGINT        NOT NULL                COMMENT '메일 알림 규칙 ID (sec_log_dlp_mail_rule)',
    KAKAO_APPROVAL_ID  BIGINT        NOT NULL                COMMENT '승인 대장 ID (sec_log_dlp_kakao_approval)',
    DEPT_NAME          VARCHAR(200)  NOT NULL                COMMENT '부서 (보낼 때 값)',
    USER_NAME          VARCHAR(200)  NOT NULL                COMMENT '이름 (보낼 때 값)',
    END_DATE           DATE          NOT NULL                COMMENT '신청 종료일 (보낼 때 값)',
    TO_ADDRS           VARCHAR(1000) NOT NULL                COMMENT '받는 사람 (보낼 때 값)',
    CC_ADDRS           VARCHAR(1000) NULL                    COMMENT '참조 (보낼 때 값)',
    SUBJECT            VARCHAR(500)  NOT NULL                COMMENT '메일 제목 (자리표시를 채운 값)',
    BODY               VARCHAR(4000) NOT NULL                COMMENT '메일 본문 (자리표시를 채운 값)',
    SEND_STATUS        VARCHAR(10)   NOT NULL                COMMENT '발송 결과 (SENT 보냄 / FAIL 실패 - 다음 실행 때 다시 시도)',
    TRY_COUNT          INT           NOT NULL DEFAULT 0      COMMENT '시도 횟수',
    SENT_AT            DATETIME      NULL                    COMMENT '보낸 일시 (성공 시)',
    LAST_TRY_AT        DATETIME      NULL                    COMMENT '마지막 시도 일시',
    ERROR_MESSAGE      VARCHAR(1000) NULL                    COMMENT '마지막 실패 사유',

    CREATED_AT         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY         BIGINT        NULL                    COMMENT '생성자 ID (자동 발송은 NULL)',
    UPDATED_AT         DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY         BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN         CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제)',
    DELETED_AT         DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY         BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (MAIL_SEND_LOG_ID),
    UNIQUE KEY UK_SEC_LOG_DLP_MAIL_SEND_RULE_APPR (MAIL_RULE_ID, KAKAO_APPROVAL_ID),
    INDEX IDX_SEC_LOG_DLP_MAIL_SEND_TRY (DELETED_YN, LAST_TRY_AT),
    CONSTRAINT FK_SEC_LOG_DLP_MAIL_SEND_RULE FOREIGN KEY (MAIL_RULE_ID) REFERENCES sec_log_dlp_mail_rule (MAIL_RULE_ID),
    CONSTRAINT FK_SEC_LOG_DLP_MAIL_SEND_APPR FOREIGN KEY (KAKAO_APPROVAL_ID) REFERENCES sec_log_dlp_kakao_approval (KAKAO_APPROVAL_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 DLP 관리 - 메일 알림 보낸 기록';

-- ------------------------------------------------------------
-- 19-3. 기본 규칙 1개: 신청 종료일 + 1일, 매일 11:00, 받는 사람 비움(회사 메일을 화면에서 넣음), 꺼짐(받는 사람을 넣은 뒤 켬), 보내기 시작일 = 적용한 날
--       본문 = 신청 종료일이 같은 사람을 1통에 목록으로 ({번호}. … 줄이 사람 수만큼 반복)
--       이미 있으면 덮어쓰지 않음 → 화면(DLP 관리 > 메일 알림 관리)에서 변경
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_dlp_mail_rule
    (RULE_KEY, RULE_NAME, OFFSET_DAYS, SEND_TIME, TO_ADDRS, CC_ADDRS, SUBJECT_TPL, BODY_TPL, USE_YN, START_DATE, CREATED_BY)
VALUES ('KAKAO_EXPIRE', '카카오톡 승인 기한 경과 차단 요청', 1, '11:00', '', NULL,
        '[DLP] 카카오톡 승인 기한 경과 차단 요청 - {부서} {이름}',
        CONCAT('아래 {인원수}명의 DLP 카카오톡 승인 기한({신청종료일})이 경과하였으니 차단 요청드립니다.', CHAR(10), CHAR(10),
               '{번호}. {부서} {이름} (신청 기간 {신청시작일} ~ {신청종료일}, 문서번호 {문서번호})'),
        'N', CURDATE(), NULL);
