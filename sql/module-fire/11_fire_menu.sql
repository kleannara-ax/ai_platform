-- ============================================================
--  소방시설관리 모듈 - 메뉴 등록 스크립트
--  MariaDB 10.11+ (utf8mb4)
--  최종 갱신: 2026-04-08
-- ============================================================
-- 
-- 이 스크립트는 소방시설관리 메뉴(부모)와 하위 메뉴 3개를 core_menu에 등록하고
-- ROLE_ADMIN, ROLE_MANAGER, ROLE_USER에 접근 권한을 부여합니다.
--
-- 메뉴 구조:
--   소방시설관리 (FIRE_MGMT)        ← 부모 메뉴
--     ├─ 품목 등록 (FIRE_EQUIP)      ← 소화기/소화전/펌프/수신기 관리
--     ├─ 소방 도면 (FIRE_FLOOR)      ← 건물/층별 도면 및 장비 배치 조회
--     └─ QR 관리  (FIRE_QR)          ← QR 코드 조회/생성/미등록 시리얼
--
-- 실행 방법:
--   mariadb -u platform_user -p platform_db < sql/module-fire/11_fire_menu.sql
-- ============================================================

-- ── 1. 부모 메뉴: 소방시설관리 ──
-- 이미 존재하면 건너뜁니다 (MENU_CODE UNIQUE 제약)
INSERT IGNORE INTO core_menu
    (MENU_NAME, MENU_CODE, PARENT_ID, MENU_URL, ICON, SORT_ORDER, MENU_TYPE, IS_VISIBLE, IS_ACTIVE, DESCRIPTION)
VALUES
    ('소방시설관리', 'FIRE_MGMT', NULL, '/fire', 'fire', 10, 'MENU', 1, 1, '소방시설 통합 관리 (대시보드)');

-- 부모 MENU_ID 조회
SET @fireParentId = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_MGMT');

-- ── 2. 하위 메뉴 3개 ──
INSERT IGNORE INTO core_menu
    (MENU_NAME, MENU_CODE, PARENT_ID, MENU_URL, ICON, SORT_ORDER, MENU_TYPE, IS_VISIBLE, IS_ACTIVE, DESCRIPTION)
VALUES
    ('품목 등록', 'FIRE_EQUIP', @fireParentId, '/fire/equip', 'clipboard', 1, 'MENU', 1, 1, '소화기/소화전/펌프/수신기 품목 관리'),
    ('소방 도면', 'FIRE_FLOOR', @fireParentId, '/fire/floor', 'map',       2, 'MENU', 1, 1, '건물/층별 소방시설 배치도 조회'),
    ('QR 관리',   'FIRE_QR',    @fireParentId, '/fire/qr',    'qrcode',    3, 'MENU', 1, 1, 'QR 코드 조회/생성 및 미등록 시리얼 관리');

-- ── 3. 역할-메뉴 매핑 ──
-- ROLE_ADMIN: 부모 + 하위 3개 모두
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_ADMIN', MENU_ID, NOW()
  FROM core_menu
 WHERE MENU_CODE IN ('FIRE_MGMT', 'FIRE_EQUIP', 'FIRE_FLOOR', 'FIRE_QR');

-- ROLE_MANAGER: 부모 + 하위 3개 모두
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_MANAGER', MENU_ID, NOW()
  FROM core_menu
 WHERE MENU_CODE IN ('FIRE_MGMT', 'FIRE_EQUIP', 'FIRE_FLOOR', 'FIRE_QR');

-- ROLE_USER: 부모 + 하위 3개 모두 (소방시설 조회는 전 직원 필요)
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_USER', MENU_ID, NOW()
  FROM core_menu
 WHERE MENU_CODE IN ('FIRE_MGMT', 'FIRE_EQUIP', 'FIRE_FLOOR', 'FIRE_QR');

-- ── 확인 쿼리 ──
-- SELECT m.MENU_ID, m.MENU_NAME, m.MENU_CODE, m.PARENT_ID,
--        (SELECT rm.ROLE FROM core_role_menu rm WHERE rm.MENU_ID = m.MENU_ID)
--   FROM core_menu m
--  WHERE m.MENU_CODE LIKE 'FIRE%'
--  ORDER BY m.SORT_ORDER;
