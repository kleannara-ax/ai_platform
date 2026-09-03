-- ============================================================
--  module-safety: 안전작업 매뉴얼 플랫폼 메뉴 등록
--
--  SAFETY_MGMT(그룹) 아래 페이지 메뉴. MENU_URL = /safety/index.html
--  (SPA 가 /safety/ 로 시작하는 메뉴를 iframe 으로 로드한다)
--  엑셀 일괄업로드는 별도 페이지가 아니라 index.html 안의 모달로 통합되어
--  독립 메뉴가 아니다.
--
--  재실행 안전:
--    메뉴는 MENU_CODE UNIQUE 키 기준 upsert, 역할 매핑은 INSERT IGNORE 로 처리한다.
--    core 메뉴를 지웠다 다시 넣지 않는다 — MENU_ID 가 바뀌면 운영자가 손봐 둔
--    역할 매핑·정렬 순서가 함께 날아가기 때문이다.
--    SORT_ORDER 는 운영자가 조정할 수 있는 값이라 최초 등록 때만 넣고 이후 덮어쓰지 않는다.
-- ============================================================

SET NAMES utf8mb4;

-- 1) 상위 그룹 (URL 없는 그룹 메뉴)
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
VALUES ('SAFETY_MGMT', '안전작업 매뉴얼', NULL, NULL, 'shield-alt', 'MENU', 70, 1, 1, NOW())
ON DUPLICATE KEY UPDATE
    MENU_NAME  = VALUES(MENU_NAME),
    MENU_URL   = VALUES(MENU_URL),
    ICON       = VALUES(ICON),
    MENU_TYPE  = VALUES(MENU_TYPE),
    IS_VISIBLE = VALUES(IS_VISIBLE),
    IS_ACTIVE  = VALUES(IS_ACTIVE);

-- 2) 페이지 (parent = SAFETY_MGMT) — 페이지 1개뿐 (엑셀 업로드는 이 화면의 모달)
--    같은 테이블을 참조하므로 파생 테이블로 한 번 감싼다 (MariaDB 제약).
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
VALUES ('SAFETY_CATEGORY', '안전작업 매뉴얼',
        (SELECT MENU_ID FROM (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'SAFETY_MGMT') AS g),
        '/safety/index.html', 'list', 'MENU', 1, 1, 1, NOW())
ON DUPLICATE KEY UPDATE
    MENU_NAME  = VALUES(MENU_NAME),
    PARENT_ID  = VALUES(PARENT_ID),
    MENU_URL   = VALUES(MENU_URL),
    ICON       = VALUES(ICON),
    MENU_TYPE  = VALUES(MENU_TYPE),
    IS_VISIBLE = VALUES(IS_VISIBLE),
    IS_ACTIVE  = VALUES(IS_ACTIVE);

-- 3) 과거 버전에서 쓰던 엑셀 업로드 전용 메뉴는 더 이상 쓰지 않는다.
--    행을 지우지 않고 숨김/비활성으로만 돌린다 (되돌릴 수 있게).
UPDATE core_menu SET IS_VISIBLE = 0, IS_ACTIVE = 0 WHERE MENU_CODE = 'SAFETY_UPLOAD';

-- 4) 역할별 메뉴 노출 권한 (ROLE + MENU_ID UNIQUE 이므로 중복 삽입은 무시된다)
--    실제 API 관리자 권한은 공통코드 SAFETY_PERM (+ 플랫폼 ROLE_ADMIN) 로 판정한다. 03_perm_code.sql 참고.
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_ADMIN',   MENU_ID FROM core_menu WHERE MENU_CODE IN ('SAFETY_MGMT', 'SAFETY_CATEGORY');
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_MANAGER', MENU_ID FROM core_menu WHERE MENU_CODE IN ('SAFETY_MGMT', 'SAFETY_CATEGORY');
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_USER',    MENU_ID FROM core_menu WHERE MENU_CODE IN ('SAFETY_MGMT', 'SAFETY_CATEGORY');

SELECT '--- 등록된 SAFETY 메뉴 ---' AS '';
SELECT MENU_ID, MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, IS_VISIBLE, IS_ACTIVE
FROM core_menu WHERE MENU_CODE LIKE 'SAFETY_%' ORDER BY SORT_ORDER, MENU_ID;
