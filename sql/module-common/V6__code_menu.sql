-- ============================================================
--  공통코드 관리 - 메뉴 등록 스크립트
--  MariaDB 10.11+ (utf8mb4)
--  최종 갱신: 2026-04-06
-- ============================================================

-- 공통코드 관리 메뉴 추가
INSERT INTO CORE_MENU (MENU_NAME, MENU_CODE, PARENT_ID, MENU_URL, ICON, SORT_ORDER, MENU_TYPE, IS_VISIBLE, IS_ACTIVE, DESCRIPTION)
VALUES ('공통코드 관리', 'CODE_MGMT', NULL, '/codes', 'code', 5, 'MENU', 1, 1, '공통코드 그룹 및 상세 코드 관리');

SET @codeMenuId = LAST_INSERT_ID();

-- ROLE_ADMIN에 공통코드 관리 메뉴 접근 권한 부여
INSERT INTO CORE_ROLE_MENU (ROLE, MENU_ID, CREATED_AT)
VALUES ('ROLE_ADMIN', @codeMenuId, NOW());

-- ROLE_MANAGER에도 조회 권한 부여
INSERT INTO CORE_ROLE_MENU (ROLE, MENU_ID, CREATED_AT)
VALUES ('ROLE_MANAGER', @codeMenuId, NOW());
