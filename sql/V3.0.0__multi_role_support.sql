-- ============================================================
--  V3.0.0 : 사용자 다중 역할(Multi-Role) 지원
--  MariaDB 10.11+ (utf8mb4)
--  최종 갱신: 2026-08-06
--
--  배경:
--    - 기존 core_user.ROLE(단일 VARCHAR) 컬럼만으로는 한 사용자에게
--      여러 역할을 동시에 부여할 수 없었음
--    - core_user_role 매핑 테이블(사용자 N : 역할 N)을 신설하여
--      한 사용자가 여러 역할을 가질 수 있도록 확장
--
--  주의:
--    - core_user.ROLE 컬럼은 삭제하지 않고 유지한다.
--      (레거시 조회/표시용 "대표 역할" 캐시로 계속 사용 — 역할 변경 시
--       core_user_role의 첫 번째 역할로 자동 동기화됨. 실제 권한 판단은
--       반드시 core_user_role 기준으로 해야 한다.)
--    - core_role_menu(역할→메뉴 매핑)는 이미 역할 단위로 저장되어 있어
--      스키마 변경이 필요 없다. 다중 역할 사용자의 메뉴는 서비스 계층에서
--      역할별 메뉴ID를 UNION하여 계산한다.
-- ============================================================

CREATE TABLE IF NOT EXISTS core_user_role (
    user_role_id BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'PK',
    user_id      BIGINT       NOT NULL                 COMMENT '사용자 ID (core_user 참조)',
    role         VARCHAR(30)  NOT NULL                 COMMENT '역할 코드 (ROLE_ADMIN 등)',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    PRIMARY KEY (user_role_id),
    CONSTRAINT UK_core_user_role UNIQUE (user_id, role),
    CONSTRAINT FK_core_user_role_user_id
        FOREIGN KEY (user_id) REFERENCES core_user (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='사용자-역할 매핑 (다중 역할 지원)';

CREATE INDEX IDX_core_user_role_role ON core_user_role (role);

-- ── 기존 core_user.ROLE 값을 core_user_role로 1건씩 이전 ──
INSERT INTO core_user_role (user_id, role)
SELECT user_id, role FROM core_user
ON DUPLICATE KEY UPDATE core_user_role.role = core_user_role.role;
