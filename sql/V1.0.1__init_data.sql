-- ============================================================
--  플랫폼 관리시스템 - 초기 데이터 삽입
--  최종 갱신: 2026-04-03
--
--  기본 관리자 비밀번호: admin123!
--  ※ 운영 배포 후 반드시 비밀번호를 변경하세요
--
--  실행 방법:
--    mariadb -u platform_user -p platform_db < V1.0.1__init_data.sql
-- ============================================================

-- USE platform_db;
-- ※ DB 이름이 다른 경우(예: aiplatform) 위 USE 문을 환경에 맞게 수정하세요


-- ============================================================
--  1. 시스템 관리자 계정
-- ============================================================
-- 비밀번호 admin123! 의 BCrypt 해시
INSERT INTO core_user (LOGIN_ID, PASSWORD, USER_NAME, EMAIL, PHONE, ROLE, ENABLED, CREATED_AT, UPDATED_AT, CREATED_BY)
VALUES (
    'admin',
    '$2a$10$GKQBHCZW3fUd/bnINzscKuHAsHFU4YaH/oCqvhlzWWiMRkT5H8WqW',
    '시스템 관리자',
    'admin@company.com',
    '010-0000-0000',
    'ROLE_ADMIN',
    1,
    NOW(), NOW(), 'SYSTEM'
);


-- ============================================================
--  2. 기본 부서 데이터
-- ============================================================
-- 부서 트리:
--   본사 (HQ)
--     ├── 경영지원본부 (MGMT)
--     │     ├── 인사팀 (HR)
--     │     └── (재무팀 등 필요 시 추가)
--     └── IT개발본부 (ITDEV)
--           ├── 개발1팀 (DEV1)
--           └── QA팀 (QA)

INSERT INTO mod_user_department (DEPT_NAME, DEPT_CODE, PARENT_DEPT_ID, SORT_ORDER, ENABLED, CREATED_AT, UPDATED_AT)
VALUES
    ('본사',         'HQ',    NULL, 1, 1, NOW(), NOW()),   -- DEPT_ID = 1
    ('경영지원본부', 'MGMT',  1,    2, 1, NOW(), NOW()),   -- DEPT_ID = 2
    ('IT개발본부',   'ITDEV', 1,    3, 1, NOW(), NOW()),   -- DEPT_ID = 3
    ('인사팀',       'HR',    2,    1, 1, NOW(), NOW()),   -- DEPT_ID = 4
    ('개발1팀',      'DEV1',  3,    1, 1, NOW(), NOW()),   -- DEPT_ID = 5
    ('QA팀',         'QA',    3,    4, 1, NOW(), NOW());   -- DEPT_ID = 6


-- ============================================================
--  3. 관리자 프로필
-- ============================================================
INSERT INTO user_profile (USER_ID, DEPT_ID, POSITION, JOB_TITLE, EMPLOYEE_NO, JOIN_DATE, CREATED_AT, UPDATED_AT)
VALUES (
    1,              -- admin 사용자
    1,              -- 본사
    '임원',
    'CTO',
    'EMP-0001',
    '2020-01-01',
    NOW(), NOW()
);


-- ============================================================
--  4. 메뉴 데이터
-- ============================================================
-- 현재 시스템 메뉴 (최상위 4개, 하위 버튼 없음)
--   1. 대시보드      (/dashboard)
--   2. 사용자 관리   (/users)       - 프로필 통합 관리 포함
--   3. 메뉴 관리     (/menus)
--   4. 접근 권한     (/permissions) - 메뉴별 접근 권한 매트릭스

INSERT INTO core_menu (MENU_NAME, MENU_CODE, PARENT_ID, MENU_URL, ICON, SORT_ORDER, MENU_TYPE, IS_VISIBLE, IS_ACTIVE, DESCRIPTION)
VALUES
    ('대시보드',    'DASHBOARD', NULL, '/dashboard',   'dashboard',  1, 'MENU', 1, 1, '대시보드'),
    ('사용자 관리', 'USER_MGMT', NULL, '/users',       'users',      2, 'MENU', 1, 1, '사용자 및 프로필 통합 관리'),
    ('메뉴 관리',   'MENU_MGMT', NULL, '/menus',       'menu',       3, 'MENU', 1, 1, '메뉴 관리'),
    ('접근 권한',   'PERM_MGMT', NULL, '/permissions', 'permission', 4, 'MENU', 1, 1, '메뉴별 접근 권한 관리');


-- ============================================================
--  5. 권한 데이터
-- ============================================================
-- 활성 권한 8개 (사용자/메뉴/권한 관련)
-- 비활성 권한 4개 (부서/프로필 – 메뉴 통합으로 비활성화)

INSERT INTO core_permission (PERM_CODE, PERM_NAME, DESCRIPTION, IS_ACTIVE)
VALUES
    -- 사용자 관련 권한
    ('USER_READ',     '사용자 조회',     '사용자 목록/상세 조회',      1),  -- PERM_ID = 1
    ('USER_WRITE',    '사용자 등록/수정', '사용자 등록 및 수정',       1),  -- PERM_ID = 2
    ('USER_DELETE',   '사용자 삭제',     '사용자 삭제/비활성화',       1),  -- PERM_ID = 3
    ('USER_ROLE',     '역할 관리',       '사용자 역할 변경',           1),  -- PERM_ID = 4
    -- 메뉴 관련 권한
    ('MENU_READ',     '메뉴 조회',       '메뉴 목록 조회',            1),  -- PERM_ID = 5
    ('MENU_WRITE',    '메뉴 관리',       '메뉴 등록/수정/삭제',       1),  -- PERM_ID = 6
    -- 권한 관련 권한
    ('PERM_READ',     '권한 조회',       '권한 목록 조회',            1),  -- PERM_ID = 7
    ('PERM_WRITE',    '권한 관리',       '역할/권한 매핑 관리',       1),  -- PERM_ID = 8
    -- 부서/프로필 (사용자 관리에 통합되어 비활성화)
    ('DEPT_READ',     '부서 조회',       '부서 목록 조회',            0),  -- PERM_ID = 9
    ('DEPT_WRITE',    '부서 관리',       '부서 등록/수정',            0),  -- PERM_ID = 10
    ('PROFILE_READ',  '프로필 조회',     '사용자 프로필 조회',        0),  -- PERM_ID = 11
    ('PROFILE_WRITE', '프로필 관리',     '사용자 프로필 등록/수정',   0);  -- PERM_ID = 12


-- ============================================================
--  6. 역할-메뉴 매핑
-- ============================================================
-- ROLE_ADMIN   : 모든 메뉴 (1=대시보드, 2=사용자관리, 3=메뉴관리, 4=접근권한)
-- ROLE_MANAGER : 대시보드, 사용자관리
-- ROLE_USER    : 대시보드만

INSERT INTO core_role_menu (ROLE, MENU_ID) VALUES
    ('ROLE_ADMIN', 1), ('ROLE_ADMIN', 2), ('ROLE_ADMIN', 3), ('ROLE_ADMIN', 4);

INSERT INTO core_role_menu (ROLE, MENU_ID) VALUES
    ('ROLE_MANAGER', 1), ('ROLE_MANAGER', 2);

INSERT INTO core_role_menu (ROLE, MENU_ID) VALUES
    ('ROLE_USER', 1);


-- ============================================================
--  7. 역할-권한 매핑
-- ============================================================
-- ROLE_ADMIN   : 모든 활성 권한 (1~8)
-- ROLE_MANAGER : USER_READ(1), MENU_READ(5), PERM_READ(7)
-- ROLE_USER    : (권한 없음 – 대시보드만 접근)

INSERT INTO core_role_permission (ROLE, PERM_ID) VALUES
    ('ROLE_ADMIN', 1), ('ROLE_ADMIN', 2), ('ROLE_ADMIN', 3), ('ROLE_ADMIN', 4),
    ('ROLE_ADMIN', 5), ('ROLE_ADMIN', 6), ('ROLE_ADMIN', 7), ('ROLE_ADMIN', 8);

INSERT INTO core_role_permission (ROLE, PERM_ID) VALUES
    ('ROLE_MANAGER', 1), ('ROLE_MANAGER', 5), ('ROLE_MANAGER', 7);
