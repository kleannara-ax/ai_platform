-- ============================================================
--  module-common : 공통코드 초기 데이터
--  MariaDB 10.11+ (utf8mb4)
--
--  원본: V1.1.0__code_schema.sql (샘플 데이터 섹션)
--       V1.2.0__dept_to_common_code.sql (부서 공통코드 전환)
--       V1.3.0__role_to_common_code.sql (역할 공통코드 전환)
-- ============================================================

-- ── 1. 사용자 상태 코드 ──
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, SORT_ORDER) VALUES
('USER_STATUS', '사용자 상태', '사용자 계정 상태 코드', 1);

SET @g1 = LAST_INSERT_ID();
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, SORT_ORDER) VALUES
(@g1, 'ACTIVE',    '활성',   1),
(@g1, 'INACTIVE',  '비활성', 2),
(@g1, 'SUSPENDED', '정지',   3);


-- ── 2. 직급 코드 ──
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, SORT_ORDER) VALUES
('POSITION', '직급', '사원 직급 구분', 2);

SET @g2 = LAST_INSERT_ID();
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, SORT_ORDER) VALUES
(@g2, 'STAFF',     '사원',   1),
(@g2, 'SENIOR',    '주임',   2),
(@g2, 'ASSISTANT', '대리',   3),
(@g2, 'MANAGER',   '과장',   4),
(@g2, 'DEPUTY',    '차장',   5),
(@g2, 'DIRECTOR',  '부장',   6),
(@g2, 'EXECUTIVE', '임원',   7);


-- ── 3. 부서유형 코드 ──
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, SORT_ORDER) VALUES
('DEPT_TYPE', '부서유형', '부서 분류 유형', 3);

SET @g3 = LAST_INSERT_ID();
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, SORT_ORDER) VALUES
(@g3, 'HQ',     '본사',   1),
(@g3, 'BRANCH', '지사',   2),
(@g3, 'TEAM',   '팀',     3),
(@g3, 'PART',   '파트',   4);


-- ── 4. 부서 목록 (공통코드로 관리) ──
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER) VALUES
('DEPT', '부서', '부서 목록 (공통코드로 관리)', 1, 4);

SET @g4 = LAST_INSERT_ID();
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER) VALUES
(@g4, 'HQ',    '본사',         NULL,                    1, 1),
(@g4, 'MGMT',  '경영지원본부', '본사 > 경영지원본부',   1, 2),
(@g4, 'ITDEV', 'IT개발본부',   '본사 > IT개발본부',     1, 3),
(@g4, 'HR',    '인사팀',       '경영지원본부 > 인사팀', 1, 4),
(@g4, 'DEV1',  '개발1팀',      'IT개발본부 > 개발1팀',  1, 5),
(@g4, 'QA',    'QA팀',         'IT개발본부 > QA팀',     1, 6);


-- ── 5. 사용자 역할 (공통코드로 관리) ──
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER) VALUES
('ROLE', '사용자 역할', '시스템 사용자 역할 구분 (Spring Security Role)', 1, 0);

SET @g5 = LAST_INSERT_ID();
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER) VALUES
(@g5, 'ROLE_ADMIN',   '관리자',     '시스템 전체 관리 권한',  'admin',   1, 1),
(@g5, 'ROLE_MANAGER', '매니저',     '부서/팀 관리 권한',      'manager', 1, 2),
(@g5, 'ROLE_USER',    '일반 사용자', '기본 사용자 권한',       'user',    1, 3);


-- ── 6. 공통코드 관리 메뉴 등록 ──
INSERT INTO CORE_MENU (MENU_NAME, MENU_CODE, PARENT_ID, MENU_URL, ICON, SORT_ORDER, MENU_TYPE, IS_VISIBLE, IS_ACTIVE, DESCRIPTION)
VALUES ('공통코드 관리', 'CODE_MGMT', NULL, '/codes', 'code', 5, 'MENU', 1, 1, '공통코드 그룹 및 상세 코드 관리');

SET @codeMenuId = LAST_INSERT_ID();

INSERT INTO CORE_ROLE_MENU (ROLE, MENU_ID, CREATED_AT) VALUES
('ROLE_ADMIN',   @codeMenuId, NOW()),
('ROLE_MANAGER', @codeMenuId, NOW());
