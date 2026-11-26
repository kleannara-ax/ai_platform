-- ============================================================
--  module-cckb: CCKB 후면 색상검사 전용 역할(Role) 신설
--
--  배경: 지금까지 CCKB 메뉴(CCKB_MGMT)는 core_role_menu에
--        ROLE_ADMIN / ROLE_MANAGER / ROLE_USER 세 역할에 노출되어 있었다.
--        ROLE_USER는 신규 가입자 기본 역할이라 사실상 "전체 공개"와 다름없어서,
--        CCKB 업무 담당자만 접근하도록 전용 역할을 새로 만들고
--        ROLE_USER 매핑은 제거한다.
--
--  플랫폼 역할은 공통코드(code_detail, GROUP_CODE='ROLE')로 관리된다
--  (core/.../RoleProvider 구현체 CommonCodeRoleProvider 가 이 테이블만 읽는다).
--  sql/module-common/V7__role_to_common_code.sql, sql/module-steam-energy/06_roles.sql,
--  sql/module-fire/V8__add_fire_manager_role.sql 과 동일한 패턴을 그대로 따른다.
--
--  신규 역할:
--    CODE      = ROLE_CCKB
--    CODE_NAME = CCKB 후면 색상검사   (사용자 지시: 역할 이름을 이 문구로)
--
--  core_menu 는 지웠다 다시 넣지 않는다는 기존 원칙을 그대로 유지 —
--  이 파일은 core_menu 를 전혀 건드리지 않고 code_detail(역할 정의)과
--  core_role_menu(메뉴-역할 매핑)만 다룬다.
--
--  재실행 안전:
--    역할 등록은 GROUP_ID+CODE UNIQUE 기준 upsert.
--    SORT_ORDER 충돌을 피하기 위해 ROLE 그룹 내 현재 최대값+1 을 사용한다
--    (고정 숫자를 박아넣지 않음 — 운영 DB에 이미 들어있는 다른 역할들과 겹치지 않도록).
--    core_role_menu 매핑도 INSERT IGNORE / DELETE 조건절 기반이라 여러 번 실행해도 안전하다.
-- ============================================================

SET NAMES utf8mb4;

-- 1) ROLE 공통코드 그룹에 CCKB 전용 역할 추가 (없으면 추가, 있으면 이름/설명만 보정)
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT g.GROUP_ID,
       'ROLE_CCKB',
       'CCKB 후면 색상검사',
       'CCKB 후면 색상검사 모듈 전용 접근 권한',
       'cckb',
       1,
       COALESCE((SELECT MAX(d2.SORT_ORDER) FROM code_detail d2 WHERE d2.GROUP_ID = g.GROUP_ID), 0) + 1,
       NOW(), NOW()
FROM code_group g
WHERE g.GROUP_CODE = 'ROLE'
  AND NOT EXISTS (
      SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = 'ROLE_CCKB'
  );

UPDATE code_detail d
  JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
SET d.CODE_NAME = 'CCKB 후면 색상검사',
    d.DESCRIPTION = 'CCKB 후면 색상검사 모듈 전용 접근 권한',
    d.EXTRA_VALUE1 = 'cckb',
    d.IS_ACTIVE = 1,
    d.UPDATED_AT = NOW()
WHERE g.GROUP_CODE = 'ROLE' AND d.CODE = 'ROLE_CCKB';

-- 2) CCKB 메뉴(core_menu.MENU_CODE='CCKB_MGMT', 현재 메뉴 단일화로 URL을 직접 보유)
--    노출 역할을 '관리자(ROLE_ADMIN) / 매니저(ROLE_MANAGER) / CCKB 후면 색상검사(ROLE_CCKB)'
--    이 세 개만 남긴다. 기존 ROLE_USER 매핑은 제거한다.
DELETE FROM core_role_menu
WHERE ROLE = 'ROLE_USER'
  AND MENU_ID = (SELECT MENU_ID FROM (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'CCKB_MGMT') AS t);

INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID)
SELECT 'ROLE_ADMIN', MENU_ID FROM core_menu WHERE MENU_CODE = 'CCKB_MGMT';

INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID)
SELECT 'ROLE_MANAGER', MENU_ID FROM core_menu WHERE MENU_CODE = 'CCKB_MGMT';

INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID)
SELECT 'ROLE_CCKB', MENU_ID FROM core_menu WHERE MENU_CODE = 'CCKB_MGMT';

-- (참고) CCKB_INSPECT 는 04_single_page_menu.sql 에서 이미 IS_ACTIVE=0 / IS_VISIBLE=0 처리되었고
-- 그 역할 매핑도 이미 전부 삭제된 상태이므로 여기서 다시 다루지 않는다.

SELECT '--- ROLE_CCKB 등록 결과 ---' AS '';
SELECT d.CODE, d.CODE_NAME, d.SORT_ORDER, d.IS_ACTIVE
FROM code_detail d JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
WHERE g.GROUP_CODE = 'ROLE' ORDER BY d.SORT_ORDER;

SELECT '--- CCKB_MGMT 메뉴 노출 역할 (core_role_menu) ---' AS '';
SELECT rm.ROLE, m.MENU_CODE, m.MENU_NAME
FROM core_role_menu rm JOIN core_menu m ON m.MENU_ID = rm.MENU_ID
WHERE m.MENU_CODE = 'CCKB_MGMT' ORDER BY rm.ROLE;
