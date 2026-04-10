-- V8: 소방관리자(FIRE_MANAGER) 역할 추가
-- 소방시설관리 모듈에서 추가/수정/삭제 권한을 가지는 역할

-- 1) ROLE 공통코드 그룹에 ROLE_FIRE_MANAGER 추가
INSERT INTO core_code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT g.GROUP_ID, 'ROLE_FIRE_MANAGER', '소방관리자', '소방시설관리 모듈 관리 권한', 'fire_manager', TRUE, 4, NOW(), NOW()
FROM core_code_group g WHERE g.GROUP_CODE = 'ROLE'
ON DUPLICATE KEY UPDATE CODE_NAME = '소방관리자', DESCRIPTION = '소방시설관리 모듈 관리 권한';
