-- =============================================================================
-- module-fire: 소방 설비 관리 모듈 DDL (MariaDB)
-- 실행 순서: ../01_ddl_core.sql → 01_schema.sql → 02_seed_data.sql
-- 사전 조건: platform_db 데이터베이스가 이미 존재해야 함
-- 최종 업데이트: 2026-04-09
-- =============================================================================

-- -----------------------------------------------------------------------
-- 건물 마스터
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS building (
    BUILDING_ID    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '건물 ID (PK)',
    BUILDING_NAME  VARCHAR(200) NOT NULL                COMMENT '건물명',
    IS_ACTIVE      TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '활성 여부',

    PRIMARY KEY (BUILDING_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='건물 마스터';

-- -----------------------------------------------------------------------
-- 층 마스터
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS floor (
    FLOOR_ID    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '층 ID (PK)',
    FLOOR_NAME  VARCHAR(100) NOT NULL                COMMENT '층 이름',
    SORT_ORDER  INT          NOT NULL DEFAULT 0      COMMENT '정렬 순서',

    PRIMARY KEY (FLOOR_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='층 마스터';

-- -----------------------------------------------------------------------
-- 소화기 그룹 (도면 위치 단위 마커)
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS extinguisher_group (
    GROUP_ID     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '그룹 ID (PK)',
    BUILDING_ID  BIGINT       NOT NULL                COMMENT '건물 FK',
    FLOOR_ID     BIGINT       NOT NULL                COMMENT '층 FK',
    X            DECIMAL(9,4)                         COMMENT '도면 X 좌표',
    Y            DECIMAL(9,4)                         COMMENT '도면 Y 좌표',
    NOTE         VARCHAR(400)                         COMMENT '비고',
    CREATED_AT   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (GROUP_ID),
    CONSTRAINT FK_EXTGR_BUILDING FOREIGN KEY (BUILDING_ID) REFERENCES building(BUILDING_ID) ON DELETE RESTRICT,
    CONSTRAINT FK_EXTGR_FLOOR    FOREIGN KEY (FLOOR_ID)    REFERENCES floor(FLOOR_ID)       ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='소화기 위치 그룹';

-- -----------------------------------------------------------------------
-- 소화기
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS extinguisher (
    EXTINGUISHER_ID         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '소화기 ID (PK)',
    SERIAL_NUMBER           VARCHAR(50)  NOT NULL                COMMENT '일련번호 (EXT-000001)',
    BUILDING_ID             BIGINT       NOT NULL                COMMENT '건물 FK',
    FLOOR_ID                BIGINT       NOT NULL                COMMENT '층 FK',
    GROUP_ID                BIGINT                               COMMENT '위치 그룹 FK (nullable)',
    EXTINGUISHER_TYPE       VARCHAR(100) NOT NULL                COMMENT '소화기 종류',
    MANUFACTURE_DATE        DATE         NOT NULL                COMMENT '제조일',
    REPLACEMENT_CYCLE_YEARS INT          NOT NULL DEFAULT 10     COMMENT '교체 주기 (년)',
    REPLACEMENT_DUE_DATE    DATE                                 COMMENT '교체 예정일',
    QUANTITY                INT          NOT NULL DEFAULT 1      COMMENT '수량',
    X                       DECIMAL(9,4)                         COMMENT '도면 X 좌표',
    Y                       DECIMAL(9,4)                         COMMENT '도면 Y 좌표',
    IMAGE_PATH              VARCHAR(600)                         COMMENT '이미지 경로',
    NOTE                    VARCHAR(500)                         COMMENT '비고',
    NOTE_KEY                VARCHAR(100) NOT NULL                COMMENT 'QR 조회용 고정 키 (UUID)',
    CREATED_AT              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (EXTINGUISHER_ID),
    CONSTRAINT UK_EXTINGUISHER_SERIAL  UNIQUE (SERIAL_NUMBER),
    CONSTRAINT UK_EXTINGUISHER_NOTEKEY UNIQUE (NOTE_KEY),
    CONSTRAINT FK_EXT_BUILDING FOREIGN KEY (BUILDING_ID) REFERENCES building(BUILDING_ID)          ON DELETE RESTRICT,
    CONSTRAINT FK_EXT_FLOOR    FOREIGN KEY (FLOOR_ID)    REFERENCES floor(FLOOR_ID)                ON DELETE RESTRICT,
    CONSTRAINT FK_EXT_GROUP    FOREIGN KEY (GROUP_ID)    REFERENCES extinguisher_group(GROUP_ID)   ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='소화기';

CREATE INDEX IF NOT EXISTS IDX_EXTINGUISHER_BUILDING ON extinguisher(BUILDING_ID);
CREATE INDEX IF NOT EXISTS IDX_EXTINGUISHER_FLOOR    ON extinguisher(FLOOR_ID);

-- -----------------------------------------------------------------------
-- 소화기 점검 이력
-- 점검 방식: IS_FAULTY(정상/비정상) + FAULT_REASON(불량 사유)
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS extinguisher_inspection (
    INSPECTION_ID        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '점검 ID (PK)',
    EXTINGUISHER_ID      BIGINT       NOT NULL                COMMENT '소화기 FK',
    INSPECTION_DATE      DATE         NOT NULL                COMMENT '점검일',
    IS_FAULTY            TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '비정상 여부 (0=정상, 1=비정상)',
    FAULT_REASON         VARCHAR(500)                         COMMENT '불량 사유',
    INSPECTED_BY_USER_ID BIGINT                               COMMENT '점검자 ID',
    INSPECTED_BY_NAME    VARCHAR(200)                         COMMENT '점검자 표시명',
    CREATED_AT           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (INSPECTION_ID),
    CONSTRAINT UK_EXT_INSPECTION_DATE UNIQUE (EXTINGUISHER_ID, INSPECTION_DATE),
    CONSTRAINT FK_EXTINSP_EXT FOREIGN KEY (EXTINGUISHER_ID) REFERENCES extinguisher(EXTINGUISHER_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='소화기 점검 이력';

-- -----------------------------------------------------------------------
-- 소화전
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_hydrant (
    HYDRANT_ID           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '소화전 ID (PK)',
    SERIAL_NUMBER        VARCHAR(50)  NOT NULL                COMMENT '일련번호 (HYD-000001)',
    HYDRANT_TYPE         VARCHAR(20)  NOT NULL                COMMENT '타입 (Indoor/Outdoor)',
    OPERATION_TYPE       VARCHAR(20)  NOT NULL                COMMENT '작동방식 (Auto/Manual)',
    BUILDING_ID          BIGINT                               COMMENT '건물 FK (옥외=99)',
    FLOOR_ID             BIGINT                               COMMENT '층 FK',
    X                    DECIMAL(5,2)                         COMMENT '도면 X 좌표',
    Y                    DECIMAL(5,2)                         COMMENT '도면 Y 좌표',
    LOCATION_DESCRIPTION VARCHAR(200)                         COMMENT '위치 설명 (옥외)',
    IMAGE_PATH           VARCHAR(600)                         COMMENT '이미지 경로',
    QR_KEY               VARCHAR(100) NOT NULL                COMMENT 'QR 고정 키 (UUID)',
    IS_ACTIVE            TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '활성 여부',
    CREATED_AT           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (HYDRANT_ID),
    CONSTRAINT UK_HYDRANT_SERIAL UNIQUE (SERIAL_NUMBER),
    CONSTRAINT UK_HYDRANT_QR_KEY UNIQUE (QR_KEY),
    CONSTRAINT FK_HYD_BUILDING FOREIGN KEY (BUILDING_ID) REFERENCES building(BUILDING_ID) ON DELETE RESTRICT,
    CONSTRAINT FK_HYD_FLOOR    FOREIGN KEY (FLOOR_ID)    REFERENCES floor(FLOOR_ID)       ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='소화전';

CREATE INDEX IF NOT EXISTS IDX_HYDRANT_BUILDING ON fire_hydrant(BUILDING_ID);
CREATE INDEX IF NOT EXISTS IDX_HYDRANT_FLOOR    ON fire_hydrant(FLOOR_ID);

-- -----------------------------------------------------------------------
-- 소화전 점검 이력
-- 점검 방식: IS_FAULTY(정상/비정상) + FAULT_REASON(불량 사유)
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_hydrant_inspection (
    INSPECTION_ID        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '점검 ID (PK)',
    HYDRANT_ID           BIGINT       NOT NULL                COMMENT '소화전 FK',
    INSPECTION_DATE      DATE         NOT NULL                COMMENT '점검일',
    IS_FAULTY            TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '비정상 여부 (0=정상, 1=비정상)',
    FAULT_REASON         VARCHAR(500)                         COMMENT '불량 사유',
    INSPECTED_BY_USER_ID BIGINT                               COMMENT '점검자 ID',
    INSPECTED_BY_NAME    VARCHAR(200)                         COMMENT '점검자 표시명',
    CREATED_AT           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (INSPECTION_ID),
    CONSTRAINT FK_HYDINSP_HYD FOREIGN KEY (HYDRANT_ID) REFERENCES fire_hydrant(HYDRANT_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='소화전 점검 이력';

-- -----------------------------------------------------------------------
-- 수신기
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_receiver (
    RECEIVER_ID          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '수신기 ID (PK)',
    SERIAL_NUMBER        VARCHAR(50)  NOT NULL                COMMENT '일련번호 (RCV-000001)',
    BUILDING_NAME        VARCHAR(200) NOT NULL                COMMENT '건물명',
    FLOOR_ID             BIGINT       NOT NULL                COMMENT '층 FK',
    X                    DECIMAL(5,2)                         COMMENT '도면 X 좌표 (%)',
    Y                    DECIMAL(5,2)                         COMMENT '도면 Y 좌표 (%)',
    LOCATION_DESCRIPTION VARCHAR(200)                         COMMENT '위치 설명',
    NOTE                 VARCHAR(500)                         COMMENT '비고',
    QR_KEY               VARCHAR(100) NOT NULL                COMMENT 'QR 고정 키 (UUID)',
    IS_ACTIVE            TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '활성 여부',
    CREATED_AT           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (RECEIVER_ID),
    CONSTRAINT UK_RECEIVER_SERIAL UNIQUE (SERIAL_NUMBER),
    CONSTRAINT UK_RECEIVER_QR_KEY UNIQUE (QR_KEY),
    CONSTRAINT FK_RECEIVER_FLOOR  FOREIGN KEY (FLOOR_ID) REFERENCES floor(FLOOR_ID) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='수신기';

-- -----------------------------------------------------------------------
-- 수신기 점검 이력
-- 점검 방식: INSPECTION_STATUS(상태값) + 개별 항목별 상태 컬럼
-- ※ 소화기/소화전과 달리 IS_FAULTY 없음
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_receiver_inspection (
    INSPECTION_ID          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '점검 ID (PK)',
    RECEIVER_ID            BIGINT        NOT NULL                COMMENT '수신기 FK',
    INSPECTION_DATE        DATE          NOT NULL                COMMENT '점검일',
    INSPECTION_TIME        TIME                                  COMMENT '점검 시각',
    INSPECTION_STATUS      VARCHAR(30)   NOT NULL DEFAULT 'NORMAL' COMMENT '점검 상태 (NORMAL/ABNORMAL)',
    CHECKLIST_JSON         LONGTEXT                              COMMENT '체크리스트 JSON',
    IMAGE_PATH             VARCHAR(600)                          COMMENT '점검 이미지 경로',
    NOTE                   VARCHAR(1000)                         COMMENT '비고',
    POWER_STATUS           VARCHAR(30)                           COMMENT '전원 상태',
    SWITCH_STATUS          VARCHAR(30)                           COMMENT '스위치 상태',
    TRANSFER_DEVICE_STATUS VARCHAR(30)                           COMMENT '중계기 상태',
    ZONE_MAP_STATUS        VARCHAR(30)                           COMMENT '구역도 상태',
    CONTINUITY_TEST_STATUS VARCHAR(30)                           COMMENT '도통시험 상태',
    OPERATION_TEST_STATUS  VARCHAR(30)                           COMMENT '작동시험 상태',
    INSPECTED_BY_USER_ID   BIGINT                                COMMENT '점검자 ID',
    INSPECTED_BY_NAME      VARCHAR(200)                          COMMENT '점검자 표시명',
    CREATED_AT             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (INSPECTION_ID),
    CONSTRAINT UK_RECEIVER_INSPECTION_DATE UNIQUE (RECEIVER_ID, INSPECTION_DATE),
    CONSTRAINT FK_RECEIVERINSP_RECEIVER FOREIGN KEY (RECEIVER_ID) REFERENCES fire_receiver(RECEIVER_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='수신기 점검 이력';

-- -----------------------------------------------------------------------
-- 소방펌프
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_pump (
    PUMP_ID              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '소방펌프 ID (PK)',
    SERIAL_NUMBER        VARCHAR(50)  NOT NULL                COMMENT '일련번호 (PMP-000001)',
    BUILDING_NAME        VARCHAR(200) NOT NULL                COMMENT '건물명',
    FLOOR_ID             BIGINT       NOT NULL                COMMENT '층 FK',
    X                    DECIMAL(5,2)                         COMMENT '도면 X 좌표 (%)',
    Y                    DECIMAL(5,2)                         COMMENT '도면 Y 좌표 (%)',
    LOCATION_DESCRIPTION VARCHAR(200)                         COMMENT '위치 설명',
    NOTE                 VARCHAR(500)                         COMMENT '비고',
    QR_KEY               VARCHAR(100) NOT NULL                COMMENT 'QR 고정 키 (UUID)',
    IS_ACTIVE            TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '활성 여부',
    CREATED_AT           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (PUMP_ID),
    CONSTRAINT UK_PUMP_SERIAL UNIQUE (SERIAL_NUMBER),
    CONSTRAINT UK_PUMP_QR_KEY UNIQUE (QR_KEY),
    CONSTRAINT FK_PUMP_FLOOR  FOREIGN KEY (FLOOR_ID) REFERENCES floor(FLOOR_ID) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='소방펌프';

-- -----------------------------------------------------------------------
-- 소방펌프 점검 이력
-- 점검 방식: INSPECTION_STATUS(상태값) + 개별 항목별 상태 컬럼
-- ※ 소화기/소화전과 달리 IS_FAULTY 없음
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_pump_inspection (
    INSPECTION_ID         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '점검 ID (PK)',
    PUMP_ID               BIGINT        NOT NULL                COMMENT '소방펌프 FK',
    INSPECTION_DATE       DATE          NOT NULL                COMMENT '점검일',
    INSPECTION_TIME       TIME                                  COMMENT '점검 시각',
    INSPECTION_STATUS     VARCHAR(30)   NOT NULL DEFAULT 'NORMAL' COMMENT '점검 상태 (NORMAL/ABNORMAL)',
    CHECKLIST_JSON        LONGTEXT                              COMMENT '체크리스트 JSON',
    IMAGE_PATH            VARCHAR(600)                          COMMENT '점검 이미지 경로',
    NOTE                  VARCHAR(1000)                         COMMENT '비고',
    PUMP_OPERATION_STATUS VARCHAR(30)                           COMMENT '펌프 작동 상태',
    PANEL_STATUS          VARCHAR(30)                           COMMENT '제어반 상태',
    WATER_SUPPLY_STATUS   VARCHAR(30)                           COMMENT '급수 상태',
    FUEL_STATUS           VARCHAR(30)                           COMMENT '연료 상태',
    DRAIN_PUMP_STATUS     VARCHAR(30)                           COMMENT '배수펌프 상태',
    PIPING_STATUS         VARCHAR(30)                           COMMENT '배관 상태',
    INSPECTED_BY_USER_ID  BIGINT                                COMMENT '점검자 ID',
    INSPECTED_BY_NAME     VARCHAR(200)                          COMMENT '점검자 표시명',
    CREATED_AT            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (INSPECTION_ID),
    CONSTRAINT UK_PUMP_INSPECTION_DATE UNIQUE (PUMP_ID, INSPECTION_DATE),
    CONSTRAINT FK_PUMPINSP_PUMP FOREIGN KEY (PUMP_ID) REFERENCES fire_pump(PUMP_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='소방펌프 점검 이력';

-- =============================================================================
-- 뷰 (Views)
-- =============================================================================

-- -----------------------------------------------------------------------
-- 뷰: 소화기 목록 (최종 점검 정보 포함)
-- 컬럼: IS_FAULTY, FAULT_REASON 사용
-- -----------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_extinguisher_list AS
SELECT
    e.EXTINGUISHER_ID,
    b.BUILDING_NAME,
    f.FLOOR_NAME,
    e.EXTINGUISHER_TYPE,
    e.MANUFACTURE_DATE,
    e.REPLACEMENT_CYCLE_YEARS,
    e.REPLACEMENT_DUE_DATE,
    e.QUANTITY,
    e.NOTE,
    e.SERIAL_NUMBER,
    e.NOTE_KEY,
    (SELECT ei.INSPECTION_DATE FROM extinguisher_inspection ei WHERE ei.EXTINGUISHER_ID = e.EXTINGUISHER_ID ORDER BY ei.INSPECTION_DATE DESC, ei.INSPECTION_ID DESC LIMIT 1) AS LAST_INSPECTION_DATE,
    (SELECT ei.INSPECTED_BY_NAME FROM extinguisher_inspection ei WHERE ei.EXTINGUISHER_ID = e.EXTINGUISHER_ID ORDER BY ei.INSPECTION_DATE DESC, ei.INSPECTION_ID DESC LIMIT 1) AS LAST_INSPECTOR_NAME,
    (SELECT ei.IS_FAULTY FROM extinguisher_inspection ei WHERE ei.EXTINGUISHER_ID = e.EXTINGUISHER_ID ORDER BY ei.INSPECTION_DATE DESC, ei.INSPECTION_ID DESC LIMIT 1) AS LAST_IS_FAULTY,
    (SELECT ei.FAULT_REASON FROM extinguisher_inspection ei WHERE ei.EXTINGUISHER_ID = e.EXTINGUISHER_ID ORDER BY ei.INSPECTION_DATE DESC, ei.INSPECTION_ID DESC LIMIT 1) AS LAST_FAULT_REASON
FROM extinguisher e
JOIN building b ON e.BUILDING_ID = b.BUILDING_ID
JOIN floor f    ON e.FLOOR_ID    = f.FLOOR_ID;

-- -----------------------------------------------------------------------
-- 뷰: 수신기 목록 (최종 점검 정보 포함)
-- 컬럼: INSPECTION_STATUS, NOTE 사용 (IS_FAULTY 없음)
-- -----------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_fire_receiver_list AS
SELECT
    r.RECEIVER_ID, r.SERIAL_NUMBER, r.BUILDING_NAME, r.FLOOR_ID, f.FLOOR_NAME,
    r.X, r.Y, r.LOCATION_DESCRIPTION, r.NOTE, r.QR_KEY, r.IS_ACTIVE,
    (SELECT ri.INSPECTION_DATE FROM fire_receiver_inspection ri WHERE ri.RECEIVER_ID = r.RECEIVER_ID ORDER BY ri.INSPECTION_DATE DESC, ri.INSPECTION_ID DESC LIMIT 1) AS LAST_INSPECTION_DATE,
    (SELECT ri.INSPECTED_BY_NAME FROM fire_receiver_inspection ri WHERE ri.RECEIVER_ID = r.RECEIVER_ID ORDER BY ri.INSPECTION_DATE DESC, ri.INSPECTION_ID DESC LIMIT 1) AS LAST_INSPECTOR_NAME,
    (SELECT ri.INSPECTION_STATUS FROM fire_receiver_inspection ri WHERE ri.RECEIVER_ID = r.RECEIVER_ID ORDER BY ri.INSPECTION_DATE DESC, ri.INSPECTION_ID DESC LIMIT 1) AS LAST_INSPECTION_STATUS,
    (SELECT ri.NOTE FROM fire_receiver_inspection ri WHERE ri.RECEIVER_ID = r.RECEIVER_ID ORDER BY ri.INSPECTION_DATE DESC, ri.INSPECTION_ID DESC LIMIT 1) AS LAST_INSPECTION_NOTE
FROM fire_receiver r
JOIN floor f ON r.FLOOR_ID = f.FLOOR_ID;

-- -----------------------------------------------------------------------
-- 뷰: 소방펌프 목록 (최종 점검 정보 포함)
-- 컬럼: INSPECTION_STATUS, NOTE 사용 (IS_FAULTY 없음)
-- -----------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_fire_pump_list AS
SELECT
    p.PUMP_ID, p.SERIAL_NUMBER, p.BUILDING_NAME, p.FLOOR_ID, f.FLOOR_NAME,
    p.X, p.Y, p.LOCATION_DESCRIPTION, p.NOTE, p.QR_KEY, p.IS_ACTIVE,
    (SELECT pi.INSPECTION_DATE FROM fire_pump_inspection pi WHERE pi.PUMP_ID = p.PUMP_ID ORDER BY pi.INSPECTION_DATE DESC, pi.INSPECTION_ID DESC LIMIT 1) AS LAST_INSPECTION_DATE,
    (SELECT pi.INSPECTED_BY_NAME FROM fire_pump_inspection pi WHERE pi.PUMP_ID = p.PUMP_ID ORDER BY pi.INSPECTION_DATE DESC, pi.INSPECTION_ID DESC LIMIT 1) AS LAST_INSPECTOR_NAME,
    (SELECT pi.INSPECTION_STATUS FROM fire_pump_inspection pi WHERE pi.PUMP_ID = p.PUMP_ID ORDER BY pi.INSPECTION_DATE DESC, pi.INSPECTION_ID DESC LIMIT 1) AS LAST_INSPECTION_STATUS,
    (SELECT pi.NOTE FROM fire_pump_inspection pi WHERE pi.PUMP_ID = p.PUMP_ID ORDER BY pi.INSPECTION_DATE DESC, pi.INSPECTION_ID DESC LIMIT 1) AS LAST_INSPECTION_NOTE
FROM fire_pump p
JOIN floor f ON p.FLOOR_ID = f.FLOOR_ID;
