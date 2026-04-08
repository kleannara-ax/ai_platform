USE fireweb;

ALTER TABLE fire_receiver_inspection
    ADD COLUMN IF NOT EXISTS inspection_time TIME NULL COMMENT 'Inspection time',
    ADD COLUMN IF NOT EXISTS inspection_status VARCHAR(30) NOT NULL DEFAULT 'NORMAL' COMMENT 'Inspection status',
    ADD COLUMN IF NOT EXISTS checklist_json LONGTEXT NULL COMMENT 'Inspection checklist JSON',
    ADD COLUMN IF NOT EXISTS image_path VARCHAR(600) NULL COMMENT 'Inspection image path',
    ADD COLUMN IF NOT EXISTS note VARCHAR(1000) NULL COMMENT 'Inspection note',
    ADD COLUMN IF NOT EXISTS power_status VARCHAR(30) NULL COMMENT 'Power status',
    ADD COLUMN IF NOT EXISTS switch_status VARCHAR(30) NULL COMMENT 'Switch status',
    ADD COLUMN IF NOT EXISTS transfer_device_status VARCHAR(30) NULL COMMENT 'Transfer device status',
    ADD COLUMN IF NOT EXISTS zone_map_status VARCHAR(30) NULL COMMENT 'Zone map status',
    ADD COLUMN IF NOT EXISTS continuity_test_status VARCHAR(30) NULL COMMENT 'Continuity test status',
    ADD COLUMN IF NOT EXISTS operation_test_status VARCHAR(30) NULL COMMENT 'Operation test status';

ALTER TABLE fire_receiver_inspection
    MODIFY COLUMN checklist_json LONGTEXT NULL COMMENT 'Inspection checklist JSON';

ALTER TABLE fire_pump_inspection
    ADD COLUMN IF NOT EXISTS inspection_time TIME NULL COMMENT 'Inspection time',
    ADD COLUMN IF NOT EXISTS inspection_status VARCHAR(30) NOT NULL DEFAULT 'NORMAL' COMMENT 'Inspection status',
    ADD COLUMN IF NOT EXISTS checklist_json LONGTEXT NULL COMMENT 'Inspection checklist JSON',
    ADD COLUMN IF NOT EXISTS image_path VARCHAR(600) NULL COMMENT 'Inspection image path',
    ADD COLUMN IF NOT EXISTS note VARCHAR(1000) NULL COMMENT 'Inspection note',
    ADD COLUMN IF NOT EXISTS pump_operation_status VARCHAR(30) NULL COMMENT 'Pump operation status',
    ADD COLUMN IF NOT EXISTS panel_status VARCHAR(30) NULL COMMENT 'Panel status',
    ADD COLUMN IF NOT EXISTS water_supply_status VARCHAR(30) NULL COMMENT 'Water supply status',
    ADD COLUMN IF NOT EXISTS fuel_status VARCHAR(30) NULL COMMENT 'Fuel status',
    ADD COLUMN IF NOT EXISTS drain_pump_status VARCHAR(30) NULL COMMENT 'Drain pump status',
    ADD COLUMN IF NOT EXISTS piping_status VARCHAR(30) NULL COMMENT 'Piping status';

ALTER TABLE fire_pump_inspection
    MODIFY COLUMN checklist_json LONGTEXT NULL COMMENT 'Inspection checklist JSON';

CREATE OR REPLACE VIEW vw_fire_receiver_list AS
SELECT
    r.receiver_id,
    r.serial_number,
    r.building_name,
    r.floor_id,
    f.floor_name,
    r.x,
    r.y,
    r.location_description,
    r.note,
    r.qr_key,
    r.is_active,
    (
        SELECT ri.inspection_date
        FROM fire_receiver_inspection ri
        WHERE ri.receiver_id = r.receiver_id
        ORDER BY ri.inspection_date DESC, ri.inspection_id DESC
        LIMIT 1
    ) AS last_inspection_date,
    (
        SELECT ri.inspected_by_name
        FROM fire_receiver_inspection ri
        WHERE ri.receiver_id = r.receiver_id
        ORDER BY ri.inspection_date DESC, ri.inspection_id DESC
        LIMIT 1
    ) AS last_inspector_name,
    (
        SELECT ri.inspection_status
        FROM fire_receiver_inspection ri
        WHERE ri.receiver_id = r.receiver_id
        ORDER BY ri.inspection_date DESC, ri.inspection_id DESC
        LIMIT 1
    ) AS last_inspection_status,
    (
        SELECT ri.note
        FROM fire_receiver_inspection ri
        WHERE ri.receiver_id = r.receiver_id
        ORDER BY ri.inspection_date DESC, ri.inspection_id DESC
        LIMIT 1
    ) AS last_inspection_note
FROM fire_receiver r
JOIN floor f ON r.floor_id = f.floor_id;

CREATE OR REPLACE VIEW vw_fire_pump_list AS
SELECT
    p.pump_id,
    p.serial_number,
    p.building_name,
    p.floor_id,
    f.floor_name,
    p.x,
    p.y,
    p.location_description,
    p.note,
    p.qr_key,
    p.is_active,
    (
        SELECT pi.inspection_date
        FROM fire_pump_inspection pi
        WHERE pi.pump_id = p.pump_id
        ORDER BY pi.inspection_date DESC, pi.inspection_id DESC
        LIMIT 1
    ) AS last_inspection_date,
    (
        SELECT pi.inspected_by_name
        FROM fire_pump_inspection pi
        WHERE pi.pump_id = p.pump_id
        ORDER BY pi.inspection_date DESC, pi.inspection_id DESC
        LIMIT 1
    ) AS last_inspector_name,
    (
        SELECT pi.inspection_status
        FROM fire_pump_inspection pi
        WHERE pi.pump_id = p.pump_id
        ORDER BY pi.inspection_date DESC, pi.inspection_id DESC
        LIMIT 1
    ) AS last_inspection_status,
    (
        SELECT pi.note
        FROM fire_pump_inspection pi
        WHERE pi.pump_id = p.pump_id
        ORDER BY pi.inspection_date DESC, pi.inspection_id DESC
        LIMIT 1
    ) AS last_inspection_note
FROM fire_pump p
JOIN floor f ON p.floor_id = f.floor_id;
