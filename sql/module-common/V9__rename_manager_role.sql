-- ============================================================
-- V9: ROLE_MANAGER 역할명 변경
--     매니저 → PS 지분 검사 매니저
--     이미 배포된 환경에서 실행
-- ============================================================

UPDATE code_detail
SET CODE_NAME   = 'PS 지분 검사 매니저',
    DESCRIPTION = 'PS 지분 검사 관리 권한',
    UPDATED_AT  = NOW()
WHERE CODE = 'ROLE_MANAGER'
  AND GROUP_ID IN (SELECT GROUP_ID FROM code_group WHERE GROUP_CODE = 'ROLE');
