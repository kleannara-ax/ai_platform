-- ============================================================
--  module-safety: 안전작업방식 매뉴얼 플랫폼 메뉴 등록
--  SAFETY_MGMT(그룹) 아래 페이지 메뉴. menu_url = /safety/{page}.html
--  (SPA 가 /safety/ 로 시작하는 메뉴를 iframe 으로 로드한다)
--  재실행 안전: 기존 SAFETY_ 메뉴/권한 삭제 후 재삽입.
-- ============================================================

DELETE rm FROM core_role_menu rm JOIN core_menu m ON rm.MENU_ID = m.MENU_ID
  WHERE m.MENU_CODE LIKE 'SAFETY_%';
DELETE FROM core_menu WHERE MENU_CODE LIKE 'SAFETY_%';

-- 상위 그룹 (URL 없는 그룹 메뉴)
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
VALUES ('SAFETY_MGMT', '안전작업방식 매뉴얼', NULL, NULL, 'shield-alt', 'MENU', 70, 1, 1, NOW());

-- 페이지 (parent = SAFETY_MGMT)
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
SELECT 'SAFETY_CATEGORY', '분류/매뉴얼 목록', MENU_ID, '/safety/index.html', 'list', 'MENU', 1, 1, 1, NOW()
FROM core_menu WHERE MENU_CODE = 'SAFETY_MGMT';
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
SELECT 'SAFETY_UPLOAD', '엑셀 일괄업로드', MENU_ID, '/safety/upload.html', 'file-excel', 'MENU', 2, 1, 1, NOW()
FROM core_menu WHERE MENU_CODE = 'SAFETY_MGMT';

-- 역할별 메뉴 노출 권한
--  실제 API 관리자 권한은 공통코드 SAFETY_PERM (+ 플랫폼 ROLE_ADMIN) 로 판정한다. 03_perm_code.sql 참고.
INSERT INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_ADMIN', MENU_ID FROM core_menu WHERE MENU_CODE LIKE 'SAFETY_%';
INSERT INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_MANAGER', MENU_ID FROM core_menu WHERE MENU_CODE LIKE 'SAFETY_%';
INSERT INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_USER', MENU_ID FROM core_menu WHERE MENU_CODE = 'SAFETY_MGMT';
INSERT INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_USER', MENU_ID FROM core_menu WHERE MENU_CODE = 'SAFETY_CATEGORY';
