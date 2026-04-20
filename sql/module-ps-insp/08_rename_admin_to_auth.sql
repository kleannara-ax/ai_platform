-- ============================================================
-- module-ps-insp: PS_INSP_ADMIN → PS_INSP_AUTH 코드그룹 변경
-- GROUP_CODE: PS_INSP_ADMIN → PS_INSP_AUTH
-- GROUP_NAME: PS 지분검사 관리자 → PS 지분검사 권한
--
-- 기존에 배포된 운영 환경에서 실행 (07_admin_group.sql 이후)
-- ============================================================

-- 1) PS_INSP_ADMIN → PS_INSP_AUTH 로 변경
UPDATE code_group
SET GROUP_CODE   = 'PS_INSP_AUTH',
    GROUP_NAME   = 'PS 지분검사 권한',
    DESCRIPTION  = 'PS 후면 지분 검사 PPM 기준값 수정 권한자 관리',
    UPDATED_AT   = NOW()
WHERE GROUP_CODE = 'PS_INSP_ADMIN';

-- 2) 확인
SELECT GROUP_ID, GROUP_CODE, GROUP_NAME, DESCRIPTION
FROM code_group
WHERE GROUP_CODE = 'PS_INSP_AUTH';
