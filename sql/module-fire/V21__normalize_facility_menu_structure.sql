SET NAMES utf8mb4;

-- ============================================================
-- V21: 설비관리시스템 메뉴 구조 보정
--   - 기타설비 하위에 프론트에서만 가상으로 추가되던 대시보드/도면/층별도면 정리
--   - 소방설비/기타설비 대시보드 메뉴는 메뉴관리/접근권한 대상에서 제거
--   - 도면(메인), 층별 도면은 설비관리시스템 바로 아래 공통 메뉴로 관리
-- ============================================================

SET @facility_parent_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_MGMT' LIMIT 1);
SET @fire_group_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_EQUIPMENT_GROUP' LIMIT 1);
SET @other_group_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'OTHER_EQUIPMENT_GROUP' LIMIT 1);

-- 1) 대시보드 메뉴 제거: 소방 대시보드와 운영 DB에 존재할 수 있는 기타설비 대시보드
DELETE rm
FROM core_role_menu rm
JOIN core_menu m ON m.MENU_ID = rm.MENU_ID
WHERE m.MENU_CODE IN ('FIRE_DASHBOARD', 'OTHER_DASHBOARD');

DELETE FROM core_menu
WHERE MENU_CODE IN ('FIRE_DASHBOARD', 'OTHER_DASHBOARD');

-- 2) 도면 메뉴는 기존 FIRE_MAP/FIRE_FLOOR 코드를 공통 메뉴로 재사용해 설비관리시스템 바로 아래로 이동
UPDATE core_menu
SET MENU_NAME = '도면 (메인)',
    PARENT_ID = @facility_parent_id,
    MENU_URL = '/fire/map',
    ICON = 'map',
    MENU_TYPE = 'MENU',
    SORT_ORDER = 1,
    DESCRIPTION = '설비관리시스템 공통 메인 도면',
    IS_VISIBLE = 1,
    IS_ACTIVE = 1,
    UPDATED_AT = NOW()
WHERE @facility_parent_id IS NOT NULL
  AND MENU_CODE = 'FIRE_MAP';

UPDATE core_menu
SET MENU_NAME = '층별 도면',
    PARENT_ID = @facility_parent_id,
    MENU_URL = '/fire/floor',
    ICON = 'floor',
    MENU_TYPE = 'MENU',
    SORT_ORDER = 2,
    DESCRIPTION = '설비관리시스템 공통 층별 도면',
    IS_VISIBLE = 1,
    IS_ACTIVE = 1,
    UPDATED_AT = NOW()
WHERE @facility_parent_id IS NOT NULL
  AND MENU_CODE = 'FIRE_FLOOR';

-- 3) 운영 DB에 별도 기타설비 도면 메뉴가 있으면 제거하여 중복 표시 방지
DELETE rm
FROM core_role_menu rm
JOIN core_menu m ON m.MENU_ID = rm.MENU_ID
WHERE m.MENU_CODE IN ('OTHER_MAP', 'OTHER_FLOOR');

DELETE FROM core_menu
WHERE MENU_CODE IN ('OTHER_MAP', 'OTHER_FLOOR');

-- 4) 설비관리시스템 하위 정렬 보정: 도면, 층별 도면, 소방설비, 기타설비 순서
UPDATE core_menu
SET SORT_ORDER = 3,
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'FIRE_EQUIPMENT_GROUP';

UPDATE core_menu
SET SORT_ORDER = 4,
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'OTHER_EQUIPMENT_GROUP';

-- 5) 역할별 접근권한 보정
-- ADMIN / 시설관리: 설비관리시스템, 공통 도면, 소방/기타 그룹 전체
INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT r.ROLE, m.MENU_ID, NOW()
FROM (
    SELECT 'ROLE_ADMIN' AS ROLE
    UNION ALL SELECT 'ROLE_FACILITY_MANAGER'
) r
JOIN core_menu m ON m.MENU_CODE IN ('FIRE_MGMT', 'FIRE_MAP', 'FIRE_FLOOR', 'FIRE_EQUIPMENT_GROUP', 'OTHER_EQUIPMENT_GROUP')
    OR m.MENU_CODE IN ('FIRE_EXTINGUISHER', 'FIRE_HYDRANT', 'FIRE_RECEIVER', 'FIRE_PUMP', 'FIRE_QR', 'OTHER_AIRCON', 'OTHER_WATER_PURIFIER')
ON DUPLICATE KEY UPDATE core_role_menu.CREATED_AT = core_role_menu.CREATED_AT;

-- 소방시설관리: 설비관리시스템 + 공통 도면 + 소방설비만
INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_FIRE_MANAGER', m.MENU_ID, NOW()
FROM core_menu m
WHERE m.MENU_CODE IN ('FIRE_MGMT', 'FIRE_MAP', 'FIRE_FLOOR', 'FIRE_EQUIPMENT_GROUP', 'FIRE_EXTINGUISHER', 'FIRE_HYDRANT', 'FIRE_RECEIVER', 'FIRE_PUMP', 'FIRE_QR')
ON DUPLICATE KEY UPDATE core_role_menu.CREATED_AT = core_role_menu.CREATED_AT;

DELETE rm
FROM core_role_menu rm
JOIN core_menu m ON m.MENU_ID = rm.MENU_ID
WHERE rm.ROLE = 'ROLE_FIRE_MANAGER'
  AND m.MENU_CODE LIKE 'OTHER_%';

-- 기타시설관리: 설비관리시스템 + 공통 도면 + 기타설비만
INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_EQUIPMENT_MANAGER', m.MENU_ID, NOW()
FROM core_menu m
WHERE m.MENU_CODE IN ('FIRE_MGMT', 'FIRE_MAP', 'FIRE_FLOOR', 'OTHER_EQUIPMENT_GROUP', 'OTHER_AIRCON', 'OTHER_WATER_PURIFIER')
ON DUPLICATE KEY UPDATE core_role_menu.CREATED_AT = core_role_menu.CREATED_AT;

DELETE rm
FROM core_role_menu rm
JOIN core_menu m ON m.MENU_ID = rm.MENU_ID
WHERE rm.ROLE = 'ROLE_EQUIPMENT_MANAGER'
  AND (m.MENU_CODE = 'FIRE_EQUIPMENT_GROUP'
       OR m.MENU_CODE IN ('FIRE_EXTINGUISHER', 'FIRE_HYDRANT', 'FIRE_RECEIVER', 'FIRE_PUMP', 'FIRE_QR'));
