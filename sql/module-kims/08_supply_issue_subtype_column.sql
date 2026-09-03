-- 08_supply_issue_subtype_column.sql
-- 소모품 지급 시 세부 구분(신형/구형, 제조사 등) 저장 — supply_issue.SUB_TYPE 컬럼 추가.
-- 마우스/키보드/노트북/모니터/데스크탑 본체는 "신형"/"구형", 태블릿은 "레노버"/"갤럭시"/"대여"
-- 중 하나가 저장된다. 구분이 없는 품목을 지급한 경우는 NULL.
-- idempotent: INFORMATION_SCHEMA 체크 후 없을 때만 ALTER (재실행 안전).

SET NAMES utf8mb4;

SET @has_subtype_column := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'supply_issue'
      AND COLUMN_NAME = 'SUB_TYPE'
);
SET @sql := IF(@has_subtype_column = 0,
    "ALTER TABLE supply_issue ADD COLUMN SUB_TYPE VARCHAR(20) DEFAULT NULL COMMENT '세부 구분 (신형/구형/레노버/갤럭시/대여 등)' AFTER ISSUED_AT",
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
