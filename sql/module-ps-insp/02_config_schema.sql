-- ============================================================
-- module-ps-insp: PS 지분 검사 공통코드 등록
-- Database: MariaDB 10.11+ (utf8mb4)
--
-- 공통코드 테이블(code_group + code_detail)을 활용합니다.
--
-- code_group: PS_INSP_DEFAULT
--   └─ PPM_LIMIT       : 후면 지분 값(PPM) 기준값 (extraValue1에 값 저장)
--
-- code_group: PS_INSP_AUTH
--   └─ 사용자별 로그인 ID 코드 : PPM 기준값 수정 권한자 (V12에서 사용자별 코드 방식으로 전환)
-- ============================================================

-- ── 1) PS_INSP_DEFAULT 코드 그룹 등록 ──
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
VALUES ('PS_INSP_DEFAULT', 'PS 지분검사 PPM 기준값', 'PS 후면 지분 검사 모듈 PPM 기준값 설정', 1, 100)
ON DUPLICATE KEY UPDATE GROUP_NAME = VALUES(GROUP_NAME), DESCRIPTION = VALUES(DESCRIPTION);

-- PPM_LIMIT: 후면 지분 값(PPM) 기준값. extraValue1 = 기준값 (0 = 비활성)
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, EXTRA_VALUE2, IS_ACTIVE, SORT_ORDER)
SELECT g.GROUP_ID, 'PPM_LIMIT', 'PPM 기준값', '후면 지분 값(PPM) 기준값. 0 = 비활성 (기준값 미설정)', '0', NULL, 1, 1
FROM code_group g WHERE g.GROUP_CODE = 'PS_INSP_DEFAULT'
ON DUPLICATE KEY UPDATE CODE_NAME = VALUES(CODE_NAME);

-- ── 2) PS_INSP_AUTH 코드 그룹 등록 (PPM 기준값 수정 권한자) ──
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
VALUES ('PS_INSP_AUTH', 'PS 지분검사 권한', 'PS 후면 지분 검사 PPM 기준값 수정 권한자 관리', 1, 101)
ON DUPLICATE KEY UPDATE GROUP_NAME = VALUES(GROUP_NAME), DESCRIPTION = VALUES(DESCRIPTION);

-- 레거시 PPM_ADMIN: V12에서 사용자별 로그인 ID 코드로 분리 전환
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, EXTRA_VALUE2, IS_ACTIVE, SORT_ORDER)
SELECT g.GROUP_ID, 'PPM_ADMIN', 'PPM 기준값 수정 권한자', '레거시 권한자 목록 - V12에서 사용자별 코드값으로 전환', 'admin,ykcho,hsjeong,jwlee2,deyang', NULL, 1, 1
FROM code_group g WHERE g.GROUP_CODE = 'PS_INSP_AUTH'
ON DUPLICATE KEY UPDATE CODE_NAME = VALUES(CODE_NAME);

-- ── 3) 기존 레거시 정리 ──
DROP TABLE IF EXISTS ps_insp_config;
