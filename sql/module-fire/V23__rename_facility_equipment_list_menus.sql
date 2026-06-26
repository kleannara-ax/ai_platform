SET NAMES utf8mb4;

-- ============================================================
-- V23: 기타설비 메뉴명을 소방설비 목록 메뉴 체계와 맞춤
--   - 에어컨 -> 에어컨 목록
--   - 정수기 -> 정수기 목록
-- ============================================================

UPDATE core_menu
SET MENU_NAME = '에어컨 목록',
    DESCRIPTION = '기타설비 에어컨 목록 및 점검 관리',
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'OTHER_AIRCON';

UPDATE core_menu
SET MENU_NAME = '정수기 목록',
    DESCRIPTION = '기타설비 정수기 목록 및 점검 관리',
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'OTHER_WATER_PURIFIER';
