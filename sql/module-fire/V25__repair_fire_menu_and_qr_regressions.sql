-- Repair menu data after adding sprinkler pipe and facility equipment list pages.
-- Idempotent migration: safe to re-run on databases where V23/V24 were partially applied.

SET @fire_parent_id := (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_EQUIPMENT_GROUP' LIMIT 1);
SET @other_parent_id := (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'OTHER_EQUIPMENT_GROUP' LIMIT 1);

-- Ensure the sprinkler pipe menu is present under the fire equipment group.
INSERT INTO core_menu (
    MENU_NAME, MENU_CODE, PARENT_ID, MENU_URL, ICON, SORT_ORDER,
    MENU_TYPE, IS_VISIBLE, IS_ACTIVE, DESCRIPTION, CREATED_AT, UPDATED_AT
)
SELECT
    '스프링쿨러 배관 목록', 'FIRE_SPRINKLER_PIPE', @fire_parent_id,
    '/fire/sprinkler-pipes', 'sprinkler_pipe', 5,
    'MENU', 1, 1, '스프링쿨러 배관 목록 및 점검 관리', NOW(), NOW()
WHERE @fire_parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM core_menu WHERE MENU_CODE = 'FIRE_SPRINKLER_PIPE');

UPDATE core_menu
SET MENU_NAME = '스프링쿨러 배관 목록',
    PARENT_ID = @fire_parent_id,
    MENU_URL = '/fire/sprinkler-pipes',
    ICON = 'sprinkler_pipe',
    SORT_ORDER = 5,
    MENU_TYPE = 'MENU',
    IS_VISIBLE = 1,
    IS_ACTIVE = 1,
    DESCRIPTION = '스프링쿨러 배관 목록 및 점검 관리',
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'FIRE_SPRINKLER_PIPE'
  AND @fire_parent_id IS NOT NULL;

-- Keep QR after sprinkler pipe.
UPDATE core_menu
SET SORT_ORDER = 6,
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'FIRE_QR';

-- Restore facility equipment list labels.
UPDATE core_menu
SET MENU_NAME = '에어컨 목록',
    PARENT_ID = COALESCE(@other_parent_id, PARENT_ID),
    MENU_URL = '/facility/air-conditioners',
    DESCRIPTION = '기타설비 에어컨 목록 및 점검 관리',
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'OTHER_AIRCON';

UPDATE core_menu
SET MENU_NAME = '정수기 목록',
    PARENT_ID = COALESCE(@other_parent_id, PARENT_ID),
    MENU_URL = '/facility/water-purifiers',
    DESCRIPTION = '기타설비 정수기 목록 및 점검 관리',
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'OTHER_WATER_PURIFIER';

-- Ensure all intended roles can see sprinkler pipe.
SET @sprinkler_menu_id := (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_SPRINKLER_PIPE' LIMIT 1);

INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT role_name, @sprinkler_menu_id, NOW()
FROM (
    SELECT 'ROLE_ADMIN' AS role_name
    UNION ALL SELECT 'ROLE_FACILITY_MANAGER'
    UNION ALL SELECT 'ROLE_FIRE_MANAGER'
) roles
WHERE @sprinkler_menu_id IS NOT NULL;
