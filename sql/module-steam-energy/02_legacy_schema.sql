-- ============================================================
-- module-steam-energy: 이관 대시보드 스키마
--   steam_unit_usage      원단위 일지 (설비·일자별)
--   steam_unit_usage_raw  원단위 일지 원시값 (디스퍼져·TOC·PICA)
--   steam_cell_value      화면별 셀 저장소 — table_name 으로 논리 표를 나눈다 (CELL_CONVENTIONS.md)
-- 플랫폼 DB(ddl-auto=none)에 수동 실행. 01_schema.sql 다음.
-- 재실행 안전 (CREATE TABLE / CREATE INDEX 모두 IF NOT EXISTS).
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS steam_unit_usage (
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
    UNIQUE KEY uk_steam_unit_usage_month_day_machine (month, day, machine_no)
);


CREATE TABLE IF NOT EXISTS steam_unit_usage_raw (
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
    UNIQUE KEY uk_steam_unit_usage_raw_month_day (month, day)
);


CREATE TABLE IF NOT EXISTS steam_cell_value (
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
    UNIQUE KEY uk_steam_cell_value_month_table_row_col (month, table_name, row_key, col_index)
);


CREATE INDEX IF NOT EXISTS ix_steam_cell_value_table_month
    ON steam_cell_value (table_name, month);
