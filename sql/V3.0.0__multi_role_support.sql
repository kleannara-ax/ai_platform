-- ============================================================
-- V3.0.0: 다중 역할 지원 (Multi-Role Support)
--   사용자에게 1개 이상의 역할을 부여할 수 있도록 변경
--   core_user_role 테이블 생성 및 기존 데이터 마이그레이션
-- ============================================================

-- ── 1. 사용자-역할 매핑 테이블 생성 ──
CREATE TABLE IF NOT EXISTS core_user_role (
    USER_ROLE_ID  BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'PK',
    USER_ID       BIGINT       NOT NULL                 COMMENT '사용자 ID',
    ROLE          VARCHAR(30)  NOT NULL                 COMMENT '역할 코드 (ROLE_ADMIN 등)',
    CREATED_AT    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',

    PRIMARY KEY (USER_ROLE_ID),
    CONSTRAINT UK_core_user_role UNIQUE (USER_ID, ROLE),
    CONSTRAINT FK_core_user_role_user FOREIGN KEY (USER_ID)
        REFERENCES core_user (USER_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='사용자-역할 매핑 (다중 역할 지원)';

CREATE INDEX IX_core_user_role_ROLE ON core_user_role (ROLE);

-- ── 2. 기존 core_user.ROLE 데이터를 core_user_role로 마이그레이션 ──
INSERT INTO core_user_role (USER_ID, ROLE, CREATED_AT)
SELECT USER_ID, ROLE, NOW()
FROM core_user
WHERE ROLE IS NOT NULL AND ROLE <> ''
ON DUPLICATE KEY UPDATE CREATED_AT = CREATED_AT;
