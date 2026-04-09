-- ============================================================
--  소방시설관리 모듈 메뉴 등록
--  상위메뉴: 소방시설관리 (FIRE_MGMT)
--  하위메뉴: 대시보드, 도면, 소화기, 소화전, 수신기, 소방펌프, 층별도면, QR코드
-- ============================================================

-- 상위 메뉴: 소방시설관리
INSERT INTO core_menu (menu_code, menu_name, parent_id, menu_url, icon, menu_type, sort_order, description, is_visible, is_active, allowed_ips, created_at, updated_at)
VALUES ('FIRE_MGMT', '소방시설관리', NULL, NULL, 'fire', 'MENU', 20, '소방시설관리 모듈', 1, 1, NULL, NOW(), NOW());

SET @fire_parent_id = LAST_INSERT_ID();

-- 하위메뉴들
INSERT INTO core_menu (menu_code, menu_name, parent_id, menu_url, icon, menu_type, sort_order, description, is_visible, is_active, allowed_ips, created_at, updated_at)
VALUES
  ('FIRE_DASHBOARD', '대시보드', @fire_parent_id, '/fire/dashboard', 'dashboard', 'MENU', 1, '소방시설 대시보드', 1, 1, NULL, NOW(), NOW()),
  ('FIRE_MAP', '도면 (메인)', @fire_parent_id, '/fire/map', 'map', 'MENU', 2, '항공사진 기반 설비 위치 표시', 1, 1, NULL, NOW(), NOW()),
  ('FIRE_EXTINGUISHER', '소화기 목록', @fire_parent_id, '/fire/extinguishers', 'extinguisher', 'MENU', 3, '소화기 관리', 1, 1, NULL, NOW(), NOW()),
  ('FIRE_HYDRANT', '소화전 목록', @fire_parent_id, '/fire/hydrants', 'hydrant', 'MENU', 4, '소화전 관리', 1, 1, NULL, NOW(), NOW()),
  ('FIRE_RECEIVER', '수신기 목록', @fire_parent_id, '/fire/receivers', 'receiver', 'MENU', 5, '수신기 관리', 1, 1, NULL, NOW(), NOW()),
  ('FIRE_PUMP', '소방펌프 목록', @fire_parent_id, '/fire/pumps', 'pump', 'MENU', 6, '소방펌프 관리', 1, 1, NULL, NOW(), NOW()),
  ('FIRE_FLOOR', '층별 도면', @fire_parent_id, '/fire/floor', 'floor', 'MENU', 7, '건물 층별 도면', 1, 1, NULL, NOW(), NOW()),
  ('FIRE_QR', 'QR코드', @fire_parent_id, '/fire/qr', 'qr', 'MENU', 8, 'QR코드 관리', 1, 1, NULL, NOW(), NOW());

-- 역할별 메뉴 접근 권한 (ROLE_ADMIN에 모든 소방 메뉴 할당)
INSERT INTO core_role_menu (role, menu_id)
SELECT 'ROLE_ADMIN', menu_id FROM core_menu WHERE menu_code LIKE 'FIRE_%';

-- ROLE_USER에도 소방 메뉴 할당 (관리 기능은 코드에서 제어)
INSERT INTO core_role_menu (role, menu_id)
SELECT 'ROLE_USER', menu_id FROM core_menu WHERE menu_code LIKE 'FIRE_%';
