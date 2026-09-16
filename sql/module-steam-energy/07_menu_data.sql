-- ============================================================
--  스팀에너지관리 모듈 메뉴 등록
--  상위 그룹: 스팀에너지관리 (STEAM_ENERGY_MGMT, menu_url 없음)
--  하위 그룹/페이지는 08_submenus.sql 에서 등록한다.
--  (레거시 셸 /steam/index.html 은 폐기되어 더 이상 메뉴로 연결하지 않는다)
-- ============================================================

-- 상위 그룹 등록 (menu_url 없음 → SPA 에서 토글 그룹으로 렌더)
--  menu_code 가 UNIQUE 라 그냥 INSERT 하면 재실행 시 실패한다. 있으면 갱신만 한다.
INSERT INTO core_menu (menu_code, menu_name, parent_id, menu_url, icon, menu_type, sort_order, description, is_visible, is_active, created_at, updated_at)
VALUES ('STEAM_ENERGY_MGMT', '스팀에너지관리', NULL, NULL, 'steam_energy', 'MENU', 40, '스팀 대시보드 상위 그룹 (하위 페이지는 08_submenus.sql)', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name), menu_url = VALUES(menu_url), icon = VALUES(icon),
    sort_order = VALUES(sort_order), description = VALUES(description),
    is_visible = VALUES(is_visible), is_active = VALUES(is_active), updated_at = NOW();

-- 역할별 메뉴 접근 권한 (중복 방지)
INSERT INTO core_role_menu (role, menu_id)
SELECT r.role, m.menu_id
FROM core_menu m
JOIN (SELECT 'ROLE_ADMIN' AS role UNION ALL SELECT 'ROLE_MANAGER' UNION ALL SELECT 'ROLE_USER') r
WHERE m.menu_code = 'STEAM_ENERGY_MGMT'
  AND NOT EXISTS (
      SELECT 1 FROM core_role_menu rm WHERE rm.role = r.role AND rm.menu_id = m.menu_id
  );
