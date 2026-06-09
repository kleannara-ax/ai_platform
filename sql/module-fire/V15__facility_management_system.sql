SET NAMES utf8mb4;

-- ============================================================
-- V15: 설비관리시스템 확장
--   - 소방시설관리 상위 메뉴명을 설비관리시스템으로 변경
--   - 하위 그룹을 소방설비 / 기타설비로 분리
--   - 기타설비: 에어컨, 정수기 메뉴 추가
--   - 기타설비 테이블/점검 이력 테이블 추가
--   - 역할: 시설관리(통합), 소방시설관리, 기타시설관리
-- ============================================================

-- 1) 기타설비 테이블
CREATE TABLE IF NOT EXISTS facility_equipment (
    EQUIPMENT_ID             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '기타설비 ID (PK)',
    CATEGORY                 VARCHAR(30)  NOT NULL                COMMENT '설비 분류 (AIRCON/WATER_PURIFIER)',
    SERIAL_NUMBER            VARCHAR(50)  NOT NULL                COMMENT '일련번호 (AC-000001/WP-000001)',
    BUILDING_ID              BIGINT       NOT NULL                COMMENT '건물 FK',
    FLOOR_ID                 BIGINT       NOT NULL                COMMENT '층 FK',
    EQUIPMENT_TYPE           VARCHAR(100) NOT NULL                COMMENT '설비 종류',
    MANUFACTURE_DATE         DATE         NOT NULL                COMMENT '제조/설치 기준일',
    REPLACEMENT_CYCLE_YEARS  INT          NOT NULL DEFAULT 10     COMMENT '교체 주기 (년)',
    REPLACEMENT_DUE_DATE     DATE                                 COMMENT '교체 예정일',
    QUANTITY                 INT          NOT NULL DEFAULT 1      COMMENT '수량',
    X                        DECIMAL(9,4)                         COMMENT '도면 X 좌표',
    Y                        DECIMAL(9,4)                         COMMENT '도면 Y 좌표',
    IMAGE_PATH               VARCHAR(600)                         COMMENT '대표 이미지 경로',
    NOTE                     VARCHAR(500)                         COMMENT '비고',
    QR_KEY                   VARCHAR(100) NOT NULL                COMMENT 'QR 조회용 고정 키 (UUID)',
    CREATED_AT               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (EQUIPMENT_ID),
    CONSTRAINT UK_FACILITY_EQUIPMENT_SERIAL UNIQUE (SERIAL_NUMBER),
    CONSTRAINT UK_FACILITY_EQUIPMENT_QR_KEY UNIQUE (QR_KEY),
    CONSTRAINT FK_FACILITY_EQUIPMENT_BUILDING FOREIGN KEY (BUILDING_ID) REFERENCES building(BUILDING_ID) ON DELETE RESTRICT,
    CONSTRAINT FK_FACILITY_EQUIPMENT_FLOOR    FOREIGN KEY (FLOOR_ID)    REFERENCES floor(FLOOR_ID)       ON DELETE RESTRICT,
    CONSTRAINT CK_FACILITY_EQUIPMENT_CATEGORY CHECK (CATEGORY IN ('AIRCON', 'WATER_PURIFIER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='기타설비(에어컨/정수기)';

CREATE INDEX IF NOT EXISTS IDX_FACILITY_EQUIPMENT_CATEGORY ON facility_equipment(CATEGORY);
CREATE INDEX IF NOT EXISTS IDX_FACILITY_EQUIPMENT_BUILDING ON facility_equipment(BUILDING_ID);
CREATE INDEX IF NOT EXISTS IDX_FACILITY_EQUIPMENT_FLOOR    ON facility_equipment(FLOOR_ID);

CREATE TABLE IF NOT EXISTS facility_equipment_inspection (
    INSPECTION_ID        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '점검 ID (PK)',
    EQUIPMENT_ID         BIGINT       NOT NULL                COMMENT '기타설비 FK',
    INSPECTION_DATE      DATE         NOT NULL                COMMENT '점검일',
    IS_FAULTY            TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '비정상 여부 (0=정상, 1=비정상)',
    FAULT_REASON         VARCHAR(500)                         COMMENT '불량 사유',
    INSPECTED_BY_USER_ID BIGINT                               COMMENT '점검자 ID',
    INSPECTED_BY_NAME    VARCHAR(200)                         COMMENT '점검자 표시명',
    CREATED_AT           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (INSPECTION_ID),
    CONSTRAINT UK_FACILITY_INSPECTION_DATE UNIQUE (EQUIPMENT_ID, INSPECTION_DATE),
    CONSTRAINT FK_FACILITY_INSPECTION_EQUIPMENT FOREIGN KEY (EQUIPMENT_ID) REFERENCES facility_equipment(EQUIPMENT_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='기타설비 점검 이력';

-- 2) 메뉴 구조: 설비관리시스템 > 소방설비 / 기타설비
UPDATE core_menu
SET MENU_NAME = '설비관리시스템',
    ICON = 'tools',
    DESCRIPTION = '소방설비와 기타설비를 통합 관리하는 설비관리시스템',
    UPDATED_AT = NOW()
WHERE MENU_CODE = 'FIRE_MGMT';

SET @facility_parent_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_MGMT' LIMIT 1);

INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, DESCRIPTION, IS_VISIBLE, IS_ACTIVE, ALLOWED_IPS, CREATED_AT, UPDATED_AT)
SELECT 'FIRE_EQUIPMENT_GROUP', '소방설비', @facility_parent_id, NULL, 'fire', 'MENU', 1, '소방설비 관리 메뉴 그룹', 1, 1, NULL, NOW(), NOW()
WHERE @facility_parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM core_menu WHERE MENU_CODE = 'FIRE_EQUIPMENT_GROUP');

INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, DESCRIPTION, IS_VISIBLE, IS_ACTIVE, ALLOWED_IPS, CREATED_AT, UPDATED_AT)
SELECT 'OTHER_EQUIPMENT_GROUP', '기타설비', @facility_parent_id, NULL, 'building-gear', 'MENU', 2, '기타설비 관리 메뉴 그룹', 1, 1, NULL, NOW(), NOW()
WHERE @facility_parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM core_menu WHERE MENU_CODE = 'OTHER_EQUIPMENT_GROUP');

SET @fire_group_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_EQUIPMENT_GROUP' LIMIT 1);
SET @other_group_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'OTHER_EQUIPMENT_GROUP' LIMIT 1);

UPDATE core_menu
SET PARENT_ID = @fire_group_id,
    UPDATED_AT = NOW()
WHERE @fire_group_id IS NOT NULL
  AND MENU_CODE IN ('FIRE_DASHBOARD', 'FIRE_MAP', 'FIRE_EXTINGUISHER', 'FIRE_HYDRANT', 'FIRE_RECEIVER', 'FIRE_PUMP', 'FIRE_FLOOR', 'FIRE_QR');

INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, DESCRIPTION, IS_VISIBLE, IS_ACTIVE, ALLOWED_IPS, CREATED_AT, UPDATED_AT)
SELECT 'OTHER_AIRCON', '에어컨', @other_group_id, '/facility/air-conditioners', 'snowflake', 'MENU', 1, '에어컨 관리', 1, 1, NULL, NOW(), NOW()
WHERE @other_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM core_menu WHERE MENU_CODE = 'OTHER_AIRCON');

INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, DESCRIPTION, IS_VISIBLE, IS_ACTIVE, ALLOWED_IPS, CREATED_AT, UPDATED_AT)
SELECT 'OTHER_WATER_PURIFIER', '정수기', @other_group_id, '/facility/water-purifiers', 'water', 'MENU', 2, '정수기 관리', 1, 1, NULL, NOW(), NOW()
WHERE @other_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM core_menu WHERE MENU_CODE = 'OTHER_WATER_PURIFIER');

-- 3) 역할 코드: 시설관리(통합), 소방시설관리, 기타시설관리
INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT g.GROUP_ID, 'ROLE_FACILITY_MANAGER', '시설관리', '소방설비와 기타설비 전체 접근 권한', 'facility_manager', TRUE, 4, NOW(), NOW()
FROM code_group g
WHERE g.GROUP_CODE = 'ROLE'
  AND NOT EXISTS (SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = 'ROLE_FACILITY_MANAGER');

INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT g.GROUP_ID, 'ROLE_FIRE_MANAGER', '소방시설관리', '소방설비 전용 접근 권한', 'fire_manager', TRUE, 5, NOW(), NOW()
FROM code_group g
WHERE g.GROUP_CODE = 'ROLE'
  AND NOT EXISTS (SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = 'ROLE_FIRE_MANAGER')
ON DUPLICATE KEY UPDATE CODE_NAME = '소방시설관리', DESCRIPTION = '소방설비 전용 접근 권한', EXTRA_VALUE1 = 'fire_manager', SORT_ORDER = 5, UPDATED_AT = NOW();

INSERT INTO code_detail (GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, IS_ACTIVE, SORT_ORDER, CREATED_AT, UPDATED_AT)
SELECT g.GROUP_ID, 'ROLE_EQUIPMENT_MANAGER', '기타시설관리', '기타설비 전용 접근 권한', 'equipment_manager', TRUE, 6, NOW(), NOW()
FROM code_group g
WHERE g.GROUP_CODE = 'ROLE'
  AND NOT EXISTS (SELECT 1 FROM code_detail d WHERE d.GROUP_ID = g.GROUP_ID AND d.CODE = 'ROLE_EQUIPMENT_MANAGER');

-- 4) 메뉴 권한 재정렬
-- 일반 사용자는 설비관리 메뉴 접근 제외, 역할 기반으로 명확히 제어한다.
DELETE rm FROM core_role_menu rm
JOIN core_menu m ON m.MENU_ID = rm.MENU_ID
WHERE rm.ROLE = 'ROLE_USER'
  AND (m.MENU_CODE LIKE 'FIRE_%' OR m.MENU_CODE LIKE 'OTHER_%');

-- ADMIN: 설비관리 전체
INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_ADMIN', m.MENU_ID, NOW()
FROM core_menu m
WHERE m.MENU_CODE IN ('FIRE_MGMT', 'FIRE_EQUIPMENT_GROUP', 'OTHER_EQUIPMENT_GROUP')
   OR m.MENU_CODE LIKE 'FIRE_%'
   OR m.MENU_CODE LIKE 'OTHER_%'
ON DUPLICATE KEY UPDATE core_role_menu.CREATED_AT = core_role_menu.CREATED_AT;

-- 시설관리: 소방 + 기타 전체
INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_FACILITY_MANAGER', m.MENU_ID, NOW()
FROM core_menu m
WHERE m.MENU_CODE IN ('FIRE_MGMT', 'FIRE_EQUIPMENT_GROUP', 'OTHER_EQUIPMENT_GROUP')
   OR m.MENU_CODE LIKE 'FIRE_%'
   OR m.MENU_CODE LIKE 'OTHER_%'
ON DUPLICATE KEY UPDATE core_role_menu.CREATED_AT = core_role_menu.CREATED_AT;

-- 소방시설관리: 소방설비만
INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_FIRE_MANAGER', m.MENU_ID, NOW()
FROM core_menu m
WHERE m.MENU_CODE IN ('FIRE_MGMT', 'FIRE_EQUIPMENT_GROUP')
   OR (m.MENU_CODE LIKE 'FIRE_%' AND m.MENU_CODE <> 'FIRE_MGMT')
ON DUPLICATE KEY UPDATE core_role_menu.CREATED_AT = core_role_menu.CREATED_AT;

DELETE rm FROM core_role_menu rm
JOIN core_menu m ON m.MENU_ID = rm.MENU_ID
WHERE rm.ROLE = 'ROLE_FIRE_MANAGER'
  AND m.MENU_CODE LIKE 'OTHER_%';

-- 기타시설관리: 기타설비만
INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT 'ROLE_EQUIPMENT_MANAGER', m.MENU_ID, NOW()
FROM core_menu m
WHERE m.MENU_CODE IN ('FIRE_MGMT', 'OTHER_EQUIPMENT_GROUP')
   OR m.MENU_CODE LIKE 'OTHER_%'
ON DUPLICATE KEY UPDATE core_role_menu.CREATED_AT = core_role_menu.CREATED_AT;

DELETE rm FROM core_role_menu rm
JOIN core_menu m ON m.MENU_ID = rm.MENU_ID
WHERE rm.ROLE = 'ROLE_EQUIPMENT_MANAGER'
  AND (m.MENU_CODE = 'FIRE_EQUIPMENT_GROUP' OR (m.MENU_CODE LIKE 'FIRE_%' AND m.MENU_CODE <> 'FIRE_MGMT'));
