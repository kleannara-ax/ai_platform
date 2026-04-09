-- =============================================================================
-- 01_ddl_core.sql
-- 공통 모듈 DDL (MariaDB) - 데이터베이스 생성 + 사용자 테이블
-- 최종 업데이트: 2026-04-09
-- =============================================================================

-- -----------------------------------------------------------------------
-- 데이터베이스 생성
-- -----------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS platform_db
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE platform_db;

-- -----------------------------------------------------------------------
-- 웹 사용자 (module-user)
-- ASP.NET PBKDF2 → BCrypt 마이그레이션 완료
-- legacy_* 컬럼은 마이그레이션 과도기용 (향후 삭제 예정)
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS web_user (
    user_id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사용자 ID (PK)',
    username                 VARCHAR(100) NOT NULL                COMMENT '로그인 아이디',
    display_name             VARCHAR(200)                         COMMENT '표시 이름',
    password_hash            VARCHAR(255) NOT NULL                COMMENT 'BCrypt 해시 비밀번호',
    legacy_password_hash_b64 VARCHAR(512)                         COMMENT '레거시 해시 (마이그레이션용)',
    legacy_password_salt_b64 VARCHAR(512)                         COMMENT '레거시 솔트 (마이그레이션용)',
    legacy_iterations        INT                                  COMMENT '레거시 반복 횟수 (마이그레이션용)',
    role                     VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '역할 (ADMIN/USER)',
    is_active                TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '활성 여부',
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',

    PRIMARY KEY (user_id),
    CONSTRAINT UK_WEBUSER_USERNAME UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='웹 사용자';
