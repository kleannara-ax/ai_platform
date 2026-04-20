-- ============================================================
-- module-ps-insp: 권한자 관리를 PS_INSP_ADMIN 코드그룹으로 분리
-- 기존: PS_INSP_DEFAULT(또는 PS_INSP_CONFIG) > PPM_ADMIN
-- 변경: PS_INSP_ADMIN > PPM_ADMIN (extraValue1에 콤마 구분 ID)
--
-- 기존에 배포된 운영 환경에서 실행 (02_config_schema.sql 이후)
-- ============================================================

-- 1) PS_INSP_ADMIN 코드 그룹 생성
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
VALUES ('PS_INSP_ADMIN', 'PS 지분검사 관리자', 'PS 후면 지분 검사 PPM 기준값 수정 권한자 관리', 1, 101)
ON DUPLICATE KEY UPDATE GROUP_NAME = VALUES(GROUP_NAME), DESCRIPTION = VALUES(DESCRIPTION);

-- 2) PPM_ADMIN 코드 등록 (콤마 구분 ID 목록)
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER)
SELECT g.GROUP_ID, 'PPM_ADMIN', 'PPM 기준값 수정 권한자', 'PPM 기준값 수정 권한이 있는 사용자 ID 목록 (콤마 구분)', 'admin,ykcho,hsjeong,jwlee2,deyang', 1, 1
FROM code_group g WHERE g.GROUP_CODE = 'PS_INSP_ADMIN'
ON DUPLICATE KEY UPDATE CODE_NAME = VALUES(CODE_NAME);

-- 3) 기존 그룹에 있던 PPM_ADMIN 제거
DELETE d FROM code_detail d
JOIN code_group g ON d.GROUP_ID = g.GROUP_ID
WHERE g.GROUP_CODE IN ('PS_INSP_DEFAULT', 'PS_INSP_CONFIG', 'PS_INSP_PPM_LIMIT')
  AND d.CODE = 'PPM_ADMIN';
