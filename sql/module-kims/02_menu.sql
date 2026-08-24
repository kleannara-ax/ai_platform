-- ============================================================
--  module-kims: KIMS(IT 운영 관리) 플랫폼 메뉴 등록
--  KIMS_MGMT(그룹) 아래 페이지 메뉴. 각 페이지 menu_url = /kims/{page}.html
--  (SPA 가 /kims/ 로 시작하는 메뉴를 iframe 으로 로드한다)
--  재실행 안전: 기존 KIMS_ 메뉴/권한 삭제 후 재삽입.
--  ※ 운영 DB 에서 메뉴를 바꿨다면 이 파일도 함께 갱신할 것.
-- ============================================================

-- 기존 KIMS 메뉴 정리
DELETE rm FROM core_role_menu rm JOIN core_menu m ON rm.MENU_ID = m.MENU_ID
  WHERE m.MENU_CODE LIKE 'KIMS_%';
DELETE FROM core_menu WHERE MENU_CODE LIKE 'KIMS_%';

-- 상위 그룹 (URL 없는 그룹 메뉴)
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
VALUES ('KIMS_MGMT', 'IT 운영 관리', NULL, NULL, 'desktop', 'MENU', 60, 1, 1, NOW());

-- 페이지 (parent = KIMS_MGMT)
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
SELECT 'KIMS_DASHBOARD', '대시보드', MENU_ID, '/kims/dashboard.html', 'chart-line', 'MENU', 1, 1, 1, NOW()
FROM core_menu WHERE MENU_CODE = 'KIMS_MGMT';
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
SELECT 'KIMS_REQUEST', '업무 요청', MENU_ID, '/kims/request.html', 'clipboard-list', 'MENU', 2, 1, 1, NOW()
FROM core_menu WHERE MENU_CODE = 'KIMS_MGMT';
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
SELECT 'KIMS_INVENTORY', '소모품 관리', MENU_ID, '/kims/inventory.html', 'boxes', 'MENU', 3, 1, 1, NOW()
FROM core_menu WHERE MENU_CODE = 'KIMS_MGMT';
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
SELECT 'KIMS_IP', 'PC 관리', MENU_ID, '/kims/ip.html', 'desktop', 'MENU', 4, 1, 1, NOW()
FROM core_menu WHERE MENU_CODE = 'KIMS_MGMT';
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
SELECT 'KIMS_SETTLEMENT', '월말 결산', MENU_ID, '/kims/settlement.html', 'calendar-check', 'MENU', 5, 1, 1, NOW()
FROM core_menu WHERE MENU_CODE = 'KIMS_MGMT';
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
SELECT 'KIMS_QR', 'QR 구역 관리', MENU_ID, '/kims/qr.html', 'qrcode', 'MENU', 6, 1, 1, NOW()
FROM core_menu WHERE MENU_CODE = 'KIMS_MGMT';
-- 사용자(권한) 화면은 제거됨 — 계정은 플랫폼 사용자 관리, KIMS 관리자는 공통코드(KIMS_PERM) 에서 관리한다.

-- 역할별 메뉴 노출 권한
--  실제 API 권한은 공통코드 KIMS_PERM (+ 플랫폼 MANAGER) 로 판정한다. 02_perm_code.sql 참고.
INSERT INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_ADMIN', MENU_ID FROM core_menu WHERE MENU_CODE LIKE 'KIMS_%';
INSERT INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_MANAGER', MENU_ID FROM core_menu WHERE MENU_CODE LIKE 'KIMS_%';
