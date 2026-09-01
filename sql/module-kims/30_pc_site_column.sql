-- 30_pc_site_column.sql
-- PC 관리 사업장(SITE) 구분 도입 — 서울/청주 PC를 별도 관리.
-- 기존 전체 데이터는 '청주'(현행 유지). 서울은 이후 신규 등록분부터 SITE='서울'.
-- 조회는 site 파라미터가 있으면 그 사업장만, 없으면 전체(기존 동작 보존) — JPQL의
-- "(:site IS NULL OR i.site = :site)" 패턴으로 처리한다.
-- idempotent: INFORMATION_SCHEMA 체크 후 없을 때만 ALTER/CREATE INDEX (재실행 안전).

SET NAMES utf8mb4;

SET @has_site_column := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ip_address'
      AND COLUMN_NAME = 'SITE'
);
SET @sql := IF(@has_site_column = 0,
    "ALTER TABLE ip_address ADD COLUMN SITE VARCHAR(20) NOT NULL DEFAULT '청주' COMMENT '사업장 구분 (청주/서울)' AFTER IP_GROUP",
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 명시적 백필 (DEFAULT 로 이미 청주지만 안전하게)
UPDATE ip_address SET SITE = '청주' WHERE SITE IS NULL OR SITE = '';

SET @has_site_index := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ip_address'
      AND INDEX_NAME = 'IDX_IP_ADDRESS_SITE'
);
SET @sql := IF(@has_site_index = 0,
    'CREATE INDEX IDX_IP_ADDRESS_SITE ON ip_address (SITE)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
