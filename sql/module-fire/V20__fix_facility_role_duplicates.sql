SET NAMES utf8mb4;

-- ============================================================
-- V20: 설비관리시스템 역할 중복 표시 보정
--   - 운영 DB에서 접근 권한 화면에 '소방시설관리'가 2개 표시되는 상황 방지
--   - 기존 ROLE_FIRE_MANAGER(소방시설관리)는 유지
--   - 신규 ROLE_FACILITY_MANAGER(시설관리), ROLE_EQUIPMENT_MANAGER(기타시설관리)는 명확히 보정
--   - ROLE 그룹/ROLE 코드 중복 row가 있는 운영 DB도 canonical row로 정리
-- ============================================================

-- 1) ROLE 공통코드 그룹 canonical row 확보
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT 'ROLE', '사용자 역할', '시스템 사용자 역할 구분 (Spring Security Role)', TRUE, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_group WHERE GROUP_CODE = 'ROLE');

SET @role_group_id = (SELECT MIN(GROUP_ID) FROM code_group WHERE GROUP_CODE = 'ROLE');

-- 2) GROUP_CODE='ROLE' 그룹이 여러 개 있는 운영 DB 정리
--    RoleProvider는 code_group.GROUP_CODE='ROLE'인 active code_detail을 모두 읽으므로
--    duplicate ROLE 그룹은 이름을 바꿔 조회 대상에서 제외한다.
UPDATE code_detail d
JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
SET d.IS_ACTIVE = FALSE,
    d.DESCRIPTION = CONCAT(COALESCE(d.DESCRIPTION, ''), ' (V20 중복 ROLE 그룹 정리로 비활성화)'),
    d.UPDATED_AT = NOW()
WHERE @role_group_id IS NOT NULL
  AND g.GROUP_CODE = 'ROLE'
  AND d.GROUP_ID <> @role_group_id;

UPDATE code_group
SET GROUP_CODE = CONCAT('ROLE_DUPLICATE_', GROUP_ID),
    GROUP_NAME = CONCAT(COALESCE(GROUP_NAME, '사용자 역할'), ' (중복 정리)'),
    IS_ACTIVE = FALSE,
    UPDATED_AT = NOW()
WHERE @role_group_id IS NOT NULL
  AND GROUP_CODE = 'ROLE'
  AND GROUP_ID <> @role_group_id;

-- 3) 같은 ROLE 그룹 내 같은 CODE가 중복된 경우 가장 먼저 생성된 row만 유지
DELETE d
FROM code_detail d
JOIN code_detail keep_d
  ON keep_d.GROUP_ID = d.GROUP_ID
 AND keep_d.CODE = d.CODE
 AND keep_d.CODE_ID < d.CODE_ID
WHERE @role_group_id IS NOT NULL
  AND d.GROUP_ID = @role_group_id;

-- 4) 의도한 3개 역할을 보장: 기존 소방시설관리 유지 + 시설관리/기타시설관리 추가
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT @role_group_id, 'ROLE_FACILITY_MANAGER', '시설관리', '소방설비와 기타설비 전체 접근 권한', 'facility_manager', TRUE, 4, NOW(), NOW()
WHERE @role_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM code_detail WHERE GROUP_ID = @role_group_id AND CODE = 'ROLE_FACILITY_MANAGER');

INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT @role_group_id, 'ROLE_FIRE_MANAGER', '소방시설관리', '소방설비 전용 접근 권한', 'fire_manager', TRUE, 5, NOW(), NOW()
WHERE @role_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM code_detail WHERE GROUP_ID = @role_group_id AND CODE = 'ROLE_FIRE_MANAGER');

INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT @role_group_id, 'ROLE_EQUIPMENT_MANAGER', '기타시설관리', '기타설비 전용 접근 권한', 'equipment_manager', TRUE, 6, NOW(), NOW()
WHERE @role_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM code_detail WHERE GROUP_ID = @role_group_id AND CODE = 'ROLE_EQUIPMENT_MANAGER');

-- 5) 주요 기본/설비 역할명을 명확히 재보정
UPDATE code_detail
SET CODE_NAME = CASE CODE
        WHEN 'ROLE_ADMIN' THEN '관리자'
        WHEN 'ROLE_MANAGER' THEN 'PS 지분 검사 매니저'
        WHEN 'ROLE_USER' THEN '일반 사용자'
        WHEN 'ROLE_FACILITY_MANAGER' THEN '시설관리'
        WHEN 'ROLE_FIRE_MANAGER' THEN '소방시설관리'
        WHEN 'ROLE_EQUIPMENT_MANAGER' THEN '기타시설관리'
        ELSE CODE_NAME
    END,
    DESCRIPTION = CASE CODE
        WHEN 'ROLE_ADMIN' THEN '시스템 전체 관리 권한'
        WHEN 'ROLE_MANAGER' THEN 'PS 지분 검사 관리 권한'
        WHEN 'ROLE_USER' THEN '기본 사용자 권한'
        WHEN 'ROLE_FACILITY_MANAGER' THEN '소방설비와 기타설비 전체 접근 권한'
        WHEN 'ROLE_FIRE_MANAGER' THEN '소방설비 전용 접근 권한'
        WHEN 'ROLE_EQUIPMENT_MANAGER' THEN '기타설비 전용 접근 권한'
        ELSE DESCRIPTION
    END,
    EXTRA_VALUE1 = CASE CODE
        WHEN 'ROLE_ADMIN' THEN 'admin'
        WHEN 'ROLE_MANAGER' THEN 'manager'
        WHEN 'ROLE_USER' THEN 'user'
        WHEN 'ROLE_FACILITY_MANAGER' THEN 'facility_manager'
        WHEN 'ROLE_FIRE_MANAGER' THEN 'fire_manager'
        WHEN 'ROLE_EQUIPMENT_MANAGER' THEN 'equipment_manager'
        ELSE EXTRA_VALUE1
    END,
    IS_ACTIVE = TRUE,
    SORT_ORDER = CASE CODE
        WHEN 'ROLE_ADMIN' THEN 1
        WHEN 'ROLE_MANAGER' THEN 2
        WHEN 'ROLE_USER' THEN 3
        WHEN 'ROLE_FACILITY_MANAGER' THEN 4
        WHEN 'ROLE_FIRE_MANAGER' THEN 5
        WHEN 'ROLE_EQUIPMENT_MANAGER' THEN 6
        ELSE SORT_ORDER
    END,
    UPDATED_AT = NOW()
WHERE @role_group_id IS NOT NULL
  AND GROUP_ID = @role_group_id
  AND CODE IN ('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER', 'ROLE_FACILITY_MANAGER', 'ROLE_FIRE_MANAGER', 'ROLE_EQUIPMENT_MANAGER');

-- 6) 그래도 남아있는 비표준 '소방시설관리' 표시 역할은 접근 권한 화면에서 숨김
--    ROLE_FIRE_MANAGER만 '소방시설관리' 이름을 사용해야 한다.
UPDATE code_detail
SET IS_ACTIVE = FALSE,
    DESCRIPTION = CONCAT(COALESCE(DESCRIPTION, ''), ' (V20 중복 표시 정리로 비활성화)'),
    UPDATED_AT = NOW()
WHERE @role_group_id IS NOT NULL
  AND GROUP_ID = @role_group_id
  AND IS_ACTIVE = TRUE
  AND CODE_NAME = '소방시설관리'
  AND CODE <> 'ROLE_FIRE_MANAGER';

-- 7) 재발 방지용 유니크 인덱스 보정
--    이미 존재하는 환경에서는 IF NOT EXISTS로 건너뜀.
CREATE UNIQUE INDEX IF NOT EXISTS UK_CODE_GROUP_CODE ON code_group (GROUP_CODE);
CREATE UNIQUE INDEX IF NOT EXISTS UK_CODE_DETAIL ON code_detail (GROUP_ID, CODE);

-- 8) 확인용 조회
-- SELECT d.CODE_ID, d.CODE, d.CODE_NAME, d.DESCRIPTION, d.EXTRA_VALUE1, d.IS_ACTIVE, d.SORT_ORDER
-- FROM code_detail d
-- JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
-- WHERE g.GROUP_CODE = 'ROLE'
-- ORDER BY d.SORT_ORDER, d.CODE_ID;
