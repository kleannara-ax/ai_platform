-- V8: 소방관리자(FIRE_MANAGER) 역할 추가
-- 소방시설관리 모듈에서 추가/수정/삭제 권한을 가지는 역할

-- ROLE 공통코드 그룹에 ROLE_FIRE_MANAGER 추가
-- 이미 존재하면 CODE_NAME, DESCRIPTION, EXTRA_VALUE1을 업데이트
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT g.GROUP_ID, 'ROLE_FIRE_MANAGER', '소방관리자', '소방시설관리 모듈 관리 권한', 'fire_manager', TRUE, 4, NOW(), NOW()
FROM code_group g WHERE g.GROUP_CODE = 'ROLE'
  AND NOT EXISTS (
    SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = 'ROLE_FIRE_MANAGER'
  );

-- 이미 존재하는 경우 이름/설명/부가값 보정
UPDATE code_detail d
  JOIN code_group g ON d.GROUP_ID = g.GROUP_ID
SET d.CODE_NAME = '소방관리자',
    d.DESCRIPTION = '소방시설관리 모듈 관리 권한',
    d.EXTRA_VALUE1 = 'fire_manager',
    d.UPDATED_AT = NOW()
WHERE g.GROUP_CODE = 'ROLE' AND d.CODE = 'ROLE_FIRE_MANAGER';
