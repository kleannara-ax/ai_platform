-- ============================================================
--  module-common : 부서를 공통코드로 전환하는 마이그레이션
--  MariaDB 10.11+ (utf8mb4)
--
--  원본: V1.2.0__dept_to_common_code.sql
--
--  변경 내용:
--   1. user_profile.DEPT_ID → DEPT_CODE 로 전환
--   2. 기존 DEPT_ID FK/인덱스 제거 후 컬럼 삭제
--
--  주의: V4__code_data.sql 에서 DEPT 공통코드 그룹이 이미 생성되어 있어야 함
--        이 스크립트는 기존 DEPT_ID 기반 → DEPT_CODE 기반으로 전환하는 마이그레이션용
-- ============================================================

-- 1. user_profile 에 DEPT_CODE 컬럼 추가
ALTER TABLE user_profile ADD COLUMN DEPT_CODE VARCHAR(50) NULL COMMENT '부서 코드 (공통코드 DEPT 참조)' AFTER DEPT_ID;

-- 2. 기존 DEPT_ID 값을 DEPT_CODE 로 변환
UPDATE user_profile p
SET p.DEPT_CODE = (SELECT d.DEPT_CODE FROM mod_user_department d WHERE d.DEPT_ID = p.DEPT_ID)
WHERE p.DEPT_ID IS NOT NULL;

-- 3. FK 제약 제거
ALTER TABLE user_profile DROP FOREIGN KEY FK_USER_PROFILE_DEPT;

-- 4. DEPT_ID 인덱스 제거
ALTER TABLE user_profile DROP INDEX IDX_USER_PROFILE_DEPT;

-- 5. DEPT_ID 컬럼 삭제
ALTER TABLE user_profile DROP COLUMN DEPT_ID;
