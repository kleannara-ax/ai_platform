-- ============================================================
-- V1.3.0 : 역할(Role)을 공통코드로 전환
-- 기존 Role enum (ROLE_ADMIN, ROLE_MANAGER, ROLE_USER) →
-- 공통코드 그룹 ROLE 에서 관리, 추가/수정 가능
-- ============================================================

-- 1) ROLE 공통코드 그룹 생성
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
VALUES ('ROLE', '사용자 역할', '시스템 사용자 역할 구분 (Spring Security Role)', TRUE, 0, NOW(), NOW());

SET @role_gid = LAST_INSERT_ID();

-- 2) 기존 역할 3종을 공통코드 상세로 등록
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
VALUES
  (@role_gid, 'ROLE_ADMIN',   '관리자',     '시스템 전체 관리 권한',      'admin',   TRUE, 1, NOW(), NOW()),
  (@role_gid, 'ROLE_MANAGER', '매니저',     '부서/팀 관리 권한',          'manager', TRUE, 2, NOW(), NOW()),
  (@role_gid, 'ROLE_USER',    '일반 사용자', '기본 사용자 권한',           'user',    TRUE, 3, NOW(), NOW());

-- 참고: 새 역할 추가 시 공통코드 관리 화면에서 ROLE 그룹에 코드 추가 후
--       접근권한 화면에서 해당 역할에 메뉴를 매핑하면 됩니다.
-- 예) ROLE_AUDITOR / 감사관 / 감사 전용 접근 권한
