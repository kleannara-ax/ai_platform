-- ============================================================
--  [module-epr] 플랫폼 메뉴 등록
--
--  EPR_MGMT (최상위 그룹, URL 없음)
--    └ EPR_REPORT  EPR 포장재 중량 신고  →  /epr/index.html
--
--  · 플랫폼 SPA(app/.../static/index.html)는 /epr/ 로 시작하는 메뉴를 epr:: 페이지로 만들어 iframe 으로 연다.
--  · EPR API(/epr-api/**)는 EPR_REPORT 메뉴가 열린 역할만 쓸 수 있다 (컨트롤러의 @PreAuthorize).
--    그래서 이 메뉴의 역할 매핑이 곧 EPR 사용 권한이다. 처음에는 ROLE_ADMIN 만 연다.
--    다른 역할은 플랫폼 "접근 권한" 화면에서 EPR 메뉴를 체크해 연다.
--
--  재실행 안전:
--    메뉴는 MENU_CODE UNIQUE 기준 upsert, 역할 매핑은 INSERT IGNORE.
--    메뉴를 지웠다 다시 넣지 않는다 (MENU_ID 가 바뀌면 운영자가 손본 역할 매핑이 사라진다).
--    SORT_ORDER 는 처음 넣을 때만 정하고 이후 덮어쓰지 않는다.
-- ============================================================

SET NAMES utf8mb4;

-- 1) 최상위 그룹
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
VALUES ('EPR_MGMT', 'EPR 포장재 신고', NULL, NULL, 'box', 'MENU', 90, 1, 1, NOW())
ON DUPLICATE KEY UPDATE
    MENU_NAME  = VALUES(MENU_NAME),
    MENU_URL   = VALUES(MENU_URL),
    ICON       = VALUES(ICON),
    MENU_TYPE  = VALUES(MENU_TYPE),
    IS_VISIBLE = VALUES(IS_VISIBLE),
    IS_ACTIVE  = VALUES(IS_ACTIVE);

-- 2) 화면 (판매수량 계산 · 제품 마스터 · 파트 코드 3개 탭이 이 화면 안에 있다)
--    같은 테이블을 참조하므로 파생 테이블로 한 번 감싼다 (MariaDB 제약).
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
VALUES ('EPR_REPORT', 'EPR 포장재 중량 신고',
        (SELECT MENU_ID FROM (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'EPR_MGMT') AS g),
        '/epr/index.html', 'scale', 'MENU', 1, 1, 1, NOW())
ON DUPLICATE KEY UPDATE
    MENU_NAME  = VALUES(MENU_NAME),
    PARENT_ID  = VALUES(PARENT_ID),
    MENU_URL   = VALUES(MENU_URL),
    ICON       = VALUES(ICON),
    MENU_TYPE  = VALUES(MENU_TYPE),
    IS_VISIBLE = VALUES(IS_VISIBLE),
    IS_ACTIVE  = VALUES(IS_ACTIVE);

-- 3) 역할별 메뉴 권한 — 처음에는 관리자만
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID)
SELECT 'ROLE_ADMIN', MENU_ID FROM core_menu WHERE MENU_CODE IN ('EPR_MGMT', 'EPR_REPORT');

SELECT MENU_ID, MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, IS_VISIBLE, IS_ACTIVE
  FROM core_menu WHERE MENU_CODE LIKE 'EPR_%' ORDER BY SORT_ORDER, MENU_ID;
