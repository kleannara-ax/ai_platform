-- ============================================================
-- module-ps-insp: PS 지분 검사 PPM 기준값 → 공통코드(code_group/code_detail) 등록
-- Database: MariaDB 10.11+ (utf8mb4)
--
-- 기존 ps_insp_config 전용 테이블 대신
-- 공통코드 테이블(code_group + code_detail)을 활용합니다.
--
-- code_group: PS_INSP_DEFAULT
--   ├─ PPM_LIMIT       : 후면 지분 값(PPM) 기준값 (extraValue1에 값 저장)
--   └─ PPM_ADMIN        : PPM 기준값 수정 권한자 ID (extraValue1에 콤마 구분 ID 목록)
-- ============================================================

-- ── 1) 코드 그룹 등록 ──
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
VALUES ('PS_INSP_DEFAULT', 'PS 지분검사 PPM 기준값', 'PS 후면 지분 검사 모듈 PPM 기준값 설정 (기준값, 권한자 관리)', 1, 100)
ON DUPLICATE KEY UPDATE GROUP_NAME = VALUES(GROUP_NAME), DESCRIPTION = VALUES(DESCRIPTION);

-- ── 2) 코드 상세 등록 ──
-- PPM_LIMIT: 후면 지분 값(PPM) 기준값. extraValue1 = 기준값 (0 = 비활성)
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, EXTRA_VALUE2, IS_ACTIVE, SORT_ORDER)
SELECT g.GROUP_ID, 'PPM_LIMIT', 'PPM 기준값', '후면 지분 값(PPM) 기준값. 0 = 비활성 (기준값 미설정)', '0', NULL, 1, 1
FROM code_group g WHERE g.GROUP_CODE = 'PS_INSP_DEFAULT'
ON DUPLICATE KEY UPDATE CODE_NAME = VALUES(CODE_NAME);

-- PPM_ADMIN: 기준값 수정 권한자 ID 목록. extraValue1 = 콤마 구분 ID
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, EXTRA_VALUE2, IS_ACTIVE, SORT_ORDER)
SELECT g.GROUP_ID, 'PPM_ADMIN', 'PPM 수정 권한자', 'PPM 기준값 수정 권한이 있는 사용자 ID 목록 (콤마 구분)', 'admin,ykcho,hsjeong,jwlee2,deyang', NULL, 1, 2
FROM code_group g WHERE g.GROUP_CODE = 'PS_INSP_DEFAULT'
ON DUPLICATE KEY UPDATE CODE_NAME = VALUES(CODE_NAME);

-- ── 3) 기존 비밀번호 코드 제거 (있으면) ──
DELETE d FROM code_detail d
JOIN code_group g ON d.GROUP_ID = g.GROUP_ID
WHERE g.GROUP_CODE = 'PS_INSP_DEFAULT' AND d.CODE = 'ADMIN_PWD_HASH';

-- ── 4) 기존 전용 테이블 제거 (있으면) ──
DROP TABLE IF EXISTS ps_insp_config;
