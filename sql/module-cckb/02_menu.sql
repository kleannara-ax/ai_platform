-- ============================================================
--  module-cckb: CCKB 후면 색상검사 플랫폼 메뉴 등록
--
--  CCKB_MGMT(독립 최상위 그룹) 아래 페이지 메뉴 1개. MENU_URL = /cckb/index.html
--  (SPA 가 /cckb/ 로 시작하는 메뉴를 iframe 으로 로드한다 — Method B, cckb:: 동적 prefix
--   패턴은 app/src/main/resources/static/index.html 에 적용해야 하며, 이는 "모듈 격리
--   원칙 보류" 로 현재 별도 반영 대기 중. sql/module-cckb/README.md 참고)
--
--  ★ 사용자 명시 지시: "메뉴 이름은 독립 그룹으로 넣어야 함"
--    → 기존 Fire/Safety 등 다른 그룹 밑에 넣지 않고, PARENT_ID=NULL 인 최상위 그룹으로 등록.
--
--  재실행 안전:
--    메뉴는 MENU_CODE UNIQUE 키 기준 upsert, 역할 매핑은 INSERT IGNORE 로 처리한다.
--    core 메뉴를 지웠다 다시 넣지 않는다 — MENU_ID 가 바뀌면 운영자가 손봐 둔
--    역할 매핑·정렬 순서가 함께 날아가기 때문이다.
--    SORT_ORDER 는 운영자가 조정할 수 있는 값이라 최초 등록 때만 넣고 이후 덮어쓰지 않는다.
-- ============================================================

SET NAMES utf8mb4;

-- 1) 상위 그룹 (URL 없는 독립 그룹 메뉴) — 사용자 지시에 따라 다른 모듈 그룹 하위가 아닌
--    최상위 독립 그룹으로 등록한다 (PARENT_ID = NULL).
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
VALUES ('CCKB_MGMT', 'CCKB 후면 색상검사', NULL, NULL, 'palette', 'MENU', 80, 1, 1, NOW())
ON DUPLICATE KEY UPDATE
    MENU_NAME  = VALUES(MENU_NAME),
    MENU_URL   = VALUES(MENU_URL),
    ICON       = VALUES(ICON),
    MENU_TYPE  = VALUES(MENU_TYPE),
    IS_VISIBLE = VALUES(IS_VISIBLE),
    IS_ACTIVE  = VALUES(IS_ACTIVE);

-- 2) 페이지 (parent = CCKB_MGMT) — 페이지 1개 (원본 V4 HTML 의 3개 탭이 이 화면 안에 모두 포함됨)
--    같은 테이블을 참조하므로 파생 테이블로 한 번 감싼다 (MariaDB 제약).
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
VALUES ('CCKB_INSPECT', 'CCKB 후면 색상검사',
        (SELECT MENU_ID FROM (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'CCKB_MGMT') AS g),
        '/cckb/index.html', 'eye-dropper', 'MENU', 1, 1, 1, NOW())
ON DUPLICATE KEY UPDATE
    MENU_NAME  = VALUES(MENU_NAME),
    PARENT_ID  = VALUES(PARENT_ID),
    MENU_URL   = VALUES(MENU_URL),
    ICON       = VALUES(ICON),
    MENU_TYPE  = VALUES(MENU_TYPE),
    IS_VISIBLE = VALUES(IS_VISIBLE),
    IS_ACTIVE  = VALUES(IS_ACTIVE);

-- 3) 역할별 메뉴 노출 권한 (ROLE + MENU_ID UNIQUE 이므로 중복 삽입은 무시된다)
--    실제 기준값 수정 등 관리자 권한은 공통코드 CCKB_PERM 로 판정한다. 03_perm_code.sql 참고.
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_ADMIN',   MENU_ID FROM core_menu WHERE MENU_CODE IN ('CCKB_MGMT', 'CCKB_INSPECT');
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_MANAGER', MENU_ID FROM core_menu WHERE MENU_CODE IN ('CCKB_MGMT', 'CCKB_INSPECT');
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_USER',    MENU_ID FROM core_menu WHERE MENU_CODE IN ('CCKB_MGMT', 'CCKB_INSPECT');

SELECT '--- 등록된 CCKB 메뉴 ---' AS '';
SELECT MENU_ID, MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, IS_VISIBLE, IS_ACTIVE
FROM core_menu WHERE MENU_CODE LIKE 'CCKB_%' ORDER BY SORT_ORDER, MENU_ID;
