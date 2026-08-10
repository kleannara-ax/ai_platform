-- ============================================================
-- module-autodrawing: 메뉴 등록
-- ============================================================

-- 1. 자동도면 메뉴 추가
INSERT INTO core_menu (MENU_NAME, MENU_CODE, PARENT_ID, MENU_URL, ICON, SORT_ORDER, MENU_TYPE, IS_VISIBLE, IS_ACTIVE, DESCRIPTION)
VALUES ('자동 도면', 'AUTODRAWING_MGMT', NULL, '/autodrawing', 'drafting-compass', 10, 'MENU', 1, 1, 'AI 기반 자동 도면 생성');

-- 2. 역할별 접근 권한 매핑 (MENU_ID는 위 INSERT 결과에 맞게 조정)
INSERT INTO core_role_menu (ROLE, MENU_ID) VALUES
    ('ROLE_ADMIN',   LAST_INSERT_ID()),
    ('ROLE_MANAGER', LAST_INSERT_ID()),
    ('ROLE_USER',    LAST_INSERT_ID());

-- 3. (선택) 세부 권한 추가
INSERT INTO core_permission (PERM_CODE, PERM_NAME, DESCRIPTION, IS_ACTIVE) VALUES
    ('AUTODRAWING_READ',  '자동도면 조회', '자동도면 프로젝트 조회', 1),
    ('AUTODRAWING_WRITE', '자동도면 관리', '자동도면 프로젝트 생성/수정/삭제', 1);

-- 4. (선택) 역할-권한 매핑
INSERT INTO core_role_permission (ROLE, PERM_ID) VALUES
    ('ROLE_ADMIN',   (SELECT PERM_ID FROM core_permission WHERE PERM_CODE='AUTODRAWING_READ')),
    ('ROLE_ADMIN',   (SELECT PERM_ID FROM core_permission WHERE PERM_CODE='AUTODRAWING_WRITE')),
    ('ROLE_MANAGER', (SELECT PERM_ID FROM core_permission WHERE PERM_CODE='AUTODRAWING_READ')),
    ('ROLE_MANAGER', (SELECT PERM_ID FROM core_permission WHERE PERM_CODE='AUTODRAWING_WRITE')),
    ('ROLE_USER',    (SELECT PERM_ID FROM core_permission WHERE PERM_CODE='AUTODRAWING_READ'));
