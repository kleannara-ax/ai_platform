-- ============================================================
--  플랫폼 관리시스템 - 스키마 생성 스크립트
--  MariaDB 10.11+ (utf8mb4)
--  최종 갱신: 2026-04-03
--
--  실행 방법:
--    mariadb -u platform_user -p platform_db < V1.0.0__init_schema.sql
-- ============================================================

-- ── 데이터베이스 생성 ──
-- CREATE DATABASE IF NOT EXISTS platform_db
--     DEFAULT CHARACTER SET utf8mb4
--     DEFAULT COLLATE utf8mb4_general_ci;
-- ※ DB 이름이 다른 경우(예: aiplatform) 환경에 맞게 수정하세요

-- USE platform_db;
-- ※ DB 이름이 다른 경우 위 USE 문을 환경에 맞게 수정하세요


-- ============================================================
--  1. CORE 모듈 테이블 (Prefix: CORE_)
-- ============================================================

-- ── 1-1. 사용자 테이블 ──
CREATE TABLE IF NOT EXISTS core_user (
    USER_ID       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '사용자 ID (PK)',
    LOGIN_ID      VARCHAR(50)  NOT NULL                 COMMENT '로그인 ID',
    PASSWORD      VARCHAR(255) NOT NULL                 COMMENT '비밀번호 (BCrypt)',
    USER_NAME     VARCHAR(100) NOT NULL                 COMMENT '사용자명',
    EMAIL         VARCHAR(200) NULL                     COMMENT '이메일',
    PHONE         VARCHAR(20)  NULL                     COMMENT '전화번호',
    ROLE          VARCHAR(30)  NOT NULL DEFAULT 'ROLE_USER' COMMENT '역할 (ROLE_ADMIN, ROLE_MANAGER, ROLE_USER)',
    ENABLED       TINYINT(1)   NOT NULL DEFAULT 1       COMMENT '활성화 여부',
    CREATED_AT    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT    DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    CREATED_BY    VARCHAR(50)  NULL                     COMMENT '생성자',
    UPDATED_BY    VARCHAR(50)  NULL                     COMMENT '수정자',

    PRIMARY KEY (USER_ID),
    CONSTRAINT UK_core_user_LOGIN_ID UNIQUE (LOGIN_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='플랫폼 사용자';

CREATE INDEX IDX_core_user_EMAIL ON core_user (EMAIL);
CREATE INDEX IDX_core_user_ROLE  ON core_user (ROLE);


-- ── 1-2. 메뉴 테이블 ──
CREATE TABLE IF NOT EXISTS core_menu (
    MENU_ID      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '메뉴 ID (PK)',
    MENU_NAME    VARCHAR(100) NOT NULL                 COMMENT '메뉴명',
    MENU_CODE    VARCHAR(50)  NOT NULL                 COMMENT '메뉴 코드 (유니크)',
    PARENT_ID    BIGINT       NULL                     COMMENT '상위 메뉴 ID',
    MENU_URL     VARCHAR(255) NULL                     COMMENT '메뉴 URL (프론트 라우트)',
    ICON         VARCHAR(50)  NULL                     COMMENT '아이콘 식별자',
    SORT_ORDER   INT          NULL     DEFAULT 0       COMMENT '정렬 순서',
    MENU_TYPE    VARCHAR(20)  NULL     DEFAULT 'MENU'  COMMENT '메뉴 유형 (MENU, BUTTON, API)',
    IS_VISIBLE   TINYINT(1)   NULL     DEFAULT 1       COMMENT '사이드바 표시 여부',
    IS_ACTIVE    TINYINT(1)   NULL     DEFAULT 1       COMMENT '활성화 여부',
    DESCRIPTION  VARCHAR(200) NULL                     COMMENT '설명',
    CREATED_AT   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT   DATETIME     NULL     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    PRIMARY KEY (MENU_ID),
    CONSTRAINT UK_core_menu_CODE UNIQUE (MENU_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='메뉴';

CREATE INDEX IX_core_menu_PARENT ON core_menu (PARENT_ID);


-- ── 1-3. 권한 테이블 ──
CREATE TABLE IF NOT EXISTS core_permission (
    PERM_ID      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '권한 ID (PK)',
    PERM_CODE    VARCHAR(50)  NOT NULL                 COMMENT '권한 코드 (유니크)',
    PERM_NAME    VARCHAR(100) NOT NULL                 COMMENT '권한명',
    DESCRIPTION  VARCHAR(200) NULL                     COMMENT '설명',
    IS_ACTIVE    TINYINT(1)   NULL     DEFAULT 1       COMMENT '활성화 여부',
    CREATED_AT   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT   DATETIME     NULL     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    PRIMARY KEY (PERM_ID),
    CONSTRAINT UK_CORE_PERM_CODE UNIQUE (PERM_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='권한';


-- ── 1-4. 역할-메뉴 매핑 테이블 ──
CREATE TABLE IF NOT EXISTS core_role_menu (
    ROLE_MENU_ID BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'PK',
    ROLE         VARCHAR(30)  NOT NULL                 COMMENT '역할 (ROLE_ADMIN 등)',
    MENU_ID      BIGINT       NOT NULL                 COMMENT '메뉴 ID',
    CREATED_AT   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',

    PRIMARY KEY (ROLE_MENU_ID),
    CONSTRAINT UK_core_role_menu UNIQUE (ROLE, MENU_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='역할-메뉴 매핑';

CREATE INDEX IX_CORE_RM_MENU ON core_role_menu (MENU_ID);


-- ── 1-5. 역할-권한 매핑 테이블 ──
CREATE TABLE IF NOT EXISTS core_role_permission (
    ROLE_PERM_ID BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'PK',
    ROLE         VARCHAR(30)  NOT NULL                 COMMENT '역할',
    PERM_ID      BIGINT       NOT NULL                 COMMENT '권한 ID',
    CREATED_AT   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',

    PRIMARY KEY (ROLE_PERM_ID),
    CONSTRAINT UK_CORE_ROLE_PERM UNIQUE (ROLE, PERM_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='역할-권한 매핑';

CREATE INDEX IX_CORE_RP_PERM ON core_role_permission (PERM_ID);


-- ============================================================
--  2. MODULE-USER 모듈 테이블 (Prefix: MOD_USER_)
-- ============================================================

-- ── 2-1. 부서 테이블 ──
CREATE TABLE IF NOT EXISTS mod_user_department (
    DEPT_ID        BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '부서 ID (PK)',
    DEPT_NAME      VARCHAR(100) NOT NULL                 COMMENT '부서명',
    DEPT_CODE      VARCHAR(20)  NOT NULL                 COMMENT '부서 코드',
    PARENT_DEPT_ID BIGINT       NULL                     COMMENT '상위 부서 ID',
    SORT_ORDER     INT          NULL     DEFAULT 0       COMMENT '정렬 순서',
    ENABLED        TINYINT(1)   NOT NULL DEFAULT 1       COMMENT '활성화 여부',
    CREATED_AT     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT     DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    PRIMARY KEY (DEPT_ID),
    CONSTRAINT UK_MOD_USER_DEPT_CODE UNIQUE (DEPT_CODE),
    CONSTRAINT FK_MOD_USER_DEPT_PARENT FOREIGN KEY (PARENT_DEPT_ID)
        REFERENCES mod_user_department (DEPT_ID) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='부서';


-- ── 2-2. 사용자 프로필 테이블 ──
CREATE TABLE IF NOT EXISTS user_profile (
    PROFILE_ID   BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '프로필 ID (PK)',
    USER_ID      BIGINT       NOT NULL                 COMMENT '사용자 ID (core_user 참조)',
    DEPT_ID      BIGINT       NULL                     COMMENT '부서 ID (mod_user_department 참조)',
    POSITION     VARCHAR(50)  NULL                     COMMENT '직위',
    JOB_TITLE    VARCHAR(100) NULL                     COMMENT '직책',
    EMPLOYEE_NO  VARCHAR(20)  NULL                     COMMENT '사번',
    JOIN_DATE    DATE         NULL                     COMMENT '입사일',
    OFFICE_PHONE VARCHAR(20)  NULL                     COMMENT '사무실 전화번호',
    INTERNAL_EXT VARCHAR(10)  NULL                     COMMENT '내선번호',
    CREATED_AT   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT   DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    PRIMARY KEY (PROFILE_ID),
    CONSTRAINT UK_USER_PROFILE_USER UNIQUE (USER_ID),
    CONSTRAINT FK_USER_PROFILE_USER FOREIGN KEY (USER_ID)
        REFERENCES core_user (USER_ID) ON DELETE CASCADE,
    CONSTRAINT FK_USER_PROFILE_DEPT FOREIGN KEY (DEPT_ID)
        REFERENCES mod_user_department (DEPT_ID) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='사용자 프로필';

CREATE INDEX IDX_USER_PROFILE_DEPT ON user_profile (DEPT_ID);
CREATE INDEX IDX_USER_PROFILE_EMP  ON user_profile (EMPLOYEE_NO);
