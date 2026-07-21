-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 01_schema.sql
-- 설명: AI 플랫폼 통합형 DDL
--       - 기존 플랫폼 테이블(core_user, core_menu, core_menu_permission) 스텁 포함
--       - 일보 업무 테이블 5개 (daily_report, daily_report_table, daily_report_cell,
--         daily_report_remark, daily_report_image)
--       - ★ 신규: daily_report_cell_auth (셀 단위 접근 권한 관리)
-- ============================================================
-- 변경이력:
--   v1.0: 최초 작성
--   v2.0: HTML 원본 기준 4개 표 재구성
--   v3.0 (현재): AI 플랫폼 통합 — 3계층 권한 체계
--     - 1계층: core_menu_permission → '세부공장일보 입력' 페이지 접근 권한
--     - 2계층: daily_report_cell_auth → 셀 단위 입력 권한 (관리자 설정)
--     - 3계층: core_menu_permission → '세부공장일보 접근권한' 관리 페이지 접근 권한
--     ※ 레거시 daily_report_cell_permission 제거 → daily_report_cell_auth로 대체
-- ============================================================

USE dailyreport_dev;

-- ────────────────────────────────────────────
-- 0-1. core_user 스텁 (플랫폼 기존 테이블)
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS core_user (
    USER_ID     BIGINT          NOT NULL AUTO_INCREMENT,
    LOGIN_ID    VARCHAR(50)     NOT NULL,
    USER_NAME   VARCHAR(100)    NOT NULL,
    PASSWORD    VARCHAR(255)             COMMENT '암호화된 비밀번호 (BCrypt 등)',
    EMAIL       VARCHAR(200),
    DEPARTMENT  VARCHAR(100),
    POSITION    VARCHAR(50),
    ROLE        VARCHAR(20)     NOT NULL DEFAULT 'USER'  COMMENT 'ADMIN/USER',
    IS_ACTIVE   TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '활성 여부',
    CREATED_AT  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT  DATETIME                 ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (USER_ID),
    UNIQUE KEY UK_CORE_USER_LOGIN (LOGIN_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[플랫폼 코어] 사용자 마스터';


-- ────────────────────────────────────────────
-- 0-2. core_menu 스텁 (플랫폼 기존 테이블)
--      AI 플랫폼의 카테고리/메뉴 계층 구조
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS core_menu (
    MENU_ID         BIGINT          NOT NULL AUTO_INCREMENT,
    PARENT_MENU_ID  BIGINT                   COMMENT '상위 메뉴 ID (NULL=최상위)',
    MENU_CODE       VARCHAR(50)     NOT NULL COMMENT '메뉴 고유 코드',
    MENU_NAME       VARCHAR(100)    NOT NULL COMMENT '메뉴 표시명',
    MENU_TYPE       VARCHAR(20)     NOT NULL DEFAULT 'PAGE'  COMMENT 'CATEGORY/PAGE/LINK',
    MENU_URL        VARCHAR(300)             COMMENT '페이지 URL 경로',
    ICON            VARCHAR(100)             COMMENT '아이콘 CSS 클래스',
    SORT_ORDER      INT             NOT NULL DEFAULT 0       COMMENT '정렬 순서',
    DESCRIPTION     VARCHAR(500)             COMMENT '메뉴 설명',
    IS_ACTIVE       TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '활성 여부',
    CREATED_AT      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT      DATETIME                 ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (MENU_ID),
    UNIQUE KEY UK_CORE_MENU_CODE (MENU_CODE),
    INDEX IDX_MENU_PARENT (PARENT_MENU_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[플랫폼 코어] 메뉴 마스터 (카테고리·페이지 계층)';


-- ────────────────────────────────────────────
-- 0-3. core_menu_permission 스텁 (플랫폼 기존 테이블)
--      사용자별 메뉴(페이지) 접근 권한
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS core_menu_permission (
    PERM_ID     BIGINT          NOT NULL AUTO_INCREMENT,
    USER_ID     BIGINT          NOT NULL  COMMENT '대상 사용자',
    MENU_ID     BIGINT          NOT NULL  COMMENT '대상 메뉴',
    CAN_READ    TINYINT(1)      NOT NULL DEFAULT 1  COMMENT '조회 권한',
    CAN_WRITE   TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '쓰기 권한',
    CAN_DELETE  TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '삭제 권한',
    CAN_ADMIN   TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '관리 권한',
    GRANTED_BY  BIGINT                   COMMENT '권한 부여자',
    CREATED_AT  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT  DATETIME                 ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (PERM_ID),
    UNIQUE KEY UK_MENU_PERM_USER_MENU (USER_ID, MENU_ID),
    INDEX IDX_PERM_USER (USER_ID),
    INDEX IDX_PERM_MENU (MENU_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[플랫폼 코어] 사용자별 메뉴 접근 권한';


-- ════════════════════════════════════════════
-- ★★ 이하 모듈 전용 테이블 ★★
-- ════════════════════════════════════════════

-- ────────────────────────────────────────────
-- 1. daily_report — 일보 마스터
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_report (
    REPORT_ID   BIGINT          NOT NULL AUTO_INCREMENT,
    REPORT_DATE DATE            NOT NULL                  COMMENT '일보 대상 날짜',
    TITLE       VARCHAR(200)    NOT NULL                  COMMENT '일보 제목',
    STATUS      VARCHAR(20)     NOT NULL DEFAULT 'DRAFT'  COMMENT 'DRAFT/SUBMITTED/CONFIRMED',
    CREATED_BY  BIGINT          NOT NULL                  COMMENT '최초 작성자 (core_user FK)',
    UPDATED_BY  BIGINT                                    COMMENT '최종 수정자',
    CREATED_AT  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT  DATETIME                 ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (REPORT_ID),
    UNIQUE KEY UK_DAILY_REPORT_DATE (REPORT_DATE),
    INDEX IDX_REPORT_STATUS (STATUS),
    INDEX IDX_REPORT_CREATED (CREATED_AT)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='세부공장일보 마스터';


-- ────────────────────────────────────────────
-- 2. daily_report_table — 표 메타
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_report_table (
    TABLE_ID    BIGINT          NOT NULL AUTO_INCREMENT,
    REPORT_ID   BIGINT          NOT NULL,
    TABLE_CODE  VARCHAR(50)     NOT NULL  COMMENT '표 코드 (TBL_PRODUCTION_INDEX 등)',
    TABLE_NAME  VARCHAR(100)    NOT NULL  COMMENT '표 제목 (주요 생산 지표 현황 등)',
    SORT_ORDER  INT             NOT NULL  COMMENT '정렬 순서 (1~4)',
    ROW_COUNT   INT             NOT NULL  COMMENT '표 행 수',
    COL_COUNT   INT             NOT NULL  COMMENT '표 열 수',
    CREATED_AT  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (TABLE_ID),
    INDEX IDX_RPT_TABLE_REPORT (REPORT_ID),
    CONSTRAINT FK_RPT_TABLE_REPORT
        FOREIGN KEY (REPORT_ID) REFERENCES daily_report (REPORT_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='일보 표 메타';


-- ────────────────────────────────────────────
-- 3. daily_report_cell — 표 셀 데이터
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_report_cell (
    CELL_ID         BIGINT          NOT NULL AUTO_INCREMENT,
    TABLE_ID        BIGINT          NOT NULL,
    ROW_INDEX       INT             NOT NULL  COMMENT '행 인덱스',
    COL_INDEX       INT             NOT NULL  COMMENT '열 인덱스',
    EXCEL_COORD     VARCHAR(10)               COMMENT '엑셀 좌표 (B5, O10 등)',
    CELL_VALUE      VARCHAR(2000)             COMMENT '셀 값',
    CELL_TYPE       VARCHAR(20)     NOT NULL  COMMENT 'HEADER/DATA/FORMULA/READONLY',
    CELL_LABEL      VARCHAR(200)              COMMENT '헤더 라벨',
    DATA_FORMAT     VARCHAR(20)               COMMENT 'TEXT/NUMBER/PERCENT/DATE',
    FORMULA         VARCHAR(500)              COMMENT '수식',
    INPUT_CYCLE     VARCHAR(20)     NOT NULL DEFAULT 'NONE' COMMENT 'DAILY/WEEKLY/MONTHLY/NONE (레거시)',
    FREQ_CODE       VARCHAR(20)               COMMENT 'daily/monthly/yearly/event/none',
    FREQ_LABEL      VARCHAR(50)               COMMENT '매일/매월/매년/발생 시',
    OWNER_IDS       VARCHAR(200)              COMMENT '★ 담당자 로그인ID 목록 (공백 구분, cell_auth 기반 동기화)',
    OWNER_NAMES     VARCHAR(500)              COMMENT '담당자 이름 (표시용)',
    IS_LOCKED       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '잠금 여부',
    ROW_SPAN        INT             DEFAULT 1 COMMENT 'rowspan (병합 행)',
    COL_SPAN        INT             DEFAULT 1 COMMENT 'colspan (병합 열)',
    LAST_EDITOR_ID  BIGINT                    COMMENT '최종 입력자',
    LAST_EDITED_AT  DATETIME                  COMMENT '최종 입력 시각',
    CREATED_AT      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (CELL_ID),
    UNIQUE KEY UK_CELL_POSITION (TABLE_ID, ROW_INDEX, COL_INDEX),
    INDEX IDX_CELL_TABLE (TABLE_ID),
    INDEX IDX_CELL_COORD (EXCEL_COORD),
    INDEX IDX_CELL_OWNER (OWNER_IDS(50)),
    CONSTRAINT FK_CELL_TABLE
        FOREIGN KEY (TABLE_ID) REFERENCES daily_report_table (TABLE_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='일보 표 셀 데이터';


-- ────────────────────────────────────────────
-- 4. ★★ daily_report_cell_auth (신규) — 셀 단위 접근 권한
--    관리자가 '세부공장일보 접근권한' 페이지에서 설정
--    → OWNER_IDS / OWNER_NAMES와 동기화
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_report_cell_auth (
    AUTH_ID         BIGINT          NOT NULL AUTO_INCREMENT,
    USER_ID         BIGINT          NOT NULL  COMMENT '담당 사용자 (core_user FK)',
    TABLE_CODE      VARCHAR(50)     NOT NULL  COMMENT '대상 표 코드',
    CELL_COORDS     TEXT            NOT NULL  COMMENT '담당 셀 좌표 목록 (JSON배열: ["B7","C7","D7"])',
    FREQ_CODE       VARCHAR(20)     NOT NULL DEFAULT 'daily' COMMENT 'daily/monthly/yearly/event',
    FREQ_LABEL      VARCHAR(50)               COMMENT '주기 라벨 (매일/매월 등)',
    IS_ACTIVE       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '활성 여부',
    GRANTED_BY      BIGINT                    COMMENT '권한 부여자 (관리자)',
    DESCRIPTION     VARCHAR(300)              COMMENT '설명 (예: 수율 담당, 에너지 담당)',
    CREATED_AT      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT      DATETIME                 ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (AUTH_ID),
    INDEX IDX_CELL_AUTH_USER (USER_ID),
    INDEX IDX_CELL_AUTH_TABLE (TABLE_CODE),
    UNIQUE KEY UK_CELL_AUTH_USER_TABLE (USER_ID, TABLE_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='★ 신규 — 셀 단위 접근 권한 (관리자 설정, 접근권한 페이지에서 관리)';


-- ────────────────────────────────────────────
-- 5. daily_report_remark — 특이사항
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_report_remark (
    REMARK_ID   BIGINT          NOT NULL AUTO_INCREMENT,
    REPORT_ID   BIGINT          NOT NULL,
    TABLE_CODE  VARCHAR(50)               COMMENT '관련 표 코드 (NULL=전체)',
    CATEGORY    VARCHAR(30)     NOT NULL DEFAULT 'GENERAL',
    CONTENT     TEXT            NOT NULL  COMMENT '특이사항 내용',
    SORT_ORDER  INT             NOT NULL DEFAULT 1,
    CREATED_BY  BIGINT          NOT NULL  COMMENT '작성자',
    CREATED_AT  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT  DATETIME                 ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (REMARK_ID),
    INDEX IDX_REMARK_REPORT (REPORT_ID),
    CONSTRAINT FK_REMARK_REPORT
        FOREIGN KEY (REPORT_ID) REFERENCES daily_report (REPORT_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='일보 특이사항';


-- ────────────────────────────────────────────
-- 6. daily_report_image — 이미지 첨부
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_report_image (
    IMAGE_ID      BIGINT          NOT NULL AUTO_INCREMENT,
    REPORT_ID     BIGINT          NOT NULL,
    ORIGINAL_NAME VARCHAR(255)    NOT NULL  COMMENT '원본 파일명',
    STORED_PATH   VARCHAR(500)    NOT NULL  COMMENT '저장 경로',
    FILE_SIZE     BIGINT          NOT NULL DEFAULT 0,
    CONTENT_TYPE  VARCHAR(100)    NOT NULL,
    DESCRIPTION   VARCHAR(500),
    TABLE_CODE    VARCHAR(50),
    SORT_ORDER    INT             NOT NULL DEFAULT 1,
    UPLOADED_BY   BIGINT          NOT NULL,
    CREATED_AT    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (IMAGE_ID),
    INDEX IDX_IMAGE_REPORT (REPORT_ID),
    CONSTRAINT FK_IMAGE_REPORT
        FOREIGN KEY (REPORT_ID) REFERENCES daily_report (REPORT_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='일보 이미지 첨부';


-- ────────────────────────────────────────────
-- 검증: 테이블 생성 확인
-- ────────────────────────────────────────────
SELECT TABLE_NAME, TABLE_COMMENT
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'dailyreport_dev'
 ORDER BY TABLE_NAME;

SELECT '=== 01_schema.sql 실행 완료 (9 테이블) ===' AS message;
