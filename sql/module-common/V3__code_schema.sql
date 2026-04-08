-- ============================================================
--  module-common : 공통코드 스키마 + 샘플 데이터
--  MariaDB 10.11+ (utf8mb4)
--
--  원본: V1.1.0__code_schema.sql
-- ============================================================

-- ── 공통코드 그룹 (상위 코드) ──
CREATE TABLE IF NOT EXISTS code_group (
    GROUP_ID     BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '그룹 ID (PK)',
    GROUP_CODE   VARCHAR(50)  NOT NULL                 COMMENT '그룹 코드 (유니크)',
    GROUP_NAME   VARCHAR(100) NOT NULL                 COMMENT '그룹명',
    DESCRIPTION  VARCHAR(200) NULL                     COMMENT '설명',
    IS_ACTIVE    TINYINT(1)   NOT NULL DEFAULT 1       COMMENT '활성 여부',
    SORT_ORDER   INT          NOT NULL DEFAULT 0       COMMENT '정렬 순서',
    CREATED_AT   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '생성일시',
    UPDATED_AT   DATETIME     NULL     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    CREATED_BY   VARCHAR(50)  NULL                     COMMENT '생성자',
    UPDATED_BY   VARCHAR(50)  NULL                     COMMENT '수정자',

    PRIMARY KEY (GROUP_ID),
    UNIQUE KEY UK_CODE_GROUP_CODE (GROUP_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='공통코드 그룹';

-- ── 공통코드 상세 (하위 코드) ──
CREATE TABLE IF NOT EXISTS code_detail (
    CODE_ID      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '코드 ID (PK)',
    GROUP_ID     BIGINT       NOT NULL                 COMMENT '그룹 ID (FK)',
    CODE         VARCHAR(50)  NOT NULL                 COMMENT '코드값',
    CODE_NAME    VARCHAR(100) NOT NULL                 COMMENT '코드명',
    DESCRIPTION  VARCHAR(200) NULL                     COMMENT '설명',
    EXTRA_VALUE1 VARCHAR(200) NULL                     COMMENT '부가값1',
    EXTRA_VALUE2 VARCHAR(200) NULL                     COMMENT '부가값2',
    IS_ACTIVE    TINYINT(1)   NOT NULL DEFAULT 1       COMMENT '활성 여부',
    SORT_ORDER   INT          NOT NULL DEFAULT 0       COMMENT '정렬 순서',
    CREATED_AT   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '생성일시',
    UPDATED_AT   DATETIME     NULL     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    PRIMARY KEY (CODE_ID),
    UNIQUE KEY UK_CODE_DETAIL (GROUP_ID, CODE),
    CONSTRAINT FK_CODE_DETAIL_GROUP FOREIGN KEY (GROUP_ID)
        REFERENCES code_group (GROUP_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='공통코드 상세';

CREATE INDEX IDX_CODE_DETAIL_GROUP ON code_detail (GROUP_ID);
CREATE INDEX IDX_CODE_DETAIL_ACTIVE ON code_detail (IS_ACTIVE, SORT_ORDER);
