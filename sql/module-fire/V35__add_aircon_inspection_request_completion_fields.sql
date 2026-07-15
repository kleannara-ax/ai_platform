SET NAMES utf8mb4;

-- 에어컨 점검 요청과 완료 처리 정보를 분리한다.
-- 기존 요청 데이터는 그대로 유지하고 완료한 점검자/결과/완료 시각만 별도 저장한다.

SET @has_inspector_name := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'facility_aircon_fault_report'
      AND COLUMN_NAME = 'INSPECTOR_NAME'
);
SET @sql := IF(@has_inspector_name = 0,
    'ALTER TABLE facility_aircon_fault_report ADD COLUMN INSPECTOR_NAME VARCHAR(100) NULL AFTER STATUS',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_inspection_result := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'facility_aircon_fault_report'
      AND COLUMN_NAME = 'INSPECTION_RESULT'
);
SET @sql := IF(@has_inspection_result = 0,
    'ALTER TABLE facility_aircon_fault_report ADD COLUMN INSPECTION_RESULT VARCHAR(30) NULL AFTER INSPECTOR_NAME',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_completed_at := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'facility_aircon_fault_report'
      AND COLUMN_NAME = 'COMPLETED_AT'
);
SET @sql := IF(@has_completed_at = 0,
    'ALTER TABLE facility_aircon_fault_report ADD COLUMN COMPLETED_AT DATETIME NULL AFTER INSPECTION_RESULT',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX IDX_FACILITY_AIRCON_FAULT_STATUS
    ON facility_aircon_fault_report (EQUIPMENT_ID, STATUS, CREATED_AT);
