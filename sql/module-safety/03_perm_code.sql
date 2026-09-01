-- ============================================================
--  module-safety : SAFETY 관리자 권한을 공통코드로 관리
--
--  플랫폼 방식(KIMS_PERM 등)과 맞춰,
--  공통코드 그룹 'SAFETY_PERM' 에 등록된 로그인 ID 를 SAFETY 관리자로 본다.
--  (SafetyPermission 은 이 명단 + 플랫폼 ROLE_ADMIN 을 모두 관리자로 인정한다.)
--
--  운영 방법: 공통코드 관리 화면 → SAFETY 관리자 그룹 → 코드 추가
--             CODE = 플랫폼 로그인 ID, 코드명 = 사람 이름
--  재실행 안전.
-- ============================================================

SET NAMES utf8mb4;

-- 1) 코드 그룹
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
VALUES ('SAFETY_PERM', '안전매뉴얼 관리자', '안전작업방식 매뉴얼(SAFETY) 관리자 권한을 가진 로그인 ID 목록', 1, 130)
ON DUPLICATE KEY UPDATE GROUP_NAME = VALUES(GROUP_NAME), DESCRIPTION = VALUES(DESCRIPTION), IS_ACTIVE = VALUES(IS_ACTIVE);

-- 2) 초기 관리자 — 플랫폼 ROLE_ADMIN 계정으로 채운다.
--    (SafetyPermission 이 ROLE_ADMIN 을 항상 관리자로 인정하므로 이 목록이 비어도 잠기지 않지만,
--     공통코드 관리 화면에서 바로 보이도록 초기값을 채워둔다.)
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
SELECT g.GROUP_ID, u.LOGIN_ID, COALESCE(NULLIF(u.USER_NAME, ''), u.LOGIN_ID), '플랫폼 관리자 계정에서 자동 등록', 1,
       100 + ROW_NUMBER() OVER (ORDER BY u.USER_ID)
FROM code_group g
JOIN core_user u ON u.ROLE = 'ROLE_ADMIN' AND u.ENABLED = 1
WHERE g.GROUP_CODE = 'SAFETY_PERM'
  AND NOT EXISTS (SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.IS_ACTIVE = 1)
  AND NOT EXISTS (SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = u.LOGIN_ID);

-- 3) 확인용
-- SELECT d.CODE, d.CODE_NAME, d.IS_ACTIVE
--   FROM code_detail d JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
--  WHERE g.GROUP_CODE = 'SAFETY_PERM' ORDER BY d.SORT_ORDER;
