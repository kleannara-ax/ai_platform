-- ============================================================
--  module-kims : KIMS 관리자 권한을 공통코드로 관리
--
--  기존에는 kims_user.ROLE(ADMIN/STAFF/USER) 로 KIMS 안에서 관리자 여부를 정했다.
--  플랫폼 방식(소방 FIRE_PERM, PS점검 PS_INSP_AUTH)과 맞춰,
--  공통코드 그룹 'KIMS_PERM' 에 등록된 로그인 ID 를 KIMS 관리자로 본다.
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

-- 2) 초기 관리자 (a) 기존 KIMS 를 옮겨온 경우 — kims_user 의 ADMIN 계정
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
SELECT g.GROUP_ID, u.USERNAME, COALESCE(NULLIF(u.NAME, ''), u.USERNAME), 'kims_user 에서 이관된 관리자', 1,
       ROW_NUMBER() OVER (ORDER BY u.USER_ID)
FROM code_group g
JOIN kims_user u ON u.ROLE = 'ADMIN' AND u.ENABLED = 1
WHERE g.GROUP_CODE = 'KIMS_PERM'
  AND NOT EXISTS (
      SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = u.USERNAME
  );

-- 3) 초기 관리자 (b) 신규 설치 등으로 위에서 한 명도 안 들어간 경우 — 플랫폼 ROLE_ADMIN 계정으로 채운다.
--    이 목록이 비면 KIMS 관리자 기능 전체가 잠기므로(판정 기준이 이 명단뿐) 안전장치로 둔다.
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
SELECT g.GROUP_ID, u.LOGIN_ID, COALESCE(NULLIF(u.USER_NAME, ''), u.LOGIN_ID), '플랫폼 관리자 계정에서 자동 등록', 1,
       100 + ROW_NUMBER() OVER (ORDER BY u.USER_ID)
FROM code_group g
JOIN core_user u ON u.ROLE = 'ROLE_ADMIN' AND u.ENABLED = 1
WHERE g.GROUP_CODE = 'KIMS_PERM'
  AND NOT EXISTS (SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.IS_ACTIVE = 1)
  AND NOT EXISTS (SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = u.LOGIN_ID);

-- 4) 확인용
-- SELECT d.CODE, d.CODE_NAME, d.IS_ACTIVE
--   FROM code_detail d JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
--  WHERE g.GROUP_CODE = 'KIMS_PERM' ORDER BY d.SORT_ORDER;
