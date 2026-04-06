-- ============================================================
--  공통코드 관리 - 스키마 생성 스크립트
--  MariaDB 10.11+ (utf8mb4)
--  최종 갱신: 2026-04-06
-- ============================================================

-- 공통코드 그룹 (상위 코드)
CREATE TABLE IF NOT EXISTS MOD_CODE_GROUP (
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

-- 공통코드 상세 (하위 코드)
CREATE TABLE IF NOT EXISTS MOD_CODE_DETAIL (
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
        REFERENCES MOD_CODE_GROUP (GROUP_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='공통코드 상세';

CREATE INDEX IDX_CODE_DETAIL_GROUP ON MOD_CODE_DETAIL (GROUP_ID);
CREATE INDEX IDX_CODE_DETAIL_ACTIVE ON MOD_CODE_DETAIL (IS_ACTIVE, SORT_ORDER);

-- ============================================================
--  샘플 데이터
-- ============================================================

-- 그룹: 사용자 상태
INSERT INTO MOD_CODE_GROUP (GROUP_CODE, GROUP_NAME, DESCRIPTION, SORT_ORDER) VALUES
('USER_STATUS', '사용자 상태', '사용자 계정 상태 코드', 1);

SET @g1 = LAST_INSERT_ID();
INSERT INTO MOD_CODE_DETAIL (GROUP_ID, CODE, CODE_NAME, SORT_ORDER) VALUES
(@g1, 'ACTIVE',    '활성',   1),
(@g1, 'INACTIVE',  '비활성', 2),
(@g1, 'SUSPENDED', '정지',   3);

-- 그룹: 직급
INSERT INTO MOD_CODE_GROUP (GROUP_CODE, GROUP_NAME, DESCRIPTION, SORT_ORDER) VALUES
('POSITION', '직급', '사원 직급 구분', 2);

SET @g2 = LAST_INSERT_ID();
INSERT INTO MOD_CODE_DETAIL (GROUP_ID, CODE, CODE_NAME, SORT_ORDER) VALUES
(@g2, 'STAFF',     '사원',   1),
(@g2, 'SENIOR',    '주임',   2),
(@g2, 'ASSISTANT', '대리',   3),
(@g2, 'MANAGER',   '과장',   4),
(@g2, 'DEPUTY',    '차장',   5),
(@g2, 'DIRECTOR',  '부장',   6),
(@g2, 'EXECUTIVE', '임원',   7);

-- 그룹: 부서유형
INSERT INTO MOD_CODE_GROUP (GROUP_CODE, GROUP_NAME, DESCRIPTION, SORT_ORDER) VALUES
('DEPT_TYPE', '부서유형', '부서 분류 유형', 3);

SET @g3 = LAST_INSERT_ID();
INSERT INTO MOD_CODE_DETAIL (GROUP_ID, CODE, CODE_NAME, SORT_ORDER) VALUES
(@g3, 'HQ',     '본사',   1),
(@g3, 'BRANCH', '지사',   2),
(@g3, 'TEAM',   '팀',     3),
(@g3, 'PART',   '파트',   4);
