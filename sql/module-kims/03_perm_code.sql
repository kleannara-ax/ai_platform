-- ============================================================
--  module-kims : KIMS 관리자 권한을 공통코드로 관리
--
--  플랫폼 방식(소방 FIRE_PERM, PS점검 PS_INSP_AUTH)과 맞춰,
--  공통코드 그룹 'KIMS_PERM' 에 등록된 로그인 ID 를 KIMS 관리자로 본다.
--  (KIMS는 자체 사용자 테이블을 두지 않으며, 플랫폼 core_user 계정만 사용한다.)
--
--  운영 방법: 공통코드 관리 화면 → KIMS 관리자 그룹 → 코드 추가
--             CODE = 플랫폼 로그인 ID, 코드명 = 사람 이름
--  재실행 안전.
-- ============================================================

SET NAMES utf8mb4;

-- 1) 코드 그룹
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
VALUES ('KIMS_PERM', 'KIMS 관리자', 'IT 운영 관리(KIMS) 관리자 권한을 가진 로그인 ID 목록', 1, 120)
ON DUPLICATE KEY UPDATE GROUP_NAME = VALUES(GROUP_NAME), DESCRIPTION = VALUES(DESCRIPTION), IS_ACTIVE = VALUES(IS_ACTIVE);

-- 2) 초기 관리자 — 플랫폼 ROLE_ADMIN 계정으로 채운다.
--    KIMS 자체 사용자 테이블이 없으므로 core_user 만을 기준으로 한다.
--    이 목록이 비면 KIMS 관리자 기능 전체가 잠기므로(판정 기준이 이 명단뿐) 안전장치로 둔다.
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
SELECT g.GROUP_ID, u.LOGIN_ID, COALESCE(NULLIF(u.USER_NAME, ''), u.LOGIN_ID), '플랫폼 관리자 계정에서 자동 등록', 1,
       100 + ROW_NUMBER() OVER (ORDER BY u.USER_ID)
FROM code_group g
JOIN core_user u ON u.ROLE = 'ROLE_ADMIN' AND u.ENABLED = 1
WHERE g.GROUP_CODE = 'KIMS_PERM'
  AND NOT EXISTS (SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.IS_ACTIVE = 1)
  AND NOT EXISTS (SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = u.LOGIN_ID);

-- 3) 확인용
-- SELECT d.CODE, d.CODE_NAME, d.IS_ACTIVE
--   FROM code_detail d JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
--  WHERE g.GROUP_CODE = 'KIMS_PERM' ORDER BY d.SORT_ORDER;
