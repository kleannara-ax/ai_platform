SET NAMES utf8mb4;

-- ============================================================
-- V32: 기타시설관리 권한 코드그룹 (OTHER_PERM) + 레거시 관리자 코드(OTHER_ADMIN) 추가
--   V38에서 OTHER_ADMIN.EXTRA_VALUE1 목록을 사용자별 CODE 방식으로 전환
-- ============================================================

-- 1) OTHER_PERM 코드 그룹 생성
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
VALUES ('OTHER_PERM', '기타시설관리 권한', '기타시설관리 모듈 추가/수정/삭제/이동/QR 확인 권한자 관리', 1, 111)
ON DUPLICATE KEY UPDATE GROUP_NAME = VALUES(GROUP_NAME), DESCRIPTION = VALUES(DESCRIPTION);

-- 2) 레거시 OTHER_ADMIN 코드 등록 (V38 전환 원본)
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER)
SELECT g.GROUP_ID, 'OTHER_ADMIN', '기타시설관리자', '레거시 권한자 ID 목록 - V38에서 사용자별 CODE 방식으로 전환', 'admin', 1, 1
FROM code_group g WHERE g.GROUP_CODE = 'OTHER_PERM'
ON DUPLICATE KEY UPDATE CODE_NAME = VALUES(CODE_NAME), DESCRIPTION = VALUES(DESCRIPTION);
