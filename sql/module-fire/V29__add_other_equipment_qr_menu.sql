SET NAMES utf8mb4;

-- ============================================================
-- V29: 기타설비 QR코드 메뉴 추가
--   - 기타설비 하위에 QR코드 메뉴를 메뉴관리/접근권한 관리 대상으로 추가
--   - ADMIN / 시설관리 / 기타시설관리 권한에 부여
--   - 소방시설관리 권한에는 기타설비 QR 접근권한을 부여하지 않음
-- ============================================================

SET @other_group_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'OTHER_EQUIPMENT_GROUP' LIMIT 1);

INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, DESCRIPTION, IS_VISIBLE, IS_ACTIVE, ALLOWED_IPS, CREATED_AT, UPDATED_AT)
SELECT 'OTHER_QR', 'QR코드', @other_group_id, '/facility/qr', 'qrcode', 'MENU', 3, '기타설비 QR코드 발급 및 조회', 1, 1, NULL, NOW(), NOW()
WHERE @other_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM core_menu WHERE MENU_CODE = 'OTHER_QR');

UPDATE core_menu
SET MENU_NAME = 'QR코드',
    PARENT_ID = @other_group_id,
    MENU_URL = '/facility/qr',
    ICON = 'qrcode',
    MENU_TYPE = 'MENU',
    SORT_ORDER = 3,
    DESCRIPTION = '기타설비 QR코드 발급 및 조회',
    IS_VISIBLE = 1,
    IS_ACTIVE = 1,
    UPDATED_AT = NOW()
WHERE @other_group_id IS NOT NULL
  AND MENU_CODE = 'OTHER_QR';

INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT r.ROLE, m.MENU_ID, NOW()
FROM (
    SELECT 'ROLE_ADMIN' AS ROLE
    UNION ALL SELECT 'ROLE_FACILITY_MANAGER'
    UNION ALL SELECT 'ROLE_EQUIPMENT_MANAGER'
) r
JOIN core_menu m ON m.MENU_CODE IN ('FIRE_MGMT', 'OTHER_EQUIPMENT_GROUP', 'OTHER_QR')
ON DUPLICATE KEY UPDATE core_role_menu.CREATED_AT = core_role_menu.CREATED_AT;

DELETE rm
FROM core_role_menu rm
JOIN core_menu m ON m.MENU_ID = rm.MENU_ID
WHERE rm.ROLE = 'ROLE_FIRE_MANAGER'
  AND m.MENU_CODE = 'OTHER_QR';
