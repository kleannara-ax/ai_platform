-- ============================================================
-- module-autodrawing: 자동도면 생성 모듈 스키마
-- ============================================================

-- 자동도면 프로젝트 테이블
CREATE TABLE IF NOT EXISTS MOD_AUTODRAWING_PROJECT (
    PROJECT_ID    BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '프로젝트 ID (PK)',
    PROJECT_UUID  VARCHAR(100) NOT NULL                 COMMENT '프로젝트 고유 식별자 (프론트엔드 UUID)',
    PROJECT_NAME  VARCHAR(200) NOT NULL                 COMMENT '프로젝트명',
    TEAM_ID       VARCHAR(50)  NOT NULL                 COMMENT '팀 ID',
    PROJECT_DATA  LONGTEXT     NULL                     COMMENT '프로젝트 데이터 (JSON)',
    CREATED_BY    BIGINT       NULL                     COMMENT '생성자 ID (core_user.USER_ID)',
    CREATED_AT    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT    DATETIME     NULL     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    PRIMARY KEY (PROJECT_ID),
    UNIQUE KEY UK_MOD_AD_PROJECT_UUID (PROJECT_UUID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='자동도면 프로젝트';

CREATE INDEX IDX_MOD_AD_PROJECT_TEAM ON MOD_AUTODRAWING_PROJECT (TEAM_ID);
CREATE INDEX IDX_MOD_AD_PROJECT_CREATED ON MOD_AUTODRAWING_PROJECT (CREATED_AT DESC);
