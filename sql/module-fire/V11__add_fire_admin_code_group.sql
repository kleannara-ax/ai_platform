SET NAMES utf8mb4;

-- ============================================================
-- V11: 소방시설관리 권한 코드그룹 (FIRE_PERM) + 소방시설관리자 (FIRE_ADMIN) 추가
--   PPM_ADMIN(PS_INSP_AUTH) 패턴 참고
--   EXTRA_VALUE1에 콤마 구분 아이디 목록 저장
-- ============================================================

-- 1) FIRE_PERM 코드 그룹 생성
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
VALUES ('FIRE_PERM', '소방시설관리 권한', '소방시설관리 모듈 추가/수정/삭제/점검 권한자 관리', 1, 110)
ON DUPLICATE KEY UPDATE GROUP_NAME = VALUES(GROUP_NAME), DESCRIPTION = VALUES(DESCRIPTION);

-- 2) FIRE_ADMIN 코드 등록 (콤마 구분 ID 목록)
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER)
SELECT g.GROUP_ID, 'FIRE_ADMIN', '소방시설관리자', '소화기/소화전/수신기/소방펌프 추가·삭제·점검 권한이 있는 사용자 ID 목록 (콤마 구분)', 'admin', 1, 1
FROM code_group g WHERE g.GROUP_CODE = 'FIRE_PERM'
ON DUPLICATE KEY UPDATE CODE_NAME = VALUES(CODE_NAME), DESCRIPTION = VALUES(DESCRIPTION);
