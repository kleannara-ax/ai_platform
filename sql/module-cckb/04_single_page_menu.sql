-- ============================================================
--  module-cckb: CCKB 메뉴 단일 페이지화 (2단계 → 1단계)
--
--  배경: CCKB는 어차피 페이지가 1개뿐이라 "상위 그룹(CCKB_MGMT, URL 없음)
--        + 하위 페이지(CCKB_INSPECT, URL=/cckb/index.html)" 2단계 구조를
--        유지할 이유가 없다. 좌측 메뉴에서 펼침/접힘 없이 바로 클릭되는
--        단일 메뉴로 바꾼다.
--
--  방식: 02_menu.sql 의 코멘트에 명시된 원칙을 그대로 따른다 —
--        "core 메뉴를 지웠다 다시 넣지 않는다. MENU_ID 가 바뀌면
--         운영자가 손봐 둔 역할 매핑·정렬 순서가 함께 날아가기 때문이다."
--        따라서 CCKB_MGMT/CCKB_INSPECT 두 행 모두 삭제하지 않고,
--        CCKB_MGMT 에 URL/아이콘을 옮겨 붙이고 CCKB_INSPECT 는
--        비활성화(IS_ACTIVE=0)만 한다. (필요하면 언제든 되돌릴 수 있음)
--
--  프론트엔드 변경 불필요: app/src/main/resources/static/index.html 의
--  renderMenuNode() 는 menu.children.length 로 그룹/리프를 동적으로
--  판단하고, isCckbPage 판정도 menu.menuUrl 접두사(/cckb/)만 보므로
--  CCKB_MGMT 가 children 없이 MENU_URL 을 갖게 되면 자동으로 바로
--  클릭되는 단일 메뉴로 렌더링된다.
--
--  재실행 안전: 모두 UPDATE/DELETE 조건절 기반이라 여러 번 실행해도 안전하다.
-- ============================================================

SET NAMES utf8mb4;

-- 1) CCKB_MGMT(최상위 메뉴)에 페이지 URL/아이콘을 직접 부여 → 더 이상 "그룹"이 아니라
--    바로 클릭 가능한 단일 메뉴가 된다.
UPDATE core_menu
SET MENU_URL = '/cckb/index.html',
    ICON     = 'eye-dropper'
WHERE MENU_CODE = 'CCKB_MGMT';

-- 2) 이제 중복인 하위 페이지(CCKB_INSPECT)는 삭제하지 않고 비활성/비노출 처리만 한다.
UPDATE core_menu
SET IS_ACTIVE = 0,
    IS_VISIBLE = 0
WHERE MENU_CODE = 'CCKB_INSPECT';

-- 3) 비활성화된 CCKB_INSPECT 의 역할-메뉴 매핑은 정리한다.
--    (CCKB_MGMT 자체의 역할 매핑은 02_menu.sql 에서 이미 등록되어 그대로 유지된다.)
DELETE FROM core_role_menu
WHERE MENU_ID = (SELECT MENU_ID FROM (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'CCKB_INSPECT') AS t);

SELECT '--- CCKB 메뉴 단일화 결과 ---' AS '';
SELECT MENU_ID, MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, IS_VISIBLE, IS_ACTIVE
FROM core_menu WHERE MENU_CODE LIKE 'CCKB_%' ORDER BY SORT_ORDER, MENU_ID;
