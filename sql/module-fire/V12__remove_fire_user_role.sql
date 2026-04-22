SET NAMES utf8mb4;

-- ============================================================
-- V12: ROLE_FIRE_USER(소방시설사용자) 역할 제거
--   FIRE_ADMIN 아이디 기반 권한 체계로 전환하면서 불필요해진 역할 정리
--   - core_role_menu 메뉴 매핑 삭제
--   - code_detail 역할 코드 삭제
--   - 해당 역할이 할당된 사용자가 있으면 ROLE_USER로 변경
-- ============================================================

-- 1) ROLE_FIRE_USER 메뉴 매핑 삭제
DELETE FROM core_role_menu WHERE ROLE = 'ROLE_FIRE_USER';

-- 2) ROLE_FIRE_USER가 할당된 사용자를 ROLE_USER로 변경 (데이터 유실 방지)
UPDATE core_user SET ROLE = 'ROLE_USER', UPDATED_AT = NOW()
WHERE ROLE = 'ROLE_FIRE_USER';

-- 3) ROLE 코드그룹에서 ROLE_FIRE_USER 삭제
DELETE d FROM code_detail d
  JOIN code_group g ON d.GROUP_ID = g.GROUP_ID
WHERE g.GROUP_CODE = 'ROLE' AND d.CODE = 'ROLE_FIRE_USER';
