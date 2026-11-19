-- ============================================================
-- module-steam-energy: 스팀에너지관리 DDL
-- Database: MariaDB 10.11+ (utf8mb4)
-- Naming: table = lower_snake_case, column = UPPER_SNAKE_CASE
-- ddl-auto=none 이므로 반드시 본 스크립트로 테이블을 생성한다.
-- ============================================================

-- ────────────────────────────────────────────────
-- 1) 스팀 설비 마스터
-- ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS steam_equipment (
    EQUIPMENT_ID   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '설비 ID (PK, 자동 증가)',
    EQUIPMENT_CODE VARCHAR(50)  NOT NULL                COMMENT '설비코드 (예: PM-2, TM-3, FLUID, LNG_BOILER)',
    NAME           VARCHAR(100) NOT NULL                COMMENT '설비명',
    CATEGORY       VARCHAR(50)  NULL                    COMMENT '구분 (제지/화장지/소각로/보일러/LNG/기타)',
    UNIT           VARCHAR(20)  NULL                    COMMENT '기준 단위 (톤, N㎥ 등)',
    SORT_ORDER     INT          NOT NULL DEFAULT 0      COMMENT '정렬 순서',
    IS_ACTIVE      TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '활성 여부 (1:사용, 0:미사용)',
    CREATED_AT     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT     DATETIME     NULL                    COMMENT '수정일시',
    CREATED_BY     BIGINT       NULL COMMENT '생성자 사용자 ID',
    UPDATED_BY     BIGINT       NULL COMMENT '수정자 사용자 ID',
    DELETED_YN     CHAR(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT     DATETIME     NULL COMMENT '삭제일시',
    DELETED_BY     BIGINT       NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (EQUIPMENT_ID),
    CONSTRAINT UK_STEAM_EQUIPMENT_CODE UNIQUE (EQUIPMENT_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 스팀 설비 마스터';

CREATE INDEX IF NOT EXISTS IDX_STEAM_EQUIPMENT_CATEGORY   ON steam_equipment (CATEGORY);
CREATE INDEX IF NOT EXISTS IDX_STEAM_EQUIPMENT_DELETED_YN ON steam_equipment (DELETED_YN);

-- ────────────────────────────────────────────────
-- 2) 시설별 계약·구매 단가 (연/월별)
-- ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS steam_price (
    PRICE_ID    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '단가 ID (PK, 자동 증가)',
    YEAR_NO     INT           NOT NULL                COMMENT '연도 (예: 2025)',
    MONTH_NO    INT           NOT NULL                COMMENT '월 (1~12)',
    FACILITY    VARCHAR(50)   NOT NULL                COMMENT '시설 (LNG/복합보일러/폐합성소각로/유동상소각로/신설소각로)',
    ITEM_CODE   VARCHAR(50)   NOT NULL                COMMENT '항목코드 (예: lng_unit, comb_bsc)',
    ITEM_NAME   VARCHAR(200)  NOT NULL                COMMENT '항목명',
    INPUT_TYPE  VARCHAR(20)   NOT NULL DEFAULT 'DIRECT' COMMENT '입력방식 (DIRECT:직접입력, AUTO:자동계산)',
    UNIT        VARCHAR(20)   NULL                    COMMENT '단위 (원/㎥, 원/톤, 원/HR 등)',
    PRICE_VALUE DECIMAL(18,6) NULL                    COMMENT '단가 값',
    REMARK      VARCHAR(500)  NULL                    COMMENT '비고',
    CREATED_AT  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT  DATETIME      NULL                    COMMENT '수정일시',
    CREATED_BY     BIGINT       NULL COMMENT '생성자 사용자 ID',
    UPDATED_BY     BIGINT       NULL COMMENT '수정자 사용자 ID',
    DELETED_YN     CHAR(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT     DATETIME     NULL COMMENT '삭제일시',
    DELETED_BY     BIGINT       NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (PRICE_ID),
    CONSTRAINT UK_STEAM_PRICE_YM_ITEM UNIQUE (YEAR_NO, MONTH_NO, ITEM_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 시설별 계약·구매 단가 (연/월별)';

CREATE INDEX IF NOT EXISTS IDX_STEAM_PRICE_YEAR     ON steam_price (YEAR_NO);
CREATE INDEX IF NOT EXISTS IDX_STEAM_PRICE_FACILITY ON steam_price (FACILITY);
CREATE INDEX IF NOT EXISTS IDX_STEAM_PRICE_DELETED_YN ON steam_price (DELETED_YN);

-- ────────────────────────────────────────────────
-- 3) 일일 스팀 사용 실적 (설비별 일자)
-- ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS steam_daily_usage (
    USAGE_ID      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '사용실적 ID (PK, 자동 증가)',
    USAGE_DATE    DATE          NOT NULL                COMMENT '사용일자',
    EQUIPMENT_ID  BIGINT        NOT NULL                COMMENT '설비 ID (steam_equipment.EQUIPMENT_ID 참조)',
    STEAM_AMOUNT  DECIMAL(18,4) NULL                    COMMENT '스팀량 (톤)',
    RUNTIME_HOURS DECIMAL(12,4) NULL                    COMMENT '가동시간 (hr)',
    PRODUCTION_KG DECIMAL(18,4) NULL                    COMMENT '생산량 (kg)',
    UNIT_RATE     DECIMAL(18,6) NULL                    COMMENT '원단위 (톤/hr 또는 원/톤)',
    REMARK        VARCHAR(500)  NULL                    COMMENT '비고',
    CREATED_AT    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT    DATETIME      NULL                    COMMENT '수정일시',
    CREATED_BY     BIGINT       NULL COMMENT '생성자 사용자 ID',
    UPDATED_BY     BIGINT       NULL COMMENT '수정자 사용자 ID',
    DELETED_YN     CHAR(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT     DATETIME     NULL COMMENT '삭제일시',
    DELETED_BY     BIGINT       NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (USAGE_ID),
    CONSTRAINT UK_STEAM_USAGE_DATE_EQUIP UNIQUE (USAGE_DATE, EQUIPMENT_ID),
    CONSTRAINT FK_STEAM_USAGE_EQUIPMENT FOREIGN KEY (EQUIPMENT_ID)
        REFERENCES steam_equipment (EQUIPMENT_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 일일 스팀 사용 실적';

CREATE INDEX IF NOT EXISTS IDX_STEAM_USAGE_DATE  ON steam_daily_usage (USAGE_DATE);
CREATE INDEX IF NOT EXISTS IDX_STEAM_USAGE_EQUIP ON steam_daily_usage (EQUIPMENT_ID);
CREATE INDEX IF NOT EXISTS IDX_STEAM_USAGE_DELETED_YN ON steam_daily_usage (DELETED_YN);

-- ────────────────────────────────────────────────
-- 4) 원단위 일지 — 호기별 일일 스팀 사용·생산 실적 (2026-11 추가)
--    화면: 스팀사용 원단위 / 스팀 사용량 / 오늘 일지 등록 / 호기별 요약
--    호기(MACHINE_CODE): PM-2, PM-3, TM-3, TM-4, TM-5. 스팀량 합·원단위 등 계산값은 저장하지 않는다.
-- ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS steam_unit_daily_usage (
    UNIT_USAGE_ID           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '원단위 일지 ID (PK, 자동 증가)',
    USAGE_DATE              DATE          NOT NULL                COMMENT '일자',
    MACHINE_CODE            VARCHAR(20)   NOT NULL                COMMENT '호기 (PM-2/PM-3/TM-3/TM-4/TM-5)',
    MAIN_STEAM              DECIMAL(18,6) NULL                    COMMENT 'Main 스팀량(톤) — PM-2/PM-3',
    COATER_STEAM            DECIMAL(18,6) NULL                    COMMENT 'Coater 스팀량(톤) — PM-2/PM-3',
    VENTILATION_STEAM       DECIMAL(18,6) NULL                    COMMENT '환기시스템 스팀량(톤) — PM-3',
    STEAM                   DECIMAL(18,6) NULL                    COMMENT '스팀량(톤) — TM-3/TM-4/TM-5',
    PRODUCTION_KG           DECIMAL(18,6) NULL                    COMMENT '생산량(kg)',
    PRODUCTION_TYPE         VARCHAR(50)   NULL                    COMMENT '생산지종 (SC/ACB/KB 등) — PM-2/PM-3',
    RUNTIME_HOURS           DECIMAL(12,4) NULL                    COMMENT '가동시간(hr)',
    DISPERSER_RUNTIME_HOURS DECIMAL(12,4) NULL                    COMMENT '디스퍼져 가동시간(hr) — PM-3',
    VENT_RUNTIME_HOURS      DECIMAL(12,4) NULL                    COMMENT 'Ventilation 가동시간(hr) — PM-3',
    NOTE                    VARCHAR(500)  NULL                    COMMENT '비고',
    CREATED_AT              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT              DATETIME      NULL                    COMMENT '수정일시',
    CREATED_BY              BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_BY              BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN              CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT              DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY              BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (UNIT_USAGE_ID),
    CONSTRAINT UK_STEAM_UNIT_DAILY_USAGE_DATE_MACHINE UNIQUE (USAGE_DATE, MACHINE_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 원단위 일지 — 호기별 일일 스팀 사용·생산 실적';

CREATE INDEX IF NOT EXISTS IDX_STEAM_UNIT_DAILY_USAGE_DATE       ON steam_unit_daily_usage (USAGE_DATE);
CREATE INDEX IF NOT EXISTS IDX_STEAM_UNIT_DAILY_USAGE_MACHINE    ON steam_unit_daily_usage (MACHINE_CODE);
CREATE INDEX IF NOT EXISTS IDX_STEAM_UNIT_DAILY_USAGE_DELETED_YN ON steam_unit_daily_usage (DELETED_YN);

-- ────────────────────────────────────────────────
-- 5) 원단위 일지 — 호기에 속하지 않는 일자별 공통 계측값 (2026-11 추가)
-- ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS steam_unit_daily_common (
    UNIT_COMMON_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '원단위 공통값 ID (PK, 자동 증가)',
    USAGE_DATE      DATE          NOT NULL                COMMENT '일자',
    DISPERSER_TOTAL DECIMAL(18,6) NULL                    COMMENT '디스퍼져 스팀량 총량(톤)',
    TOC_STEAM       DECIMAL(18,6) NULL                    COMMENT 'TOC 스팀량 (환경관리)',
    PICA121_VENT    DECIMAL(18,6) NULL                    COMMENT 'PICA-121 Vent',
    CREATED_AT      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT      DATETIME      NULL                    COMMENT '수정일시',
    CREATED_BY      BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_BY      BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN      CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT      DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY      BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (UNIT_COMMON_ID),
    CONSTRAINT UK_STEAM_UNIT_DAILY_COMMON_DATE UNIQUE (USAGE_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 원단위 일지 — 일자별 공통 계측값';

CREATE INDEX IF NOT EXISTS IDX_STEAM_UNIT_DAILY_COMMON_DELETED_YN ON steam_unit_daily_common (DELETED_YN);

-- ────────────────────────────────────────────────
-- 6) 일별 LNG 연료 사용량 (2026-11 추가)
--    화면: LNG 연료 사용 비용 / 오늘 일지 등록 / 호기별 요약. 사용합계·비용·총비용은 화면 계산.
--    MIX_* (복합+외부 LNG 호기별)는 엑셀 왕복용으로만 보관한다.
-- ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS steam_lng_daily_usage (
    LNG_USAGE_ID           BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'LNG 일일 사용 ID (PK, 자동 증가)',
    USAGE_DATE             DATE          NOT NULL                COMMENT '일자',
    BOILER_20T_1           DECIMAL(18,6) NULL                    COMMENT '보일러 LNG 20톤-1 (N㎥)',
    BOILER_20T_2           DECIMAL(18,6) NULL                    COMMENT '보일러 LNG 20톤-2 (N㎥)',
    BOILER_50T             DECIMAL(18,6) NULL                    COMMENT '보일러 LNG 50톤 (N㎥)',
    BOILER_60T             DECIMAL(18,6) NULL                    COMMENT '보일러 LNG 60톤 (N㎥)',
    BOILER_COMBINED        DECIMAL(18,6) NULL                    COMMENT '복합보일러 LNG (N㎥)',
    BOILER_NEW_INCINERATOR DECIMAL(18,6) NULL                    COMMENT '신설소각로 LNG (N㎥)',
    BOILER_UNIT_PRICE      DECIMAL(18,6) NULL                    COMMENT '보일러 LNG 단가 (원/N㎥) — 단가 입력 lng_unit 전파',
    BURNER_PM3             DECIMAL(18,6) NULL                    COMMENT '버너 LNG PM-3 (N㎥)',
    BURNER_TM3             DECIMAL(18,6) NULL                    COMMENT '버너 LNG TM-3 (N㎥)',
    BURNER_TM4             DECIMAL(18,6) NULL                    COMMENT '버너 LNG TM-4 (N㎥)',
    BURNER_TM5             DECIMAL(18,6) NULL                    COMMENT '버너 LNG TM-5 (N㎥)',
    BURNER_UNIT_PRICE      DECIMAL(18,6) NULL                    COMMENT '버너 LNG 단가 (원/N㎥) — 단가 입력 lng_unit 전파',
    MIX_PM2                DECIMAL(18,6) NULL                    COMMENT '복합+외부 LNG PM-2 (엑셀 왕복용)',
    MIX_PM3                DECIMAL(18,6) NULL                    COMMENT '복합+외부 LNG PM-3 (엑셀 왕복용)',
    MIX_TM3                DECIMAL(18,6) NULL                    COMMENT '복합+외부 LNG TM-3 (엑셀 왕복용)',
    MIX_TM4                DECIMAL(18,6) NULL                    COMMENT '복합+외부 LNG TM-4 (엑셀 왕복용)',
    MIX_TM5                DECIMAL(18,6) NULL                    COMMENT '복합+외부 LNG TM-5 (엑셀 왕복용)',
    CREATED_AT             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT             DATETIME      NULL                    COMMENT '수정일시',
    CREATED_BY             BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_BY             BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN             CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT             DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY             BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (LNG_USAGE_ID),
    CONSTRAINT UK_STEAM_LNG_DAILY_USAGE_DATE UNIQUE (USAGE_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 일별 LNG 연료 사용량';

CREATE INDEX IF NOT EXISTS IDX_STEAM_LNG_DAILY_USAGE_DELETED_YN ON steam_lng_daily_usage (DELETED_YN);

-- ────────────────────────────────────────────────
-- 7) 유동상·폐합성소각로·복합보일러·외부보일러 일일 스팀 구매량 (2026-11 추가)
--    화면: 유동상/소각로/복합 일일 사용량 / 오늘 일지 등록 / 호기별 요약.
--    유동상 스팀량(FLUID_STEAM_TON)은 유동상 운전일지·세부 운영내역도 쓴다. 비용·스팀구매비는 화면 계산.
-- ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS steam_fuel_daily_usage (
    FUEL_USAGE_ID          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '스팀 구매 일일 사용 ID (PK, 자동 증가)',
    USAGE_DATE             DATE          NOT NULL                COMMENT '일자',
    FLUID_STEAM_TON        DECIMAL(18,6) NULL                    COMMENT '유동상 스팀량(톤)',
    FLUID_UNIT_PRICE       DECIMAL(18,6) NULL                    COMMENT '유동상 단가(원/톤) — 단가 입력 fluid_steam 전파',
    INCINERATOR_STEAM_TON  DECIMAL(18,6) NULL                    COMMENT '폐합성소각로 스팀생산량(톤)',
    INCINERATOR_UNIT_PRICE DECIMAL(18,6) NULL                    COMMENT '폐합성소각로 단가(원/톤) — 단가 입력 waste2_steam 전파',
    COMBINED_STEAM_TON     DECIMAL(18,6) NULL                    COMMENT '복합보일러 스팀생산량(톤)',
    COMBINED_UNIT_PRICE    DECIMAL(18,6) NULL                    COMMENT '복합보일러 단가(원/톤) — 직접 입력',
    COMBINED_DEDUCTION     DECIMAL(18,6) NULL                    COMMENT '복합보일러 차감비(백만원)',
    EXT_BOILER_STEAM_TON   DECIMAL(18,6) NULL                    COMMENT '외부보일러 스팀생산량(톤)',
    EXT_BOILER_UNIT_PRICE  DECIMAL(18,6) NULL                    COMMENT '외부보일러 단가(원/톤) — 직접 입력',
    EXT_BOILER_DEDUCTION   DECIMAL(18,6) NULL                    COMMENT '외부보일러 차감비(백만원)',
    CREATED_AT             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT             DATETIME      NULL                    COMMENT '수정일시',
    CREATED_BY             BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_BY             BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN             CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT             DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY             BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (FUEL_USAGE_ID),
    CONSTRAINT UK_STEAM_FUEL_DAILY_USAGE_DATE UNIQUE (USAGE_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 유동상·폐합성소각로·복합보일러·외부보일러 일일 스팀 구매량';

CREATE INDEX IF NOT EXISTS IDX_STEAM_FUEL_DAILY_USAGE_DELETED_YN ON steam_fuel_daily_usage (DELETED_YN);
