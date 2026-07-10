SET NAMES utf8mb4;

-- ============================================================
-- V33: 기타설비 에어컨 자동 ID 추가
--   - facility_equipment.EQUIPMENT_CODE 컬럼 추가
--   - 에어컨은 AC-000001 형식의 자동 ID를 저장
--   - 에어컨 현장 식별 No.(SERIAL_NUMBER)와 별도 관리
--   - 정수기는 기존 SERIAL_NUMBER(WP-000001 형식) 자동 생성 정책을 유지하며
--     EQUIPMENT_CODE에는 별도 값을 저장하지 않음
--
-- V31은 모바일 QR 업무 테이블만 담당하고, 이번 에어컨 ID 스키마 변경은
-- 신규 버전 SQL인 V33에서만 반영한다.
-- ============================================================

SET @has_facility_equipment_code := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'facility_equipment'
      AND COLUMN_NAME = 'EQUIPMENT_CODE'
);
SET @sql := IF(@has_facility_equipment_code = 0,
    'ALTER TABLE facility_equipment ADD COLUMN EQUIPMENT_CODE VARCHAR(50) NULL AFTER SERIAL_NUMBER',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 개발 중 임시 SQL이 적용되어 정수기에 EQUIPMENT_CODE가 들어간 경우 정리한다.
-- 정수기 ID는 SERIAL_NUMBER의 WP-000001 형식 자동 생성 값을 그대로 사용한다.
UPDATE facility_equipment
SET EQUIPMENT_CODE = NULL
WHERE CATEGORY = 'WATER_PURIFIER'
  AND EQUIPMENT_CODE REGEXP '^WP-[0-9]+$';

-- 초기 개발 버전 적용 과정에서 AC-1.0000처럼 소수점이 포함된 값이 들어간 경우
-- 정식 AC-000001 형식으로 다시 채울 수 있도록 먼저 비운다.
UPDATE facility_equipment
SET EQUIPMENT_CODE = NULL
WHERE CATEGORY = 'AIRCON'
  AND EQUIPMENT_CODE REGEXP '^AC-[0-9]+\\.[0-9]+$';

SET @facility_ac_code_base := (
    SELECT COALESCE(MAX(CAST(SUBSTRING(EQUIPMENT_CODE, 4) AS UNSIGNED)), 0)
    FROM facility_equipment
    WHERE CATEGORY = 'AIRCON'
      AND EQUIPMENT_CODE REGEXP '^AC-[0-9]+$'
);
UPDATE facility_equipment e
JOIN (
    SELECT EQUIPMENT_ID,
           CONCAT('AC-', LPAD(CAST(@facility_ac_code_base + ROW_NUMBER() OVER (ORDER BY EQUIPMENT_ID) AS UNSIGNED), 6, '0')) AS NEXT_CODE
    FROM facility_equipment
    WHERE CATEGORY = 'AIRCON'
      AND (EQUIPMENT_CODE IS NULL OR EQUIPMENT_CODE = '')
) seq ON seq.EQUIPMENT_ID = e.EQUIPMENT_ID
SET e.EQUIPMENT_CODE = seq.NEXT_CODE;

SET @has_facility_equipment_code_index := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'facility_equipment'
      AND INDEX_NAME = 'UK_FACILITY_EQUIPMENT_CODE'
);
SET @sql := IF(@has_facility_equipment_code_index = 0,
    'ALTER TABLE facility_equipment ADD UNIQUE KEY UK_FACILITY_EQUIPMENT_CODE (EQUIPMENT_CODE)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
