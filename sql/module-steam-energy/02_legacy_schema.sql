-- ============================================================
-- module-steam-energy: 레거시 스팀 대시보드 스키마 (이관)
-- steam 표준 대시보드가 사용하는 범용 table_cell_value 및 unit_usage 등.
-- 플랫폼 DB(ddl-auto=none)에 수동 실행. 01_schema.sql 이후 실행 권장.
-- ============================================================

CREATE TABLE IF NOT EXISTS unit_usage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    month VARCHAR(7) NOT NULL,
    year_no INT NULL,
    month_no INT NULL,
    day VARCHAR(4) NOT NULL,
    machine_no VARCHAR(32) NOT NULL,
    main_steam DECIMAL(18,10) NULL,
    coater_steam DECIMAL(18,10) NULL,
    disperser_steam DECIMAL(18,10) NULL,
    ventilation_steam DECIMAL(18,10) NULL,
    production DECIMAL(18,10) NULL,
    production_type VARCHAR(255) NULL,
    steam DECIMAL(18,10) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_unit_usage_month_day_machine (month, day, machine_no)
);

ALTER TABLE unit_usage
    ADD COLUMN IF NOT EXISTS year_no INT NULL AFTER month,
    ADD COLUMN IF NOT EXISTS month_no INT NULL AFTER year_no,
    ADD COLUMN IF NOT EXISTS disperser_steam DECIMAL(18,10) NULL AFTER coater_steam,
    MODIFY COLUMN main_steam DECIMAL(18,10) NULL,
    MODIFY COLUMN coater_steam DECIMAL(18,10) NULL,
    MODIFY COLUMN disperser_steam DECIMAL(18,10) NULL,
    MODIFY COLUMN ventilation_steam DECIMAL(18,10) NULL,
    MODIFY COLUMN production DECIMAL(18,10) NULL,
    MODIFY COLUMN steam DECIMAL(18,10) NULL;

UPDATE unit_usage
SET
    year_no = CAST(SUBSTRING(month, 1, 4) AS UNSIGNED),
    month_no = CAST(SUBSTRING(month, 6, 2) AS UNSIGNED)
WHERE month IS NOT NULL
  AND month <> ''
  AND (year_no IS NULL OR month_no IS NULL);

CREATE TABLE IF NOT EXISTS unit_usage_raw (
    id BIGINT NOT NULL AUTO_INCREMENT,
    month VARCHAR(7) NOT NULL,
    year_no INT NULL,
    month_no INT NULL,
    day VARCHAR(4) NOT NULL,
    disperser_total DECIMAL(18,10) NULL,
    toc_steam DECIMAL(18,10) NULL,
    pica121_vent DECIMAL(18,10) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_unit_usage_raw_month_day (month, day)
);

ALTER TABLE unit_usage_raw
    ADD COLUMN IF NOT EXISTS year_no INT NULL AFTER month,
    ADD COLUMN IF NOT EXISTS month_no INT NULL AFTER year_no,
    MODIFY COLUMN disperser_total DECIMAL(18,10) NULL,
    MODIFY COLUMN toc_steam DECIMAL(18,10) NULL,
    MODIFY COLUMN pica121_vent DECIMAL(18,10) NULL;

UPDATE unit_usage_raw
SET
    year_no = CAST(SUBSTRING(month, 1, 4) AS UNSIGNED),
    month_no = CAST(SUBSTRING(month, 6, 2) AS UNSIGNED)
WHERE month IS NOT NULL
  AND month <> ''
  AND (year_no IS NULL OR month_no IS NULL);

CREATE TABLE IF NOT EXISTS table_cell_value (
    id BIGINT NOT NULL AUTO_INCREMENT,
    month VARCHAR(7) NOT NULL,
    year_no INT NULL,
    month_no INT NULL,
    table_name VARCHAR(32) NOT NULL,
    row_key VARCHAR(64) NOT NULL,
    col_index INT NOT NULL,
    cell_value TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_table_cell_value_month_table_row_col (month, table_name, row_key, col_index)
);

ALTER TABLE table_cell_value
    ADD COLUMN IF NOT EXISTS year_no INT NULL AFTER month,
    ADD COLUMN IF NOT EXISTS month_no INT NULL AFTER year_no;

UPDATE table_cell_value
SET
    year_no = CAST(SUBSTRING(month, 1, 4) AS UNSIGNED),
    month_no = CAST(SUBSTRING(month, 6, 2) AS UNSIGNED)
WHERE month IS NOT NULL
  AND month <> ''
  AND (year_no IS NULL OR month_no IS NULL);

CREATE INDEX IF NOT EXISTS ix_table_cell_value_table_month
    ON table_cell_value (table_name, month);
