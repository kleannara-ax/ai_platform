-- ============================================================
--  module-cckb : CCKB 관리자 권한을 공통코드로 관리
--
--  플랫폼 방식(SAFETY_PERM 등)과 맞춰,
--  공통코드 그룹 'CCKB_PERM' 에 등록된 로그인 ID 를 CCKB 관리자로 본다.
--  (CckbPermission 은 이 명단만 관리자로 인정한다. 플랫폼 ROLE_ADMIN 이라도
--   이 명단에 없으면 "⚙ 기준값 설정" 탭의 수정 권한이 없다 — 관리 권한을
--   공통코드 한 곳에서만 관리하기 위함.)
--
--  운영 방법: 공통코드 관리 화면 → CCKB 관리자 그룹 → 코드 추가
--             CODE = 플랫폼 로그인 ID, 코드명 = 사람 이름
--  재실행 안전.
-- ============================================================

SET NAMES utf8mb4;

-- 1) 코드 그룹
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
VALUES ('CCKB_PERM', 'CCKB 색상검사 관리자', 'CCKB 후면 색상검사(CCKB) 기준값 설정 관리자 권한을 가진 로그인 ID 목록', 1, 140)
ON DUPLICATE KEY UPDATE GROUP_NAME = VALUES(GROUP_NAME), DESCRIPTION = VALUES(DESCRIPTION), IS_ACTIVE = VALUES(IS_ACTIVE);

-- 2) 판정 코드 기본값 시드 (JudgmentCodeService.ensureDefaults() 가 애플리케이션 기동 시에도
--    자동 보정하지만, SQL 로 먼저 넣어두면 운영 DB 적용 즉시 화면이 정상 동작한다)
INSERT INTO judgment_code (JUDGMENT_CODE, JUDGMENT_NAME_KR, COLOR_HEX, SORT_ORDER, DESCRIPTION, CREATED_AT)
VALUES
  ('NORMAL',   '정상', '#22c55e', 1, '모든 값이 정상 기준 이내', NOW()),
  ('BOUNDARY', '경계', '#f59e0b', 2, '정상 기준은 벗어났으나 불량 기준 미만', NOW()),
  ('DEFECT',   '불량', '#ef4444', 3, '불량 기준을 벗어남', NOW())
ON DUPLICATE KEY UPDATE
  JUDGMENT_NAME_KR = VALUES(JUDGMENT_NAME_KR),
  COLOR_HEX        = VALUES(COLOR_HEX),
  SORT_ORDER       = VALUES(SORT_ORDER),
  DESCRIPTION      = VALUES(DESCRIPTION);

-- 3) 기본 판정 기준값 1건 시드 (원본 V4 HTML 의 DEFAULT_REF_LAB/DEFAULT_THRESHOLDS 그대로)
--    InspectionStandardService.createDefaultStandard() 가 애플리케이션 기동 시에도 자동
--    생성하지만, SQL 로 먼저 넣어두면 운영 DB 적용 즉시 "검사" 탭이 정상 동작한다.
INSERT INTO inspection_standard
  (STANDARD_VERSION, REF_L, REF_A, REF_B,
   NORMAL_DE00_MAX, NORMAL_DL_MAX, NORMAL_DA_MAX, NORMAL_DB_MAX,
   DEFECT_DE00_MIN, DEFECT_DL_MAX, DEFECT_DA_MAX, DEFECT_DB_MAX,
   EFFECTIVE_FROM, IS_ACTIVE, REMARKS, CREATED_AT)
SELECT 'v1.0', 59.2, 9.0, 21.3,
       2.0, 2.0, 2.0, 2.0,
       4.0, 2.0, 2.0, 2.0,
       CURDATE(), 'Y', '플랫폼 이관 초기 기준값 (원본 CCKB V4 HTML 기본값)', NOW()
WHERE NOT EXISTS (SELECT 1 FROM inspection_standard WHERE IS_ACTIVE = 'Y' AND DELETED_YN = 'N');

-- 관리자 명단은 자동으로 채우지 않는다.
-- 공통코드 관리 화면에서 운영자가 직접 추가한다:
--   공통코드 관리 → 'CCKB_PERM'(CCKB 색상검사 관리자) → 코드 추가
--   CODE = 플랫폼 로그인 ID, 코드명 = 사람 이름

-- 확인용
-- SELECT d.CODE, d.CODE_NAME, d.IS_ACTIVE
--   FROM code_detail d JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
--  WHERE g.GROUP_CODE = 'CCKB_PERM' ORDER BY d.SORT_ORDER;
