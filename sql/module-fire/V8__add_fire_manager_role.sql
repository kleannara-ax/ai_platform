SET NAMES utf8mb4;

-- V8: 소방시설관리(FIRE_MANAGER) 역할 추가
-- 소방시설관리 매니저 역할

-- ============================================================
--  1. ROLE 공통코드 그룹에 ROLE_FIRE_MANAGER 추가
-- ============================================================
-- 이미 존재하면 CODE_NAME, DESCRIPTION, EXTRA_VALUE1을 업데이트
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT g.GROUP_ID, 'ROLE_FIRE_MANAGER', '소방시설관리', '소방시설관리 매니저', 'fire_manager', TRUE, 4, NOW(), NOW()
FROM code_group g WHERE g.GROUP_CODE = 'ROLE'
  AND NOT EXISTS (
    SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = 'ROLE_FIRE_MANAGER'
  );

-- 이미 존재하는 경우 이름/설명/부가값 보정
UPDATE code_detail d
  JOIN code_group g ON d.GROUP_ID = g.GROUP_ID
SET d.CODE_NAME = '소방시설관리',
    d.DESCRIPTION = '소방시설관리 매니저',
    d.EXTRA_VALUE1 = 'fire_manager',
    d.UPDATED_AT = NOW()
WHERE g.GROUP_CODE = 'ROLE' AND d.CODE = 'ROLE_FIRE_MANAGER';

-- ============================================================
--  2. ROLE_FIRE_MANAGER에 소방시설관리 메뉴 접근 권한 부여
-- ============================================================
-- 대시보드(DASHBOARD) + 소방시설관리 전체 하위메뉴(FIRE_%)를 할당
-- 이미 매핑된 메뉴는 건너뜀 (중복 방지)
INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_FIRE_MANAGER', m.MENU_ID, NOW()
FROM core_menu m
WHERE (m.MENU_CODE = 'DASHBOARD' OR m.MENU_CODE LIKE 'FIRE_%')
  AND NOT EXISTS (
    SELECT 1 FROM core_role_menu rm
    WHERE rm.ROLE = 'ROLE_FIRE_MANAGER' AND rm.MENU_ID = m.MENU_ID
  );
