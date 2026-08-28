SET NAMES utf8mb4;

-- ============================================================
-- V1: PC(IP) 관리대장 사업장 구분(SITE) 컬럼 추가
--   - ip_address.SITE VARCHAR(20) NOT NULL DEFAULT 'CHEONGJU'
--   - 기존 데이터는 전부 청주공장(CHEONGJU) 데이터이므로 DEFAULT/UPDATE 모두 CHEONGJU
--   - 서울(SEOUL) 탭 신설을 위한 선행 마이그레이션. 재실행해도 안전(컬럼 존재 시 스킵)
-- ============================================================

SET @has_ip_address_site := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ip_address'
      AND COLUMN_NAME = 'SITE'
);

SET @sql := IF(
    @has_ip_address_site = 0,
    'ALTER TABLE ip_address ADD COLUMN SITE VARCHAR(20) NOT NULL DEFAULT ''CHEONGJU'' COMMENT ''사업장 구분 (CHEONGJU/SEOUL)'' AFTER IP_GROUP',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 기존 데이터는 전부 청주공장 데이터이므로 명시적으로 한 번 더 보정(안전장치)
UPDATE ip_address SET SITE = 'CHEONGJU' WHERE SITE IS NULL OR SITE = '';

SET @has_ip_address_site_idx := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ip_address'
      AND INDEX_NAME = 'IDX_IP_ADDRESS_SITE'
);

SET @sql2 := IF(
    @has_ip_address_site_idx = 0,
    'ALTER TABLE ip_address ADD INDEX IDX_IP_ADDRESS_SITE (SITE)',
    'SELECT 1'
);
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
