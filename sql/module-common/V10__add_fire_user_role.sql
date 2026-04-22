SET NAMES utf8mb4;

-- ============================================================
-- V10: 소방시설사용자(FIRE_USER) 역할 추가
--     소화기/소화전 목록 확인, 층별 도면 확인만 가능
--     점검/추가/삭제 등 변경 작업은 웹에서 불가 (QR 모바일 점검만 가능)
-- ============================================================

-- 1. ROLE 공통코드 그룹에 ROLE_FIRE_USER 추가
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT g.GROUP_ID, 'ROLE_FIRE_USER', '소방시설사용자', '소방시설 읽기 전용 (모바일 QR 점검만 가능)', 'fire_user', TRUE, 5, NOW(), NOW()
FROM code_group g WHERE g.GROUP_CODE = 'ROLE'
  AND NOT EXISTS (
    SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = 'ROLE_FIRE_USER'
  );

-- 이미 존재하는 경우 이름/설명 보정
UPDATE code_detail d
  JOIN code_group g ON d.GROUP_ID = g.GROUP_ID
SET d.CODE_NAME = '소방시설사용자',
    d.DESCRIPTION = '소방시설 읽기 전용 (모바일 QR 점검만 가능)',
    d.EXTRA_VALUE1 = 'fire_user',
    d.UPDATED_AT = NOW()
WHERE g.GROUP_CODE = 'ROLE' AND d.CODE = 'ROLE_FIRE_USER';

-- 2. ROLE_FIRE_USER에 소방시설 읽기 메뉴만 할당
--    소화기 목록, 소화전 목록, 층별 도면, 도면(메인) 만 접근 가능
--    수신기, 소방펌프, QR코드, 대시보드는 제외
INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_FIRE_USER', m.MENU_ID, NOW()
FROM core_menu m
WHERE m.MENU_CODE IN ('FIRE_MGMT', 'FIRE_EXTINGUISHER', 'FIRE_HYDRANT', 'FIRE_FLOOR', 'FIRE_MAP')
  AND NOT EXISTS (
    SELECT 1 FROM core_role_menu rm
    WHERE rm.ROLE = 'ROLE_FIRE_USER' AND rm.MENU_ID = m.MENU_ID
  );
