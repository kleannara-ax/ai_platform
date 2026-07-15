SET NAMES utf8mb4;

-- ============================================================
-- V34: 에어컨 식별 No. 선택 입력 지원
--   - facility_equipment.SERIAL_NUMBER의 NOT NULL 제약을 제거
--   - 에어컨 모바일 QR 등록에서 식별 No.를 비우면 NULL로 저장
--   - UNIQUE 인덱스는 유지하며 MariaDB는 여러 NULL 값을 허용
-- ============================================================

SET @has_facility_equipment_serial := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'facility_equipment'
      AND COLUMN_NAME = 'SERIAL_NUMBER'
);

SET @is_facility_equipment_serial_nullable := (
    SELECT IS_NULLABLE = 'YES' FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'facility_equipment'
      AND COLUMN_NAME = 'SERIAL_NUMBER'
);

SET @sql := IF(
    @has_facility_equipment_serial > 0 AND NOT COALESCE(@is_facility_equipment_serial_nullable, 0),
    'ALTER TABLE facility_equipment MODIFY COLUMN SERIAL_NUMBER VARCHAR(50) NULL COMMENT ''식별 No. (에어컨은 선택 입력)''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
