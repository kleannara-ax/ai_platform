-- ============================================================
--  소방시설관리 모듈 - 청주창고 메뉴 등록 스크립트
--  MariaDB 10.11+ (utf8mb4)
--  최종 갱신: 2026-04-08
-- ============================================================
--
-- 이 스크립트는 소방시설관리 메뉴 하위에 '청주창고' 메뉴를 추가합니다.
--
-- 메뉴 구조:
--   소방시설관리 (FIRE_MGMT)        <- 기존 부모 메뉴
--     ├─ 품목 등록 (FIRE_EQUIP)      <- 기존
--     ├─ 소방 도면 (FIRE_FLOOR)      <- 기존
--     ├─ 청주창고  (FIRE_CHEONGJU)   <- 신규: 청주창고 전용 도면
--     └─ QR 관리  (FIRE_QR)          <- 기존
--
-- 실행 방법:
--   mariadb -u platform_user -p platform_db < sql/module-fire/13_fire_cheongju_menu.sql
-- ============================================================

-- 부모 MENU_ID 조회
SET @fireParentId = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_MGMT');

-- 청주창고 하위 메뉴 추가
INSERT IGNORE INTO core_menu
    (MENU_NAME, MENU_CODE, PARENT_ID, MENU_URL, ICON, SORT_ORDER, MENU_TYPE, IS_VISIBLE, IS_ACTIVE, DESCRIPTION)
VALUES
    ('청주창고', 'FIRE_CHEONGJU', @fireParentId, '/fire/cheongju', 'warehouse', 4, 'MENU', 1, 1, '청주창고 전용 소방시설 도면 및 장비 배치 관리');

-- 역할-메뉴 매핑: ROLE_ADMIN, ROLE_MANAGER, ROLE_USER 모두 접근 가능
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_ADMIN', MENU_ID, NOW()
  FROM core_menu
 WHERE MENU_CODE = 'FIRE_CHEONGJU';

INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_MANAGER', MENU_ID, NOW()
  FROM core_menu
 WHERE MENU_CODE = 'FIRE_CHEONGJU';

INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_USER', MENU_ID, NOW()
  FROM core_menu
 WHERE MENU_CODE = 'FIRE_CHEONGJU';
