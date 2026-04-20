-- ============================================================
-- module-ps-insp: 공통코드 그룹 코드 변경 마이그레이션
-- PS_INSP_CONFIG → PS_INSP_PPM_LIMIT
--
-- 기존에 배포된 운영 환경에서 실행 (02_config_schema.sql 이후)
-- ============================================================

-- code_group: GROUP_CODE, GROUP_NAME 변경
UPDATE code_group
SET GROUP_CODE  = 'PS_INSP_PPM_LIMIT',
    GROUP_NAME  = 'PS 지분검사 PPM 기준값',
    DESCRIPTION = 'PS 후면 지분 검사 모듈 PPM 기준값 설정 (기준값, 권한자 관리)',
    UPDATED_AT  = NOW()
WHERE GROUP_CODE = 'PS_INSP_CONFIG';
