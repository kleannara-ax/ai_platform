-- ============================================================
--  PS 지분 검사 모듈 메뉴 등록
--  단독 메뉴: PS 지분 검사 (PS_INSP_MGMT)
--  검사 실행 / 검사 이력 / 이력 테이블은 페이지 내 탭으로 제공
-- ============================================================

-- 단독 메뉴: PS 지분 검사 (하위 메뉴 없음, 바로 페이지 로드)
INSERT INTO core_menu (menu_code, menu_name, parent_id, menu_url, icon, menu_type, sort_order, description, is_visible, is_active, allowed_ips, created_at, updated_at)
VALUES ('PS_INSP_MGMT', 'PS 지분 검사', NULL, '/ps-insp-api/page', 'ps_insp', 'MENU', 30, 'PS 지분 검사 (점보롤 지분 검사)', 1, 1, NULL, NOW(), NOW());

-- 역할별 메뉴 접근 권한
INSERT INTO core_role_menu (role, menu_id)
SELECT 'ROLE_ADMIN', menu_id FROM core_menu WHERE menu_code = 'PS_INSP_MGMT';

INSERT INTO core_role_menu (role, menu_id)
SELECT 'ROLE_MANAGER', menu_id FROM core_menu WHERE menu_code = 'PS_INSP_MGMT';

INSERT INTO core_role_menu (role, menu_id)
SELECT 'ROLE_USER', menu_id FROM core_menu WHERE menu_code = 'PS_INSP_MGMT';
