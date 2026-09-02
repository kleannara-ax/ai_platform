-- ============================================================
--  module-safety : SAFETY 관리자 권한을 공통코드로 관리
--
--  플랫폼 방식(KIMS_PERM 등)과 맞춰,
--  공통코드 그룹 'SAFETY_PERM' 에 등록된 로그인 ID 를 SAFETY 관리자로 본다.
--  (SafetyPermission 은 이 명단만 관리자로 인정한다. 플랫폼 ROLE_ADMIN 이라도
--   이 명단에 없으면 관리자가 아니다 — 관리 권한을 공통코드 한 곳에서만 관리하기 위함.)
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

-- 관리자 명단은 자동으로 채우지 않는다.
-- 공통코드 관리 화면에서 운영자가 직접 추가한다:
--   공통코드 관리 → 'SAFETY_PERM'(안전매뉴얼 관리자) → 코드 추가
--   CODE = 플랫폼 로그인 ID, 코드명 = 사람 이름

-- 확인용
-- SELECT d.CODE, d.CODE_NAME, d.IS_ACTIVE
--   FROM code_detail d JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
--  WHERE g.GROUP_CODE = 'SAFETY_PERM' ORDER BY d.SORT_ORDER;
