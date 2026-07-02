SET NAMES utf8mb4;

-- ============================================================
-- V27: 스프링클러 메뉴명/라우팅 정리
--   - 과거 메뉴 코드/명칭(FIRE_SPRINKLER_PIPE, 스프링쿨러 배관 목록)을
--     현재 표준 메뉴(FIRE_SPRINKLER, 스프링클러 목록)로 통합
--   - 메뉴관리/접근권한 화면에서도 스프링클러 목록으로 표시되도록 보정
-- ============================================================

SET @fire_group_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_EQUIPMENT_GROUP' LIMIT 1);
SET @fire_parent_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_MGMT' LIMIT 1);
SET @sprinkler_parent_id = COALESCE(@fire_group_id, @fire_parent_id);

-- 1) FIRE_SPRINKLER가 없고 과거 FIRE_SPRINKLER_PIPE만 있으면 기존 행을 표준 코드로 승격
UPDATE core_menu
SET MENU_CODE = 'FIRE_SPRINKLER',
    MENU_NAME = '스프링클러 목록',
    PARENT_ID = @sprinkler_parent_id,
    MENU_URL = '/fire/sprinklers',
    ICON = 'sprinkler',
    MENU_TYPE = 'MENU',
    SORT_ORDER = 5,
    DESCRIPTION = '스프링클러 목록 및 점검 관리',
    IS_VISIBLE = 1,
    IS_ACTIVE = 1,
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'FIRE_SPRINKLER_PIPE'
  AND @sprinkler_parent_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_SPRINKLER') existing_sprinkler
  );

-- 2) 표준 메뉴가 없으면 신규 생성
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, DESCRIPTION, IS_VISIBLE, IS_ACTIVE, ALLOWED_IPS, CREATED_AT, UPDATED_AT)
SELECT 'FIRE_SPRINKLER', '스프링클러 목록', @sprinkler_parent_id, '/fire/sprinklers', 'sprinkler', 'MENU', 5, '스프링클러 목록 및 점검 관리', 1, 1, NULL, NOW(), NOW()
WHERE @sprinkler_parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM core_menu WHERE MENU_CODE = 'FIRE_SPRINKLER');

-- 3) 표준 메뉴명/URL을 강제 보정
UPDATE core_menu
SET MENU_NAME = '스프링클러 목록',
    PARENT_ID = @sprinkler_parent_id,
    MENU_URL = '/fire/sprinklers',
    ICON = 'sprinkler',
    MENU_TYPE = 'MENU',
    SORT_ORDER = 5,
    DESCRIPTION = '스프링클러 목록 및 점검 관리',
    IS_VISIBLE = 1,
    IS_ACTIVE = 1,
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'FIRE_SPRINKLER'
  AND @sprinkler_parent_id IS NOT NULL;

-- 4) 표준 메뉴가 별도로 존재하는 경우 과거 메뉴/권한은 제거하여 사이드바/메뉴관리 중복 노출 방지
DELETE rm
FROM core_role_menu rm
JOIN core_menu m ON m.MENU_ID = rm.MENU_ID
WHERE m.MENU_CODE = 'FIRE_SPRINKLER_PIPE'
  AND EXISTS (SELECT 1 FROM (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_SPRINKLER') existing_sprinkler);

DELETE FROM core_menu
WHERE MENU_CODE = 'FIRE_SPRINKLER_PIPE'
  AND EXISTS (SELECT 1 FROM (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_SPRINKLER') existing_sprinkler);

-- 5) 혹시 명칭만 남아있는 과거 오탈자 메뉴가 있으면 표준 메뉴명/URL로 보정
UPDATE core_menu
SET MENU_NAME = '스프링클러 목록',
    MENU_URL = '/fire/sprinklers',
    ICON = 'sprinkler',
    UPDATED_AT = NOW()
WHERE MENU_NAME IN ('스프링쿨러 배관 목록', '스프링쿨러 배관', '스프링클러 배관 목록')
  AND MENU_CODE = 'FIRE_SPRINKLER';

-- 6) 접근권한 부여 보정
INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT r.ROLE, m.MENU_ID, NOW()
FROM (
    SELECT 'ROLE_ADMIN' AS ROLE
    UNION ALL SELECT 'ROLE_FACILITY_MANAGER'
    UNION ALL SELECT 'ROLE_FIRE_MANAGER'
) r
JOIN core_menu m ON m.MENU_CODE = 'FIRE_SPRINKLER'
ON DUPLICATE KEY UPDATE core_role_menu.CREATED_AT = core_role_menu.CREATED_AT;
