-- ============================================================
--  PS 커버리지 검사 모듈 메뉴 등록
--  상위메뉴: PS 커버리지 검사 (PS_INSP_MGMT)
--  하위메뉴: PS 검사 도구, PS 검사 이력
-- ============================================================

-- 상위 메뉴: PS 커버리지 검사
INSERT INTO core_menu (menu_code, menu_name, parent_id, menu_url, icon, menu_type, sort_order, description, is_visible, is_active, allowed_ips, created_at, updated_at)
VALUES ('PS_INSP_MGMT', 'PS 커버리지 검사', NULL, NULL, 'ps_insp', 'MENU', 30, 'PS 커버리지 검사 모듈 (점보롤 지분 검사)', 1, 1, NULL, NOW(), NOW());

SET @ps_insp_parent_id = LAST_INSERT_ID();

-- 하위메뉴들
INSERT INTO core_menu (menu_code, menu_name, parent_id, menu_url, icon, menu_type, sort_order, description, is_visible, is_active, allowed_ips, created_at, updated_at)
VALUES
  ('PS_INSP_PAGE', 'PS 검사 도구', @ps_insp_parent_id, '/ps-insp-api/page', 'ps_insp_page', 'MENU', 1, 'PS 커버리지 검사 도구 (이미지 분석)', 1, 1, NULL, NOW(), NOW()),
  ('PS_INSP_HISTORY', 'PS 검사 이력', @ps_insp_parent_id, '/ps-insp-api/inspections', 'ps_insp_history', 'MENU', 2, 'PS 커버리지 검사 이력 조회', 1, 1, NULL, NOW(), NOW());

-- 역할별 메뉴 접근 권한 (ROLE_ADMIN에 모든 PS-INSP 메뉴 할당)
INSERT INTO core_role_menu (role, menu_id)
SELECT 'ROLE_ADMIN', menu_id FROM core_menu WHERE menu_code LIKE 'PS_INSP_%';

-- ROLE_MANAGER에도 PS-INSP 메뉴 할당
INSERT INTO core_role_menu (role, menu_id)
SELECT 'ROLE_MANAGER', menu_id FROM core_menu WHERE menu_code LIKE 'PS_INSP_%';

-- ROLE_USER에도 PS-INSP 메뉴 할당 (관리 기능은 코드에서 @PreAuthorize로 제어)
INSERT INTO core_role_menu (role, menu_id)
SELECT 'ROLE_USER', menu_id FROM core_menu WHERE menu_code LIKE 'PS_INSP_%';
