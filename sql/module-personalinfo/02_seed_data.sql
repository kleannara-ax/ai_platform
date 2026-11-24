-- ============================================================
--  module-personalinfo : 개인정보 위수탁 협력사 관리 - 초기 데이터 (운영자 검토용)
--  MariaDB 10.11+ (utf8mb4)
--
--  ※ 이 스크립트는 업무 테이블을 만들지 않는다. (01_schema.sql 먼저 실행)
--  ※ core 메뉴/역할 테이블 구조는 저장소의 아래 DDL 로 확인한 뒤 작성했다.
--      - core_menu       : sql/V1.0.0__init_schema.sql (MENU_CODE UNIQUE)
--      - core_role_menu  : sql/V1.0.0__init_schema.sql (ROLE + MENU_ID UNIQUE)
--      - code_group/code_detail(ROLE 그룹) : sql/module-common/V3__code_schema.sql, V7__role_to_common_code.sql
--    운영 DB 구조가 다르면 실행하지 말고 README 의 "메뉴 등록 요청 정보" 표로 화면에서 등록한다.
--
--  재실행 안전:
--    - 메뉴     : MENU_CODE UNIQUE 기준 ON DUPLICATE KEY UPDATE (MENU_ID 유지, SORT_ORDER 는 최초 1회만)
--    - 역할코드 : NOT EXISTS 조건 INSERT (이미 있으면 건너뜀)
--    - 역할매핑 : INSERT IGNORE
--    DROP / TRUNCATE / DELETE 없음. 업무 데이터(계약 등)는 넣지 않는다.
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1) 역할 코드 (공통코드 ROLE 그룹) — 사용자 관리 화면에서 사용자에게 부여
--    ROLE_PI_ADMIN  : 개인정보 위수탁 관리자 (보안팀)  - 전체 기능 + 삭제
--    ROLE_PI_USER   : 개인정보 위수탁 일반 사용자 (계약 담당자) - 등록/수정/조회
--    ROLE_PI_VIEWER : 개인정보 위수탁 조회 사용자 - 조회만
--    (플랫폼 ROLE_ADMIN 은 별도 등록 없이 관리자 권한을 가진다)
-- ------------------------------------------------------------
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT g.GROUP_ID, 'ROLE_PI_ADMIN', '개인정보위수탁 관리자', '개인정보 위수탁 협력사 관리 - 전체 기능(삭제 포함)', 'pi_admin', 1, 41, NOW(), NOW()
  FROM code_group g
 WHERE g.GROUP_CODE = 'ROLE'
   AND NOT EXISTS (SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = 'ROLE_PI_ADMIN');

INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT g.GROUP_ID, 'ROLE_PI_USER', '개인정보위수탁 사용자', '개인정보 위수탁 협력사 관리 - 등록/수정/조회', 'pi_user', 1, 42, NOW(), NOW()
  FROM code_group g
 WHERE g.GROUP_CODE = 'ROLE'
   AND NOT EXISTS (SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = 'ROLE_PI_USER');

INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT g.GROUP_ID, 'ROLE_PI_VIEWER', '개인정보위수탁 조회자', '개인정보 위수탁 협력사 관리 - 조회 전용', 'pi_viewer', 1, 43, NOW(), NOW()
  FROM code_group g
 WHERE g.GROUP_CODE = 'ROLE'
   AND NOT EXISTS (SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = 'ROLE_PI_VIEWER');

-- ------------------------------------------------------------
-- 2) 메뉴 (보안관리 > 개인정보 위수탁 관리)
-- ------------------------------------------------------------
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, DESCRIPTION, CREATED_AT)
VALUES ('PI_MGMT', '보안관리', NULL, NULL, 'user-shield', 'MENU', 80, 1, 1, '개인정보 위수탁 협력사 관리 상위 메뉴', NOW())
ON DUPLICATE KEY UPDATE
    MENU_NAME   = VALUES(MENU_NAME),
    ICON        = VALUES(ICON),
    MENU_TYPE   = VALUES(MENU_TYPE),
    DESCRIPTION = VALUES(DESCRIPTION);

-- 같은 테이블을 참조하므로 파생 테이블로 한 번 감싼다 (MariaDB 제약)
INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, DESCRIPTION, CREATED_AT)
VALUES ('PI_CONTRACT', '개인정보 위수탁 관리',
        (SELECT MENU_ID FROM (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'PI_MGMT') AS g),
        '/personalinfo/index.html', 'file-contract', 'MENU', 1, 1, 1,
        '위수탁 계약서 등록/조회, 만료 알림, 점검 체크리스트', NOW())
ON DUPLICATE KEY UPDATE
    MENU_NAME   = VALUES(MENU_NAME),
    PARENT_ID   = VALUES(PARENT_ID),
    MENU_URL    = VALUES(MENU_URL),
    ICON        = VALUES(ICON),
    MENU_TYPE   = VALUES(MENU_TYPE),
    DESCRIPTION = VALUES(DESCRIPTION);

-- ------------------------------------------------------------
-- 3) 역할별 메뉴 노출 (ROLE + MENU_ID UNIQUE → 중복은 무시)
--    실제 API 권한은 각 API 의 @PreAuthorize 가 역할 코드로 판정한다.
-- ------------------------------------------------------------
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_ADMIN',     MENU_ID FROM core_menu WHERE MENU_CODE IN ('PI_MGMT', 'PI_CONTRACT');
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_PI_ADMIN',  MENU_ID FROM core_menu WHERE MENU_CODE IN ('PI_MGMT', 'PI_CONTRACT');
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_PI_USER',   MENU_ID FROM core_menu WHERE MENU_CODE IN ('PI_MGMT', 'PI_CONTRACT');
INSERT IGNORE INTO core_role_menu (ROLE, MENU_ID) SELECT 'ROLE_PI_VIEWER', MENU_ID FROM core_menu WHERE MENU_CODE IN ('PI_MGMT', 'PI_CONTRACT');

-- ------------------------------------------------------------
-- 확인용
-- ------------------------------------------------------------
SELECT MENU_ID, MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, IS_VISIBLE, IS_ACTIVE
  FROM core_menu WHERE MENU_CODE IN ('PI_MGMT', 'PI_CONTRACT') ORDER BY MENU_ID;
SELECT d.CODE, d.CODE_NAME, d.IS_ACTIVE
  FROM code_detail d JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
 WHERE g.GROUP_CODE = 'ROLE' AND d.CODE LIKE 'ROLE_PI_%' ORDER BY d.SORT_ORDER;
