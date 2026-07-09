SET NAMES utf8mb4;

-- ============================================================
-- V30: 소방설비 QR코드 메뉴 상위 이동
--   - FIRE_QR 메뉴를 소방설비 그룹이 아니라 설비관리시스템 바로 아래 공통 메뉴로 이동
--   - 도면(메인), 층별 도면과 같은 레벨에서 메뉴관리/접근권한에 표시
--   - 기존 소방 QR 기능/URL은 유지하고, 권한은 ADMIN / 시설관리 / 소방시설관리에게 유지
-- ============================================================

SET @facility_parent_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_MGMT' LIMIT 1);
SET @fire_qr_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_QR' LIMIT 1);

-- FIRE_QR가 누락된 환경까지 보정한다.
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, DESCRIPTION, IS_VISIBLE, IS_ACTIVE, ALLOWED_IPS, CREATED_AT, UPDATED_AT)
SELECT 'FIRE_QR', 'QR코드', @facility_parent_id, '/fire/qr', 'qr', 'MENU', 3, '소방설비 QR코드 발급 및 조회', 1, 1, NULL, NOW(), NOW()
WHERE @facility_parent_id IS NOT NULL
  AND @fire_qr_id IS NULL;

SET @fire_qr_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_QR' LIMIT 1);

-- 1) 메뉴관리 트리: 설비관리시스템 > 도면 (메인) > 층별 도면 > QR코드 > 소방설비 > 기타설비
UPDATE core_menu
SET MENU_NAME = 'QR코드',
    PARENT_ID = @facility_parent_id,
    MENU_URL = '/fire/qr',
    ICON = 'qr',
    MENU_TYPE = 'MENU',
    SORT_ORDER = 3,
    DESCRIPTION = '소방설비 QR코드 발급 및 조회',
    IS_VISIBLE = 1,
    IS_ACTIVE = 1,
    UPDATED_AT = NOW()
WHERE @facility_parent_id IS NOT NULL
  AND MENU_CODE = 'FIRE_QR';

UPDATE core_menu
SET SORT_ORDER = 1,
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'FIRE_MAP';

UPDATE core_menu
SET SORT_ORDER = 2,
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'FIRE_FLOOR';

UPDATE core_menu
SET SORT_ORDER = 4,
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'FIRE_EQUIPMENT_GROUP';

UPDATE core_menu
SET SORT_ORDER = 5,
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'OTHER_EQUIPMENT_GROUP';

-- 2) 접근권한: 소방 QR은 기존대로 ADMIN / 시설관리 / 소방시설관리에게 유지한다.
INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT r.ROLE, m.MENU_ID, NOW()
FROM (
    SELECT 'ROLE_ADMIN' AS ROLE
    UNION ALL SELECT 'ROLE_FACILITY_MANAGER'
    UNION ALL SELECT 'ROLE_FIRE_MANAGER'
) r
JOIN core_menu m ON m.MENU_CODE IN ('FIRE_MGMT', 'FIRE_QR')
ON DUPLICATE KEY UPDATE core_role_menu.CREATED_AT = core_role_menu.CREATED_AT;

-- 기타시설관리 권한에는 소방 QR을 부여하지 않는다.
DELETE rm
FROM core_role_menu rm
JOIN core_menu m ON m.MENU_ID = rm.MENU_ID
WHERE rm.ROLE = 'ROLE_EQUIPMENT_MANAGER'
  AND m.MENU_CODE = 'FIRE_QR';
