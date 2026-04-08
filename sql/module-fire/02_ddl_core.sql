-- =============================================================================
-- 02_ddl_core.sql
-- 기준 마스터 테이블 + 사용자 테이블 DDL (MariaDB)
-- module-user, module-fire 공통 참조 테이블
-- =============================================================================

USE fireweb;

-- -----------------------------------------------------------------------
-- 사용자 테이블 (module-user)
-- 기존 ASP.NET: WebUser 테이블
-- 변경: PasswordHash+PasswordSalt+Iterations → password_hash(BCrypt 단일 필드)
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS web_user (
    user_id         BIGINT          NOT NULL AUTO_INCREMENT COMMENT '사용자 ID',
    username        VARCHAR(100)    NOT NULL                COMMENT '로그인 아이디',
    display_name    VARCHAR(200)                            COMMENT '표시 이름 (담당자/부서명)',
    password_hash   VARCHAR(255)    NOT NULL                COMMENT 'BCrypt 해시 비밀번호',
    role            VARCHAR(20)     NOT NULL DEFAULT 'USER' COMMENT '역할 (ADMIN/USER)',
    is_active       TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '활성 여부',
    created_at      DATETIME        NOT NULL DEFAULT NOW()  COMMENT '등록일시',

    PRIMARY KEY (user_id),
    UNIQUE KEY uk_web_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='웹 사용자';

-- -----------------------------------------------------------------------
-- 건물 마스터 (module-fire)
-- 기존 ASP.NET: Building 테이블
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS building (
    building_id     BIGINT          NOT NULL AUTO_INCREMENT COMMENT '건물 ID',
    building_name   VARCHAR(200)    NOT NULL                COMMENT '건물명',
    is_active       TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '활성 여부',

    PRIMARY KEY (building_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='건물 마스터';

-- 옥외 소화전용 가상 건물 (id=99, 기존 규칙 유지)
INSERT INTO building (building_id, building_name, is_active)
VALUES (99, '옥외', 1)
ON DUPLICATE KEY UPDATE building_name = '옥외';

-- -----------------------------------------------------------------------
-- 층 마스터 (module-fire)
-- 기존 ASP.NET: Floor 테이블
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS floor (
    floor_id        BIGINT          NOT NULL AUTO_INCREMENT COMMENT '층 ID',
    floor_name      VARCHAR(100)    NOT NULL                COMMENT '층명',
    sort_order      INT             NOT NULL DEFAULT 0      COMMENT '정렬 순서',

    PRIMARY KEY (floor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='층 마스터';
