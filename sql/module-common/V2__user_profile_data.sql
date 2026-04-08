-- ============================================================
--  module-common : 부서·프로필 초기 데이터
--  MariaDB 10.11+ (utf8mb4)
--
--  원본: V1.0.1__init_data.sql (부서·프로필 섹션)
--  의존: CORE_USER, MOD_USER_DEPARTMENT 테이블
-- ============================================================

-- ── 기본 부서 데이터 ──
-- 부서 트리:
--   본사 (HQ)
--     ├── 경영지원본부 (MGMT)
--     │     └── 인사팀 (HR)
--     └── IT개발본부 (ITDEV)
--           ├── 개발1팀 (DEV1)
--           └── QA팀 (QA)

INSERT INTO MOD_USER_DEPARTMENT (DEPT_NAME, DEPT_CODE, PARENT_DEPT_ID, SORT_ORDER, ENABLED, CREATED_AT, UPDATED_AT)
VALUES
    ('본사',         'HQ',    NULL, 1, 1, NOW(), NOW()),   -- DEPT_ID = 1
    ('경영지원본부', 'MGMT',  1,    2, 1, NOW(), NOW()),   -- DEPT_ID = 2
    ('IT개발본부',   'ITDEV', 1,    3, 1, NOW(), NOW()),   -- DEPT_ID = 3
    ('인사팀',       'HR',    2,    1, 1, NOW(), NOW()),   -- DEPT_ID = 4
    ('개발1팀',      'DEV1',  3,    1, 1, NOW(), NOW()),   -- DEPT_ID = 5
    ('QA팀',         'QA',    3,    4, 1, NOW(), NOW());   -- DEPT_ID = 6


-- ── 관리자 프로필 ──
INSERT INTO MOD_USER_PROFILE (USER_ID, DEPT_ID, POSITION, JOB_TITLE, EMPLOYEE_NO, JOIN_DATE, CREATED_AT, UPDATED_AT)
VALUES (
    1,              -- admin 사용자
    1,              -- 본사
    '임원',
    'CTO',
    'EMP-0001',
    '2020-01-01',
    NOW(), NOW()
);
