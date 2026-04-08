-- =============================================================================
-- 03_ddl_fire.sql
-- 소화기/소화전 도메인 DDL (MariaDB) - module-fire
-- 기존 ASP.NET MSSQL → MariaDB 변환
-- 주요 변경:
--   - SYSUTCDATETIME() → NOW()
--   - DB Computed Column(ReplacementDueDate) → 애플리케이션(@PrePersist)에서 계산
--   - SEQ_ExtinguisherSerial, SEQ_FireHydrantSerial → AUTO_INCREMENT + 서비스 레이어 생성
--   - OUTPUT Clause 이슈 없음 (MariaDB는 해당 없음)
-- =============================================================================

USE fireweb;

-- -----------------------------------------------------------------------
-- 소화기 그룹 (도면 위치 단위)
-- 기존 ASP.NET: ExtinguisherGroup 테이블
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS extinguisher_group (
    group_id        BIGINT          NOT NULL AUTO_INCREMENT COMMENT '그룹 ID',
    building_id     BIGINT          NOT NULL                COMMENT '건물 FK',
    floor_id        BIGINT          NOT NULL                COMMENT '층 FK',
    x               DECIMAL(9,4)                            COMMENT '도면 X 좌표',
    y               DECIMAL(9,4)                            COMMENT '도면 Y 좌표',
    note            VARCHAR(400)                            COMMENT '비고',
    created_at      DATETIME        NOT NULL DEFAULT NOW()  COMMENT '등록일시',

    PRIMARY KEY (group_id),
    CONSTRAINT fk_extgr_building FOREIGN KEY (building_id) REFERENCES building(building_id) ON DELETE RESTRICT,
    CONSTRAINT fk_extgr_floor    FOREIGN KEY (floor_id)    REFERENCES floor(floor_id)       ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='소화기 위치 그룹';

-- -----------------------------------------------------------------------
-- 소화기
-- 기존 ASP.NET: Extinguisher 테이블
-- 변경:
--   - SerialNumber: DB Sequence+DEFAULT 제거 → 서비스에서 생성 (EXT-000001)
--   - ReplacementDueDate: Computed Column 제거 → @PrePersist/@PreUpdate에서 계산
--   - NoteKey: DB DEFAULT (UUID) 제거 → @PrePersist에서 UUID 생성
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS extinguisher (
    extinguisher_id         BIGINT          NOT NULL AUTO_INCREMENT COMMENT '소화기 ID',
    serial_number           VARCHAR(50)     NOT NULL                COMMENT '일련번호 (EXT-000001)',
    building_id             BIGINT          NOT NULL                COMMENT '건물 FK',
    floor_id                BIGINT          NOT NULL                COMMENT '층 FK',
    group_id                BIGINT                                  COMMENT '위치 그룹 FK (nullable)',
    extinguisher_type       VARCHAR(100)    NOT NULL                COMMENT '소화기 종류',
    manufacture_date            DATE            NOT NULL                COMMENT '제조일 (기존 InstallDate)',
    replacement_cycle_years INT             NOT NULL DEFAULT 10      COMMENT '교체 주기(년)',
    replacement_due_date    DATE                                    COMMENT '교체 예정일 (제조일+주기)',
    quantity                INT             NOT NULL DEFAULT 1      COMMENT '수량',
    x                       DECIMAL(9,4)                            COMMENT '도면 X 좌표',
    y                       DECIMAL(9,4)                            COMMENT '도면 Y 좌표',
    image_path              VARCHAR(600)                            COMMENT '이미지 경로',
    note                    VARCHAR(500)                            COMMENT '비고',
    note_key                VARCHAR(100)    NOT NULL                COMMENT 'QR 조회용 고정 키 (UUID)',
    created_at              DATETIME        NOT NULL DEFAULT NOW()  COMMENT '등록일시',

    PRIMARY KEY (extinguisher_id),
    UNIQUE KEY uk_extinguisher_serial  (serial_number),
    UNIQUE KEY uk_extinguisher_notekey (note_key),
    CONSTRAINT fk_ext_building FOREIGN KEY (building_id) REFERENCES building(building_id)         ON DELETE RESTRICT,
    CONSTRAINT fk_ext_floor    FOREIGN KEY (floor_id)    REFERENCES floor(floor_id)               ON DELETE RESTRICT,
    CONSTRAINT fk_ext_group    FOREIGN KEY (group_id)    REFERENCES extinguisher_group(group_id)  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='소화기';

-- -----------------------------------------------------------------------
-- 소화기 점검 이력
-- 기존 ASP.NET: ExtinguisherInspection 테이블
-- - Cascade Delete: extinguisher 삭제 시 점검 이력 자동 삭제
-- - 당일 중복 점검 방지 유니크 제약
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS extinguisher_inspection (
    inspection_id           BIGINT          NOT NULL AUTO_INCREMENT COMMENT '점검 ID',
    extinguisher_id         BIGINT          NOT NULL                COMMENT '소화기 FK',
    inspection_date         DATE            NOT NULL                COMMENT '점검일',
    is_faulty               TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '비정상 여부',
    fault_reason            VARCHAR(500)                            COMMENT '불량 사유',
    inspected_by_user_id    BIGINT                                  COMMENT '점검자 ID (web_user FK)',
    inspected_by_name       VARCHAR(200)                            COMMENT '점검자 표시명 스냅샷',
    created_at              DATETIME        NOT NULL DEFAULT NOW()  COMMENT '등록일시',

    PRIMARY KEY (inspection_id),
    UNIQUE KEY uk_ext_inspection_date (extinguisher_id, inspection_date),
    CONSTRAINT fk_extinsp_ext FOREIGN KEY (extinguisher_id) REFERENCES extinguisher(extinguisher_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='소화기 점검 이력';

-- -----------------------------------------------------------------------
-- 소화전
-- 기존 ASP.NET: FireHydrant 테이블
-- 변경:
--   - SerialNumber: DB Sequence+DEFAULT 제거 → 서비스에서 생성 (HYD-000001)
--   - TRIGGER 제거 (MSSQL OUTPUT Clause 우회용 → MariaDB에서 불필요)
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_hydrant (
    hydrant_id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '소화전 ID',
    serial_number           VARCHAR(50)     NOT NULL                COMMENT '일련번호 (HYD-000001)',
    hydrant_type            VARCHAR(20)     NOT NULL                COMMENT '타입 (Indoor/Outdoor)',
    operation_type          VARCHAR(20)     NOT NULL                COMMENT '작동방식 (Auto/Manual)',
    building_id             BIGINT                                  COMMENT '건물 FK (옥외=99)',
    floor_id                BIGINT                                  COMMENT '층 FK (옥외=1)',
    x                       DECIMAL(5,2)                            COMMENT '도면 X 좌표',
    y                       DECIMAL(5,2)                            COMMENT '도면 Y 좌표',
    location_description    VARCHAR(200)                            COMMENT '위치 설명 (옥외)',
    image_path              VARCHAR(600)                            COMMENT '이미지 경로',
    is_active               TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '활성 여부',
    created_at              DATETIME        NOT NULL DEFAULT NOW()  COMMENT '등록일시',

    PRIMARY KEY (hydrant_id),
    UNIQUE KEY uk_hydrant_serial (serial_number),
    CONSTRAINT fk_hyd_building FOREIGN KEY (building_id) REFERENCES building(building_id) ON DELETE RESTRICT,
    CONSTRAINT fk_hyd_floor    FOREIGN KEY (floor_id)    REFERENCES floor(floor_id)       ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='소화전';

-- -----------------------------------------------------------------------
-- 소화전 점검 이력
-- 기존 ASP.NET: FireHydrantInspection 테이블
-- - TRIGGER 제거 (MSSQL에서 OUTPUT Clause 우회용)
-- - Cascade Delete: fire_hydrant 삭제 시 점검 이력 자동 삭제
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_hydrant_inspection (
    inspection_id           BIGINT          NOT NULL AUTO_INCREMENT COMMENT '점검 ID',
    hydrant_id              BIGINT          NOT NULL                COMMENT '소화전 FK',
    inspection_date         DATE            NOT NULL                COMMENT '점검일',
    is_faulty               TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '비정상 여부',
    fault_reason            VARCHAR(500)                            COMMENT '불량 사유',
    inspected_by_user_id    BIGINT                                  COMMENT '점검자 ID (web_user FK)',
    inspected_by_name       VARCHAR(200)                            COMMENT '점검자 표시명 스냅샷',
    created_at              DATETIME        NOT NULL DEFAULT NOW()  COMMENT '등록일시',

    PRIMARY KEY (inspection_id),
    CONSTRAINT fk_hydinsp_hyd FOREIGN KEY (hydrant_id) REFERENCES fire_hydrant(hydrant_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='소화전 점검 이력';


-- -----------------------------------------------------------------------
-- 뷰: vw_extinguisher_list
-- 기존 ASP.NET: vw_ExtinguisherList
-- 최종 점검 정보를 합쳐서 조회 성능/가독성 확보
-- (JPA에서는 직접 쿼리 사용, 뷰는 레거시 연동/보고서용으로 유지)
-- -----------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_extinguisher_list AS
SELECT
    e.extinguisher_id,
    b.building_name,
    f.floor_name,
    e.extinguisher_type,
    e.manufacture_date,
    e.replacement_cycle_years,
    e.replacement_due_date,
    e.quantity,
    e.note,
    e.serial_number,
    e.note_key,
    (
        SELECT ei.inspection_date
        FROM extinguisher_inspection ei
        WHERE ei.extinguisher_id = e.extinguisher_id
        ORDER BY ei.inspection_date DESC, ei.inspection_id DESC
        LIMIT 1
    ) AS last_inspection_date,
    (
        SELECT ei.inspected_by_name
        FROM extinguisher_inspection ei
        WHERE ei.extinguisher_id = e.extinguisher_id
        ORDER BY ei.inspection_date DESC, ei.inspection_id DESC
        LIMIT 1
    ) AS last_inspector_name,
    (
        SELECT ei.is_faulty
        FROM extinguisher_inspection ei
        WHERE ei.extinguisher_id = e.extinguisher_id
        ORDER BY ei.inspection_date DESC, ei.inspection_id DESC
        LIMIT 1
    ) AS last_is_faulty,
    (
        SELECT ei.fault_reason
        FROM extinguisher_inspection ei
        WHERE ei.extinguisher_id = e.extinguisher_id
        ORDER BY ei.inspection_date DESC, ei.inspection_id DESC
        LIMIT 1
    ) AS last_fault_reason
FROM extinguisher e
JOIN building b ON e.building_id = b.building_id
JOIN floor f    ON e.floor_id    = f.floor_id;

-- -----------------------------------------------------------------------
-- Fire receiver
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_receiver (
    receiver_id             BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'Fire receiver ID',
    serial_number           VARCHAR(50)     NOT NULL                COMMENT 'Serial number (RCV-000001)',
    building_name           VARCHAR(200)    NOT NULL                COMMENT 'Building name',
    floor_id                BIGINT          NOT NULL                COMMENT 'Floor FK',
    x                       DECIMAL(5,2)                            COMMENT 'Main screen X percent',
    y                       DECIMAL(5,2)                            COMMENT 'Main screen Y percent',
    location_description    VARCHAR(200)                            COMMENT 'Location description',
    note                    VARCHAR(500)                            COMMENT 'Note',
    qr_key                  VARCHAR(100)    NOT NULL                COMMENT 'QR fixed key',
    is_active               TINYINT(1)      NOT NULL DEFAULT 1      COMMENT 'Active flag',
    created_at              DATETIME        NOT NULL DEFAULT NOW()  COMMENT 'Created at',

    PRIMARY KEY (receiver_id),
    UNIQUE KEY uk_receiver_serial (serial_number),
    UNIQUE KEY uk_receiver_qr_key (qr_key),
    CONSTRAINT fk_receiver_floor    FOREIGN KEY (floor_id)    REFERENCES floor(floor_id)       ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fire receiver';

-- -----------------------------------------------------------------------
-- Fire receiver inspection history
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_receiver_inspection (
    inspection_id           BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'Inspection ID',
    receiver_id             BIGINT          NOT NULL                COMMENT 'Fire receiver FK',
    inspection_date         DATE            NOT NULL                COMMENT 'Inspection date',
    inspection_time         TIME                                    COMMENT 'Inspection time',
    is_faulty               TINYINT(1)      NOT NULL DEFAULT 0      COMMENT 'Fault flag',
    fault_reason            VARCHAR(500)                            COMMENT 'Fault reason',
    power_status            VARCHAR(30)                             COMMENT 'Power status',
    switch_status           VARCHAR(30)                             COMMENT 'Switch status',
    transfer_device_status  VARCHAR(30)                             COMMENT 'Transfer device status',
    zone_map_status         VARCHAR(30)                             COMMENT 'Zone map status',
    continuity_test_status  VARCHAR(30)                             COMMENT 'Continuity test status',
    operation_test_status   VARCHAR(30)                             COMMENT 'Operation test status',
    inspected_by_user_id    BIGINT                                  COMMENT 'Inspector user ID',
    inspected_by_name       VARCHAR(200)                            COMMENT 'Inspector display name snapshot',
    created_at              DATETIME        NOT NULL DEFAULT NOW()  COMMENT 'Created at',

    PRIMARY KEY (inspection_id),
    UNIQUE KEY uk_receiver_inspection_date (receiver_id, inspection_date),
    CONSTRAINT fk_receiverinsp_receiver FOREIGN KEY (receiver_id) REFERENCES fire_receiver(receiver_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fire receiver inspection history';

-- -----------------------------------------------------------------------
-- Fire pump
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_pump (
    pump_id                 BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'Fire pump ID',
    serial_number           VARCHAR(50)     NOT NULL                COMMENT 'Serial number (PMP-000001)',
    building_name           VARCHAR(200)    NOT NULL                COMMENT 'Building name',
    floor_id                BIGINT          NOT NULL                COMMENT 'Floor FK',
    x                       DECIMAL(5,2)                            COMMENT 'Main screen X percent',
    y                       DECIMAL(5,2)                            COMMENT 'Main screen Y percent',
    location_description    VARCHAR(200)                            COMMENT 'Location description',
    note                    VARCHAR(500)                            COMMENT 'Note',
    qr_key                  VARCHAR(100)    NOT NULL                COMMENT 'QR fixed key',
    is_active               TINYINT(1)      NOT NULL DEFAULT 1      COMMENT 'Active flag',
    created_at              DATETIME        NOT NULL DEFAULT NOW()  COMMENT 'Created at',

    PRIMARY KEY (pump_id),
    UNIQUE KEY uk_pump_serial (serial_number),
    UNIQUE KEY uk_pump_qr_key (qr_key),
    CONSTRAINT fk_pump_floor    FOREIGN KEY (floor_id)    REFERENCES floor(floor_id)       ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fire pump';

-- -----------------------------------------------------------------------
-- Fire pump inspection history
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_pump_inspection (
    inspection_id           BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'Inspection ID',
    pump_id                 BIGINT          NOT NULL                COMMENT 'Fire pump FK',
    inspection_date         DATE            NOT NULL                COMMENT 'Inspection date',
    inspection_time         TIME                                    COMMENT 'Inspection time',
    is_faulty               TINYINT(1)      NOT NULL DEFAULT 0      COMMENT 'Fault flag',
    fault_reason            VARCHAR(500)                            COMMENT 'Fault reason',
    pump_operation_status   VARCHAR(30)                             COMMENT 'Pump operation status',
    panel_status            VARCHAR(30)                             COMMENT 'Panel status',
    water_supply_status     VARCHAR(30)                             COMMENT 'Water supply status',
    fuel_status             VARCHAR(30)                             COMMENT 'Fuel status',
    drain_pump_status       VARCHAR(30)                             COMMENT 'Drain pump status',
    piping_status           VARCHAR(30)                             COMMENT 'Piping status',
    inspected_by_user_id    BIGINT                                  COMMENT 'Inspector user ID',
    inspected_by_name       VARCHAR(200)                            COMMENT 'Inspector display name snapshot',
    created_at              DATETIME        NOT NULL DEFAULT NOW()  COMMENT 'Created at',

    PRIMARY KEY (inspection_id),
    UNIQUE KEY uk_pump_inspection_date (pump_id, inspection_date),
    CONSTRAINT fk_pumpinsp_pump FOREIGN KEY (pump_id) REFERENCES fire_pump(pump_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fire pump inspection history';

-- -----------------------------------------------------------------------
-- View: receiver list with latest inspection snapshot
-- -----------------------------------------------------------------------
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
        SELECT ri.is_faulty
        FROM fire_receiver_inspection ri
        WHERE ri.receiver_id = r.receiver_id
        ORDER BY ri.inspection_date DESC, ri.inspection_id DESC
        LIMIT 1
    ) AS last_is_faulty,
    (
        SELECT ri.fault_reason
        FROM fire_receiver_inspection ri
        WHERE ri.receiver_id = r.receiver_id
        ORDER BY ri.inspection_date DESC, ri.inspection_id DESC
        LIMIT 1
    ) AS last_fault_reason
FROM fire_receiver r
JOIN floor f    ON r.floor_id    = f.floor_id;

-- -----------------------------------------------------------------------
-- View: pump list with latest inspection snapshot
-- -----------------------------------------------------------------------
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
        SELECT pi.is_faulty
        FROM fire_pump_inspection pi
        WHERE pi.pump_id = p.pump_id
        ORDER BY pi.inspection_date DESC, pi.inspection_id DESC
        LIMIT 1
    ) AS last_is_faulty,
    (
        SELECT pi.fault_reason
        FROM fire_pump_inspection pi
        WHERE pi.pump_id = p.pump_id
        ORDER BY pi.inspection_date DESC, pi.inspection_id DESC
        LIMIT 1
    ) AS last_fault_reason
FROM fire_pump p
JOIN floor f    ON p.floor_id    = f.floor_id;
