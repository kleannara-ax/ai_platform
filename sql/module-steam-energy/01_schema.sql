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

-- ============================================================
-- 8) 에너지 회계비용 그룹 (2026-11 규격 전환, 묶음 2)
--   연/월별 한 행, 입력 항목 1개 = 컬럼 1개. '화면 계산값' 이라고 적힌 컬럼은 다른 화면이 읽는 계산 결과를 저장한 것.
--   옛 steam_cell_value 논리 표 energy_accounting_plan / energy_fuel_plan / energy_fuel_ratio / energy_month_compare / energy_fuel_summary 를 대체.
--   전년 12월·다음해 1월 열은 별도 저장 없이 이웃 연도 행을 쓴다.
-- ============================================================

CREATE TABLE IF NOT EXISTS steam_energy_accounting_plan (
    ACCOUNTING_PLAN_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '회계비용 계획 ID (PK)',
    YEAR_NO              INT           NOT NULL COMMENT '연도',
    MONTH_NO             INT           NOT NULL COMMENT '월 (1~12)',
    COMPOSITE_STEAM_BUY  DECIMAL(18,6) NULL COMMENT '연료비: 복합보일러 스팀구매(백만원)',
    ZESCO_STEAM_BUY      DECIMAL(18,6) NULL COMMENT '연료비: 제스코 스팀구매(백만원)',
    LNG_BUY              DECIMAL(18,6) NULL COMMENT '연료비: LNG 구매(백만원)',
    INCINERATOR_COST     DECIMAL(18,6) NULL COMMENT '연료비: 폐합성소각로(백만원)',
    FLUID_COST           DECIMAL(18,6) NULL COMMENT '연료비: 유동상소각로(백만원)',
    ZESCO_OFFSET         DECIMAL(18,6) NULL COMMENT '차감비: 제스코 상계비(용수사용 등)',
    ZESCO_PENALTY        DECIMAL(18,6) NULL COMMENT '차감비: 제스코 페널티',
    ZESCO_ADVANCE_REPAY  DECIMAL(18,6) NULL COMMENT '차감비: 제스코 선급금 상환',
    COMPOSITE_LNG_WATER  DECIMAL(18,6) NULL COMMENT '차감비: 복합 LNG·용수사용량',
    COMPOSITE_INVEST     DECIMAL(18,6) NULL COMMENT '차감비: 복합 투자비 감가상각',
    COMPOSITE_INCOME     DECIMAL(18,6) NULL COMMENT '차감비: 복합보일러 처분수익 연료비 차감분',
    LAND_LEASE           DECIMAL(18,6) NULL COMMENT '차감비: 토지 지상권 지료',
    PAPER_FUEL           DECIMAL(18,6) NULL COMMENT '제지 연료비 = 실적 × 0.7 (화면 계산값, 연료비 요약이 읽음)',
    LIVING_FUEL          DECIMAL(18,6) NULL COMMENT '생활 연료비 = 실적 − 제지 (화면 계산값, 연료비 요약이 읽음)',
    FUEL_VS_PLAN         DECIMAL(18,6) NULL COMMENT '회계비용 대비 증감(직접 입력)',
    PAPER_PROD_TON       DECIMAL(18,6) NULL COMMENT '제지 제품생산량(톤)',
    TISSUE_PROD_TON      DECIMAL(18,6) NULL COMMENT '화장지 제품생산량(톤)',
    STEAM_PROD_TON       DECIMAL(18,6) NULL COMMENT '보조: 스팀생산량(톤)',
    LNG_BURNER_COST      DECIMAL(18,6) NULL COMMENT '보조: LNG 직버너 사용 비용(백만원)',
    COMPOSITE_NET        DECIMAL(18,6) NULL COMMENT '참고: 복합 = 스팀구매 − 차감비 일부 (화면 계산값, 연료비 요약이 읽음)',
    ZESCO_NET            DECIMAL(18,6) NULL COMMENT '참고: 제스코 = 스팀구매 − 상계비 − 페널티 − 선급금 (화면 계산값)',
    CREATED_AT           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY           BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT           DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY           BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN           CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT           DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY           BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (ACCOUNTING_PLAN_ID),
    CONSTRAINT UK_STEAM_ENERGY_ACCOUNTING_PLAN_YM UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 회계비용(연료비) 계획 — 연/월별 (백만원, 생산량 톤)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_ENERGY_ACCOUNTING_PLAN_DELETED_YN ON steam_energy_accounting_plan (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_energy_fuel_plan (
    FUEL_PLAN_ID        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '연료비 사업계획 ID (PK)',
    YEAR_NO             INT           NOT NULL COMMENT '연도',
    MONTH_NO            INT           NOT NULL COMMENT '월 (1~12)',
    PAPER_PROD_TON      DECIMAL(18,6) NULL COMMENT '스팀사용량 계획: 제지 생산량(톤/월)',
    TISSUE_PROD_TON     DECIMAL(18,6) NULL COMMENT '스팀사용량 계획: 화장지 생산량(톤/월)',
    PAPER_UNIT          DECIMAL(18,6) NULL COMMENT '스팀사용량 계획: 제지 생산원단위(톤/톤·제품)',
    TISSUE_UNIT         DECIMAL(18,6) NULL COMMENT '스팀사용량 계획: 화장지 생산원단위(톤/톤·제품)',
    PAPER_TOTAL_STEAM   DECIMAL(18,6) NULL COMMENT '제지 총 사용량(톤/월) (화면 계산값, 연료비 요약이 읽음)',
    TISSUE_TOTAL_STEAM  DECIMAL(18,6) NULL COMMENT '화장지 총 사용량(톤/월) (화면 계산값, 연료비 요약이 읽음)',
    ETC_DISPERSER       DECIMAL(18,6) NULL COMMENT '기타 스팀사용량: 디스퍼져(톤/월)',
    ETC_TOC             DECIMAL(18,6) NULL COMMENT '기타 스팀사용량: TOC 재생설비(톤/월)',
    OPERATING_DAYS      DECIMAL(18,6) NULL COMMENT '가동일수(일)',
    PROD_INCINERATOR    DECIMAL(18,6) NULL COMMENT '스팀생산량: 폐합성소각로(톤/월)',
    PROD_COMPOSITE      DECIMAL(18,6) NULL COMMENT '스팀생산량: 복합보일러(톤/월)',
    PROD_EXT_BOILER     DECIMAL(18,6) NULL COMMENT '스팀생산량: 외부보일러(톤/월)',
    PROD_FLUID          DECIMAL(18,6) NULL COMMENT '스팀생산량: 유동상소각로(톤/월)',
    LNG_COMPOSITE       DECIMAL(18,6) NULL COMMENT '연료사용량: 복합 LNG(㎥/월)',
    LNG_TM3             DECIMAL(18,6) NULL COMMENT '연료사용량: 화장지3 LNG(㎥/월)',
    LNG_TM4             DECIMAL(18,6) NULL COMMENT '연료사용량: 화장지4 LNG(㎥/월)',
    LNG_TM5             DECIMAL(18,6) NULL COMMENT '연료사용량: 화장지5 LNG(㎥/월)',
    LNG_PM3             DECIMAL(18,6) NULL COMMENT '연료사용량: 제지3 LNG(㎥/월)',
    PRICE_LNG           DECIMAL(18,6) NULL COMMENT '연료단가: LNG 단가(원/㎥)',
    PRICE_INCINERATOR   DECIMAL(18,6) NULL COMMENT '운영단가: 소각로 위탁(원/톤)',
    PRICE_COMPOSITE     DECIMAL(18,6) NULL COMMENT '운영단가: 복합 기본(원/톤)',
    PRICE_EXT_BOILER    DECIMAL(18,6) NULL COMMENT '운영단가: 외부 기본(원/톤)',
    PRICE_FLUID         DECIMAL(18,6) NULL COMMENT '운영단가: 유동상소각로(원/톤)',
    OPEX_INCINERATOR    DECIMAL(18,6) NULL COMMENT '보일러 운영비: 소각로 운영비(백만원/월)',
    OPEX_FLUID          DECIMAL(18,6) NULL COMMENT '보일러 운영비: 유동상소각로(백만원/월)',
    PAY_EXT_ADVANCE     DECIMAL(18,6) NULL COMMENT '실지급비: 외부 선급금 차감(백만원/월)',
    PAY_EXT_PENALTY     DECIMAL(18,6) NULL COMMENT '실지급비: 외부 페널티 차감(백만원/월)',
    PAY_INCINERATOR     DECIMAL(18,6) NULL COMMENT '실지급비: 폐합성소각로(백만원/월)',
    PAY_FLUID           DECIMAL(18,6) NULL COMMENT '실지급비: 유동상소각로(백만원/월)',
    CREATED_AT          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY          BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT          DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY          BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN          CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT          DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY          BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (FUEL_PLAN_ID),
    CONSTRAINT UK_STEAM_ENERGY_FUEL_PLAN_YM UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 연료비 사업계획(안) — 연/월별 (스팀 톤, LNG ㎥, 단가 원, 비용 백만원)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_ENERGY_FUEL_PLAN_DELETED_YN ON steam_energy_fuel_plan (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_energy_ratio_boiler (
    RATIO_BOILER_ID           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '연료비 배분비율(보일러별) ID (PK)',
    YEAR_NO                   INT           NOT NULL COMMENT '연도',
    MONTH_NO                  INT           NOT NULL COMMENT '월 (1~12)',
    STEAM_LNG_BOILER          DECIMAL(18,6) NULL COMMENT '스팀생산량: LNG 보일러(톤)',
    STEAM_COMPOSITE           DECIMAL(18,6) NULL COMMENT '스팀생산량: 복합보일러(톤)',
    STEAM_INCINERATOR         DECIMAL(18,6) NULL COMMENT '스팀생산량: 폐합성소각로(톤)',
    STEAM_FLUID               DECIMAL(18,6) NULL COMMENT '스팀생산량: 유동상소각로(톤)',
    STEAM_EXT_BOILER          DECIMAL(18,6) NULL COMMENT '스팀생산량: 외부보일러(톤)',
    FLUID_VENT_TON            DECIMAL(18,6) NULL COMMENT '유동상 밴트량(톤)',
    COMPOSITE_PENALTY_TON     DECIMAL(18,6) NULL COMMENT '복합 페널티량(톤)',
    COMPOSITE_GUARANTEE_TON   DECIMAL(18,6) NULL COMMENT '복합 보증량(톤)',
    COST_LNG_BOILER           DECIMAL(18,6) NULL COMMENT '보일러 연료비: LNG 보일러(백만원)',
    COST_COMPOSITE            DECIMAL(18,6) NULL COMMENT '보일러 연료비: 복합보일러(LNG·페널티 포함) (인보이스 연동 계산값 저장)',
    COST_INCINERATOR          DECIMAL(18,6) NULL COMMENT '보일러 연료비: 폐합성소각로 (마감비교 연동값 저장)',
    COST_FLUID                DECIMAL(18,6) NULL COMMENT '보일러 연료비: 유동상소각로(잉여포함) (마감비교 연동값 저장)',
    COST_EXT_BOILER           DECIMAL(18,6) NULL COMMENT '보일러 연료비: 외부보일러 (마감비교 연동값 저장)',
    FLUID_INTERNAL_VENT_COST  DECIMAL(18,6) NULL COMMENT '유동상 소내사용 + 밴트스팀 비용(백만원)',
    PRICE_LNG_BOILER          DECIMAL(18,6) NULL COMMENT '스팀단가: LNG 보일러(천원/톤) (화면 계산값, 연료비 요약이 읽음)',
    PRICE_COMPOSITE           DECIMAL(18,6) NULL COMMENT '스팀단가: 복합보일러(천원/톤) (화면 계산값)',
    PRICE_INCINERATOR         DECIMAL(18,6) NULL COMMENT '스팀단가: 폐합성소각로(천원/톤) (화면 계산값)',
    PRICE_FLUID               DECIMAL(18,6) NULL COMMENT '스팀단가: 유동상소각로(천원/톤) (화면 계산값)',
    PRICE_EXT_BOILER          DECIMAL(18,6) NULL COMMENT '스팀단가: 외부보일러(천원/톤) (화면 계산값)',
    CREATED_AT                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY                BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT                DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY                BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN                CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT                DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY                BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (RATIO_BOILER_ID),
    CONSTRAINT UK_STEAM_ENERGY_RATIO_BOILER_YM UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 연료비 배분비율 — 보일러별 스팀생산량·연료비·스팀단가 (연/월별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_ENERGY_RATIO_BOILER_DELETED_YN ON steam_energy_ratio_boiler (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_energy_ratio_machine (
    RATIO_MACHINE_ID         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '연료비 배분비율(호기별) ID (PK)',
    YEAR_NO                  INT           NOT NULL COMMENT '연도',
    MONTH_NO                 INT           NOT NULL COMMENT '월 (1~12)',
    FUEL_PM2                 DECIMAL(18,6) NULL COMMENT '호기별 연료비배분: 제지 2호기(백만원)',
    FUEL_PM3                 DECIMAL(18,6) NULL COMMENT '호기별 연료비배분: 제지 3호기(백만원)',
    FUEL_PAPER_SUBTOTAL      DECIMAL(18,6) NULL COMMENT '호기별 연료비배분: 제지 소계 (화면 계산값, 마감비교·요약이 읽음)',
    FUEL_TM3                 DECIMAL(18,6) NULL COMMENT '호기별 연료비배분: 화장지 3호기(백만원)',
    FUEL_TM4                 DECIMAL(18,6) NULL COMMENT '호기별 연료비배분: 화장지 4호기(백만원)',
    FUEL_TM5                 DECIMAL(18,6) NULL COMMENT '호기별 연료비배분: 화장지 5호기(백만원)',
    FUEL_TISSUE_SUBTOTAL     DECIMAL(18,6) NULL COMMENT '호기별 연료비배분: 화장지 소계 (화면 계산값, 마감비교가 읽음)',
    FUEL_STEAM_PROD_TON      DECIMAL(18,6) NULL COMMENT '호기별 연료비배분: 스팀생산량(톤)',
    USAGE_PM2                DECIMAL(18,6) NULL COMMENT '스팀사용량: 제지 2호기(톤)',
    USAGE_PM3                DECIMAL(18,6) NULL COMMENT '스팀사용량: 제지 3호기(톤)',
    USAGE_TM3                DECIMAL(18,6) NULL COMMENT '스팀사용량: 화장지 3호기(톤)',
    USAGE_TM4                DECIMAL(18,6) NULL COMMENT '스팀사용량: 화장지 4호기(톤)',
    USAGE_TM5                DECIMAL(18,6) NULL COMMENT '스팀사용량: 화장지 5호기(톤)',
    USAGE_TOTAL_PROD_STEAM   DECIMAL(18,6) NULL COMMENT '스팀사용량: 총 생산스팀량(톤)',
    USAGE_ETC                DECIMAL(18,6) NULL COMMENT '스팀사용량: 기타 사용량(톤) = 총 생산스팀량 − 합계 (계산 가능하면 계산값, 아니면 입력)',
    PROD_PM2_KG              DECIMAL(18,6) NULL COMMENT '제품생산량: 제지2(kg)',
    PROD_PM3_KG              DECIMAL(18,6) NULL COMMENT '제품생산량: 제지3(kg)',
    PROD_PAPER_SUBTOTAL_KG   DECIMAL(18,6) NULL COMMENT '제품생산량: 제지 소계(kg) (화면 계산값, 마감비교·요약이 읽음)',
    PROD_TM3_KG              DECIMAL(18,6) NULL COMMENT '제품생산량: 화장지3(kg)',
    PROD_TM4_KG              DECIMAL(18,6) NULL COMMENT '제품생산량: 화장지4(kg)',
    PROD_TM5_KG              DECIMAL(18,6) NULL COMMENT '제품생산량: 화장지5(kg)',
    PROD_TISSUE_SUBTOTAL_KG  DECIMAL(18,6) NULL COMMENT '제품생산량: 화장지 소계(kg) (화면 계산값, 마감비교·요약이 읽음)',
    CREATED_AT               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY               BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT               DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY               BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN               CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT               DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY               BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (RATIO_MACHINE_ID),
    CONSTRAINT UK_STEAM_ENERGY_RATIO_MACHINE_YM UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 연료비 배분비율 — 호기별 연료비 배분·스팀사용량·제품생산량 (연/월별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_ENERGY_RATIO_MACHINE_DELETED_YN ON steam_energy_ratio_machine (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_energy_ratio_burner (
    RATIO_BURNER_ID           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '연료비 배분비율(버너 LNG) ID (PK)',
    YEAR_NO                   INT           NOT NULL COMMENT '연도',
    MONTH_NO                  INT           NOT NULL COMMENT '월 (1~12)',
    LNG_PM3                   DECIMAL(18,6) NULL COMMENT '버너 LNG 사용량: 제지3(㎥)',
    LNG_TM3                   DECIMAL(18,6) NULL COMMENT '버너 LNG 사용량: 화장지3(㎥)',
    LNG_TM4                   DECIMAL(18,6) NULL COMMENT '버너 LNG 사용량: 화장지4(㎥)',
    LNG_TM5                   DECIMAL(18,6) NULL COMMENT '버너 LNG 사용량: 화장지5(㎥)',
    LNG_PRICE_PREV_YEAR       DECIMAL(18,6) NULL COMMENT '버너 LNG 비용: 전년 LNG 단가(원/㎥)',
    LNG_PRICE                 DECIMAL(18,6) NULL COMMENT '버너 LNG 비용: 당년 LNG 단가(원/㎥)',
    LNG_PRICE_CHANGE_RATE     DECIMAL(18,6) NULL COMMENT '버너 LNG 비용: 증감률 (두 단가가 있으면 계산값, 아니면 입력)',
    LNG_COST_TOTAL            DECIMAL(18,6) NULL COMMENT '버너 LNG 비용 합계(백만원) (화면 계산값, 마감비교·요약이 읽음)',
    LNG_UNIT_PM3              DECIMAL(18,6) NULL COMMENT '버너 LNG 사용량 원단위: 제지3(㎥/제품톤) (화면 계산값, 요약이 읽음)',
    LNG_UNIT_TM3              DECIMAL(18,6) NULL COMMENT '버너 LNG 사용량 원단위: 화장지3 (화면 계산값)',
    LNG_UNIT_TM4              DECIMAL(18,6) NULL COMMENT '버너 LNG 사용량 원단위: 화장지4 (화면 계산값)',
    LNG_UNIT_TM5              DECIMAL(18,6) NULL COMMENT '버너 LNG 사용량 원단위: 화장지5 (화면 계산값)',
    LNG_UNIT_TISSUE_SUBTOTAL  DECIMAL(18,6) NULL COMMENT '버너 LNG 사용량 원단위: 화장지 소계 (화면 계산값)',
    CREATED_AT                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY                BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT                DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY                BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN                CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT                DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY                BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (RATIO_BURNER_ID),
    CONSTRAINT UK_STEAM_ENERGY_RATIO_BURNER_YM UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 연료비 배분비율 — 버너 LNG 사용량·단가·비용·원단위 (연/월별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_ENERGY_RATIO_BURNER_DELETED_YN ON steam_energy_ratio_burner (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_energy_ratio_steam_unit (
    RATIO_STEAM_UNIT_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '스팀사용량 원단위(기타 포함) ID (PK)',
    YEAR_NO               INT           NOT NULL COMMENT '연도',
    MONTH_NO              INT           NOT NULL COMMENT '월 (1~12)',
    UNIT_PM2              DECIMAL(18,6) NULL COMMENT '제지2 (화면 계산값)',
    UNIT_PM3              DECIMAL(18,6) NULL COMMENT '제지3 (화면 계산값)',
    UNIT_PAPER_SUBTOTAL   DECIMAL(18,6) NULL COMMENT '제지 소계 (화면 계산값)',
    UNIT_TM3              DECIMAL(18,6) NULL COMMENT '화장지3 (화면 계산값)',
    UNIT_TM4              DECIMAL(18,6) NULL COMMENT '화장지4 (화면 계산값)',
    UNIT_TM5              DECIMAL(18,6) NULL COMMENT '화장지5 (화면 계산값)',
    UNIT_TISSUE_SUBTOTAL  DECIMAL(18,6) NULL COMMENT '화장지 소계 (화면 계산값)',
    CREATED_AT            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY            BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT            DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY            BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN            CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT            DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY            BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (RATIO_STEAM_UNIT_ID),
    CONSTRAINT UK_STEAM_ENERGY_RATIO_STEAM_UNIT_YM UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 스팀사용량 원단위(톤·스팀/톤·제품, 기타 스팀량 포함) — 화면 계산값 저장, 다른 사이트와 공유';
CREATE INDEX IF NOT EXISTS IDX_STEAM_ENERGY_RATIO_STEAM_UNIT_DELETED_YN ON steam_energy_ratio_steam_unit (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_energy_ratio_ref2019 (
    RATIO_REF2019_ID       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '연료비 배분비율(2019 참고) ID (PK)',
    YEAR_NO                INT           NOT NULL COMMENT '연도',
    MONTH_NO               INT           NOT NULL COMMENT '월 (1~12)',
    T18_PM1                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀생산량: 제지 1호기',
    T18_PM2                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀생산량: 제지 2호기',
    T18_PM3                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀생산량: 제지 3호기',
    T18_PAPER_SUBTOTAL     DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀생산량: 제지 소계',
    T18_TM1                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀생산량: 화장지 1호기',
    T18_TM3                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀생산량: 화장지 3호기',
    T18_TM4                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀생산량: 화장지 4호기',
    T18_TM5                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀생산량: 화장지 5호기',
    T18_TISSUE_SUBTOTAL    DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀생산량: 화장지 소계',
    T18_TOTAL              DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀생산량: 합계',
    T19_PM1                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀사용비율: 제지 1호기',
    T19_PM2                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀사용비율: 제지 2호기',
    T19_PM3                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀사용비율: 제지 3호기',
    T19_PAPER_SUBTOTAL     DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀사용비율: 제지 소계',
    T19_TM1                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀사용비율: 화장지 1호기',
    T19_TM3                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀사용비율: 화장지 3호기',
    T19_TM4                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀사용비율: 화장지 4호기',
    T19_TM5                DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀사용비율: 화장지 5호기',
    T19_TISSUE_SUBTOTAL    DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀사용비율: 화장지 소계',
    T19_TOTAL              DECIMAL(18,6) NULL COMMENT '2019 호기별 스팀사용비율: 합계',
    T20_PM1                DECIMAL(18,6) NULL COMMENT '2019 호기별 에너지비용 배분: 제지 1호기',
    T20_PM2                DECIMAL(18,6) NULL COMMENT '2019 호기별 에너지비용 배분: 제지 2호기',
    T20_PM3                DECIMAL(18,6) NULL COMMENT '2019 호기별 에너지비용 배분: 제지 3호기',
    T20_PAPER_SUBTOTAL     DECIMAL(18,6) NULL COMMENT '2019 호기별 에너지비용 배분: 제지 소계',
    T20_TM1                DECIMAL(18,6) NULL COMMENT '2019 호기별 에너지비용 배분: 화장지 1호기',
    T20_TM3                DECIMAL(18,6) NULL COMMENT '2019 호기별 에너지비용 배분: 화장지 3호기',
    T20_TM4                DECIMAL(18,6) NULL COMMENT '2019 호기별 에너지비용 배분: 화장지 4호기',
    T20_TM5                DECIMAL(18,6) NULL COMMENT '2019 호기별 에너지비용 배분: 화장지 5호기',
    T20_TISSUE_SUBTOTAL    DECIMAL(18,6) NULL COMMENT '2019 호기별 에너지비용 배분: 화장지 소계',
    T20_TOTAL              DECIMAL(18,6) NULL COMMENT '2019 호기별 에너지비용 배분: 합계',
    T20_TOTAL_ENERGY_COST  DECIMAL(18,6) NULL COMMENT '2019 호기별 에너지비용 배분: 총 에너지비용',
    CREATED_AT             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY             BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT             DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY             BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN             CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT             DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY             BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (RATIO_REF2019_ID),
    CONSTRAINT UK_STEAM_ENERGY_RATIO_REF2019_YM UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 연료비 배분비율 — 2019 호기별 참고 표(스팀생산량·사용비율·에너지비용 배분), 월별 입력';
CREATE INDEX IF NOT EXISTS IDX_STEAM_ENERGY_RATIO_REF2019_DELETED_YN ON steam_energy_ratio_ref2019 (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_energy_month_compare (
    MONTH_COMPARE_ID       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '회계비용 마감비교 ID (PK)',
    YEAR_NO                INT           NOT NULL COMMENT '연도',
    MONTH_NO               INT           NOT NULL COMMENT '월 (1~12)',
    ZESCO_STEAM_BUY        DECIMAL(18,6) NULL COMMENT '연료비: 제스코 스팀구매(백만원)',
    LNG_BUY                DECIMAL(18,6) NULL COMMENT '연료비: LNG 구매(백만원)',
    ZESCO_OFFSET           DECIMAL(18,6) NULL COMMENT '차감비: 제스코 상계비(유틸리티)',
    ZESCO_PENALTY          DECIMAL(18,6) NULL COMMENT '차감비: 제스코 페널티',
    FUEL_PLAN              DECIMAL(18,6) NULL COMMENT '계획: 연료비(회계비용) SG 계획금액(백만원)',
    INCINERATOR_STEAM_BUY  DECIMAL(18,6) NULL COMMENT '연료비: 폐합성소각로 스팀구매 (도급내역 연동값 저장, 배분비율이 읽음)',
    FLUID_STEAM_BUY        DECIMAL(18,6) NULL COMMENT '연료비: 유동상소각로 스팀구매 (유동상 운영내역 연동값 저장, 배분비율이 읽음)',
    FUEL_ACTUAL            DECIMAL(18,6) NULL COMMENT '실적: 연료비(회계비용) 실적 (화면 계산값, 배분비율이 읽음)',
    FUEL_STEAM_UNIT        DECIMAL(18,6) NULL COMMENT '보조: 스팀톤당 연료비(천원/톤) (화면 계산값, 배분비율이 읽음)',
    CREATED_AT             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY             BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT             DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY             BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN             CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT             DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY             BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (MONTH_COMPARE_ID),
    CONSTRAINT UK_STEAM_ENERGY_MONTH_COMPARE_YM UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 월별 회계비용 마감 vs 실마감 — 입력값과 다른 화면이 읽는 계산값 (연/월별, 백만원)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_ENERGY_MONTH_COMPARE_DELETED_YN ON steam_energy_month_compare (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_energy_month_compare_note (
    MONTH_COMPARE_NOTE_ID BIGINT        NOT NULL AUTO_INCREMENT COMMENT '회계비용 마감비교 비고 ID (PK)',
    YEAR_NO           INT           NOT NULL COMMENT '연도',
    NOTE_FUEL         VARCHAR(500)  NULL COMMENT '비고: 연료비',
    NOTE_DEDUCT       VARCHAR(500)  NULL COMMENT '비고: 차감비',
    NOTE_ACTUAL       VARCHAR(500)  NULL COMMENT '비고: 실적',
    NOTE_STEAM_RATIO  VARCHAR(500)  NULL COMMENT '비고: 스팀사용비율',
    NOTE_PLAN         VARCHAR(500)  NULL COMMENT '비고: 계획',
    NOTE_PROD         VARCHAR(500)  NULL COMMENT '비고: 생산량',
    NOTE_PRODUCT      VARCHAR(500)  NULL COMMENT '비고: 제품생산량',
    NOTE_UNIT         VARCHAR(500)  NULL COMMENT '비고: 원단위',
    NOTE_PROFIT       VARCHAR(500)  NULL COMMENT '비고: 손익',
    NOTE_AUX          VARCHAR(500)  NULL COMMENT '비고: 보조 분석',
    CREATED_AT        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY        BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT        DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY        BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN        CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT        DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY        BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (MONTH_COMPARE_NOTE_ID),
    CONSTRAINT UK_STEAM_ENERGY_MONTH_COMPARE_NOTE_YEAR UNIQUE (YEAR_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 월별 회계비용 마감비교 — 구분별 비고 (연도별 한 행)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_ENERGY_MONTH_COMPARE_NOTE_DELETED_YN ON steam_energy_month_compare_note (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_energy_fuel_summary (
    FUEL_SUMMARY_ID           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '연료비 요약 ID (PK)',
    YEAR_NO                   INT           NOT NULL COMMENT '연도',
    MONTH_NO                  INT           NOT NULL COMMENT '월 (1~12)',
    T1_PM2_PROD               DECIMAL(18,6) NULL COMMENT '호기별 손익분석 직접입력: 제지 2호기 제품생산량(톤)',
    T1_PM2_FUEL               DECIMAL(18,6) NULL COMMENT '호기별 손익분석 직접입력: 제지 2호기 총연료비용(백만원)',
    T1_PM3_PROD               DECIMAL(18,6) NULL COMMENT '호기별 손익분석 직접입력: 제지 3호기 제품생산량(톤)',
    T1_PM3_FUEL               DECIMAL(18,6) NULL COMMENT '호기별 손익분석 직접입력: 제지 3호기 총연료비용(백만원)',
    T1_TM3_PROD               DECIMAL(18,6) NULL COMMENT '호기별 손익분석 직접입력: 화장지 3호기 제품생산량(톤)',
    T1_TM3_FUEL               DECIMAL(18,6) NULL COMMENT '호기별 손익분석 직접입력: 화장지 3호기 총연료비용(백만원)',
    T1_TM4_PROD               DECIMAL(18,6) NULL COMMENT '호기별 손익분석 직접입력: 화장지 4호기 제품생산량(톤)',
    T1_TM4_FUEL               DECIMAL(18,6) NULL COMMENT '호기별 손익분석 직접입력: 화장지 4호기 총연료비용(백만원)',
    T1_TM5_PROD               DECIMAL(18,6) NULL COMMENT '호기별 손익분석 직접입력: 화장지 5호기 제품생산량(톤)',
    T1_TM5_FUEL               DECIMAL(18,6) NULL COMMENT '호기별 손익분석 직접입력: 화장지 5호기 총연료비용(백만원)',
    T2_LNG_USAGE              DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: LNG 보일러 스팀사용량(톤)',
    T2_LNG_PRICE              DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: LNG 보일러 스팀단가(천원/톤)',
    T2_LNG_COST               DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: LNG 보일러 운영비용(백만원)',
    T2_COMPOSITE_USAGE        DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: 복합보일러 스팀사용량(톤)',
    T2_COMPOSITE_PRICE        DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: 복합보일러 스팀단가(천원/톤)',
    T2_COMPOSITE_COST         DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: 복합보일러 운영비용(백만원)',
    T2_INCINERATOR_USAGE      DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: 폐합성소각로 스팀사용량(톤)',
    T2_INCINERATOR_PRICE      DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: 폐합성소각로 스팀단가(천원/톤)',
    T2_INCINERATOR_COST       DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: 폐합성소각로 운영비용(백만원)',
    T2_FLUID_USAGE            DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: 유동상소각로 스팀사용량(톤)',
    T2_FLUID_PRICE            DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: 유동상소각로 스팀단가(천원/톤)',
    T2_FLUID_COST             DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: 유동상소각로 운영비용(백만원)',
    T2_EXT_USAGE              DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: 외부보일러 스팀사용량(톤)',
    T2_EXT_PRICE              DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: 외부보일러 스팀단가(천원/톤)',
    T2_EXT_COST               DECIMAL(18,6) NULL COMMENT '운영단가 비교 직접입력: 외부보일러 운영비용(백만원)',
    T2_LNG_ELEC               DECIMAL(18,6) NULL COMMENT '운영단가 비교 참고: LNG 보일러 전력비용/전력단가',
    T2_LNG_PENALTY            DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: LNG 보일러 페널티',
    T2_LNG_OVERPRICE          DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: LNG 보일러 초과단가영향',
    T2_COMPOSITE_ELEC         DECIMAL(18,6) NULL COMMENT '운영단가 비교 참고: 복합보일러 전력비용/전력단가',
    T2_COMPOSITE_PENALTY      DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 복합보일러 페널티',
    T2_COMPOSITE_OVERPRICE    DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 복합보일러 초과단가영향',
    T2_INCINERATOR_ELEC       DECIMAL(18,6) NULL COMMENT '운영단가 비교 참고: 폐합성소각로 전력비용/전력단가',
    T2_INCINERATOR_PENALTY    DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 폐합성소각로 페널티',
    T2_INCINERATOR_OVERPRICE  DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 폐합성소각로 초과단가영향',
    T2_FLUID_ELEC             DECIMAL(18,6) NULL COMMENT '운영단가 비교 참고: 유동상소각로 전력비용/전력단가',
    T2_FLUID_PENALTY          DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 유동상소각로 페널티',
    T2_FLUID_OVERPRICE        DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 유동상소각로 초과단가영향',
    T2_EXT_ELEC               DECIMAL(18,6) NULL COMMENT '운영단가 비교 참고: 외부보일러 전력비용/전력단가',
    T2_EXT_PENALTY            DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 외부보일러 페널티',
    T2_EXT_OVERPRICE          DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 외부보일러 초과단가영향',
    T2_SUBTOTAL_ELEC          DECIMAL(18,6) NULL COMMENT '운영단가 비교 참고: 소계 전력비용/전력단가',
    T2_SUBTOTAL_PENALTY       DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 소계 페널티',
    T2_SUBTOTAL_OVERPRICE     DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 소계 초과단가영향',
    T2_BURNER_LNG_ELEC        DECIMAL(18,6) NULL COMMENT '운영단가 비교 참고: 공정 LNG 연료비 전력비용/전력단가',
    T2_BURNER_LNG_PENALTY     DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 공정 LNG 연료비 페널티',
    T2_BURNER_LNG_OVERPRICE   DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 공정 LNG 연료비 초과단가영향',
    T2_TOTAL_ELEC             DECIMAL(18,6) NULL COMMENT '운영단가 비교 참고: 연료비 합계 전력비용/전력단가',
    T2_TOTAL_PENALTY          DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 연료비 합계 페널티',
    T2_TOTAL_OVERPRICE        DECIMAL(18,6) NULL COMMENT '운영단가 비교 특이사항: 연료비 합계 초과단가영향',
    CREATED_AT                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY                BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT                DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY                BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN                CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT                DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY                BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (FUEL_SUMMARY_ID),
    CONSTRAINT UK_STEAM_ENERGY_FUEL_SUMMARY_YM UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 연료비 요약 — 배분비율 기본값을 덮어쓰는 직접 입력값 (연/월별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_ENERGY_FUEL_SUMMARY_DELETED_YN ON steam_energy_fuel_summary (DELETED_YN);


-- ============================================================
-- 9) 복합보일러 그룹 (2026-11 규격 전환, 묶음 3-A)
--    옛 steam_cell_value 의 combo_boiler_production / _downtime / _recovery / _etc 를 대체한다.
--    계산값(T/H, 합계, 소계, 잔량)은 저장하지 않는다. 회수 감소는 한 건 한 행(줄 번호).
-- ============================================================

CREATE TABLE IF NOT EXISTS steam_combo_daily_production (
    COMBO_PRODUCTION_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '복합보일러 스팀생산실적 ID (PK)',
    USAGE_DATE            DATE          NOT NULL COMMENT '일자',
    COMBO_STEAM           DECIMAL(18,6) NULL COMMENT '복합보일러 스팀구매량(T/D)',
    COMBO_CONDENSATE      DECIMAL(18,6) NULL COMMENT '복합보일러 응축수판매량',
    COMBO_MAKEUP          DECIMAL(18,6) NULL COMMENT '복합보일러 보충수판매량',
    COMBO_DEAERATOR       DECIMAL(18,6) NULL COMMENT '복합보일러 탈기기급수량',
    COMBO_PROCESS         DECIMAL(18,6) NULL COMMENT '복합보일러 공정수판매량',
    COMBO_DILUTION        DECIMAL(18,6) NULL COMMENT '복합보일러 희석수판매량',
    EXTERNAL_STEAM        DECIMAL(18,6) NULL COMMENT '외부보일러 스팀구매량(T/D)',
    EXTERNAL_CONDENSATE   DECIMAL(18,6) NULL COMMENT '외부보일러 응축수판매량',
    EXTERNAL_MAKEUP       DECIMAL(18,6) NULL COMMENT '외부보일러 보충수판매량',
    WASTE_STEAM1          DECIMAL(18,6) NULL COMMENT '폐합성 1호 스팀 구입량(T/D)',
    WASTE_STEAM2          DECIMAL(18,6) NULL COMMENT '폐합성 2호 스팀 구입량(T/D) — 비어 있으면 소각로 도급내역 실 스팀량(2호기)을 따른다',
    KN20                  DECIMAL(18,6) NULL COMMENT '깨끗한나라 보일러 20톤(#2) 스팀생산량(T/D)',
    KN50                  DECIMAL(18,6) NULL COMMENT '깨끗한나라 보일러 50톤(#3) 스팀생산량(T/D)',
    FLUIDIZED_STEAM       DECIMAL(18,6) NULL COMMENT '유동상 스팀 생산량(T/D)',
    FLUIDIZED_CONDENSATE  DECIMAL(18,6) NULL COMMENT '유동상 응축수',
    RUNTIME20             DECIMAL(18,6) NULL COMMENT '20톤(#2) 가동시간(H/D)',
    RUNTIME50             DECIMAL(18,6) NULL COMMENT '50톤(#3) 가동시간(H/D)',
    RUNTIME_FLUIDIZED     DECIMAL(18,6) NULL COMMENT '유동상 가동시간(H/D)',
    CREATED_AT            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY            BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT            DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY            BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN            CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT            DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY            BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (COMBO_PRODUCTION_ID),
    CONSTRAINT UK_STEAM_COMBO_DAILY_PRODUCTION_KEY UNIQUE (USAGE_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 복합보일러 스팀생산실적 (일자별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_COMBO_DAILY_PRODUCTION_DELETED_YN ON steam_combo_daily_production (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_combo_daily_downtime (
    COMBO_DOWNTIME_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '복합보일러 운휴·디스퍼져 ID (PK)',
    USAGE_DATE         DATE          NOT NULL COMMENT '일자',
    CHANGE_COMBO       VARCHAR(500)  NULL COMMENT '변동사항(복합보일러)',
    CHANGE_JESCO       VARCHAR(500)  NULL COMMENT '변동사항(제스코, 유동상 보일러)',
    DOWN1              DECIMAL(18,6) NULL COMMENT '#1호기 운휴시간(분)',
    DOWN2              DECIMAL(18,6) NULL COMMENT '#2호기 운휴시간(분)',
    DISPERSER_STEAM    DECIMAL(18,6) NULL COMMENT '디스퍼져 스팀량',
    RUNTIME_MIN        DECIMAL(18,6) NULL COMMENT '디스퍼져 가동시간(분)',
    VENT_PRODUCTION    DECIMAL(18,6) NULL COMMENT '벤트 손실: 생산량 FIT-301 (엑셀 왕복용, 화면 없음)',
    VENT_INTERNAL      DECIMAL(18,6) NULL COMMENT '벤트 손실: 소내 사용량 추정 (엑셀 왕복용, 화면 없음)',
    VENT_USAGE         DECIMAL(18,6) NULL COMMENT '벤트 손실: 사용량 FIT-304 (엑셀 왕복용, 화면 없음)',
    CREATED_AT         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY         BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT         DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY         BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN         CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT         DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY         BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (COMBO_DOWNTIME_ID),
    CONSTRAINT UK_STEAM_COMBO_DAILY_DOWNTIME_KEY UNIQUE (USAGE_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 복합보일러 변동사항·운휴시간·디스퍼져·벤트 (일자별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_COMBO_DAILY_DOWNTIME_DELETED_YN ON steam_combo_daily_downtime (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_combo_monthly_price (
    COMBO_PRICE_ID         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '복합보일러 월 연료단가 ID (PK)',
    YEAR_NO                INT           NOT NULL COMMENT '연도',
    MONTH_NO               INT           NOT NULL COMMENT '월 (1~12)',
    FUEL_PRICE_LNG_BOILER  DECIMAL(18,6) NULL COMMENT '연료단가: LNG 보일러',
    FUEL_PRICE_FLUIDIZED   DECIMAL(18,6) NULL COMMENT '연료단가: 유동상',
    FUEL_PRICE_COMBO       DECIMAL(18,6) NULL COMMENT '연료단가: 복합보일러',
    FUEL_PRICE_WASTE       DECIMAL(18,6) NULL COMMENT '연료단가: 폐합성',
    FUEL_PRICE_EXTERNAL    DECIMAL(18,6) NULL COMMENT '연료단가: 외부보일러',
    VENT_LOSS_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '벤트 손실비용 단가(전월) (엑셀 왕복용, 화면 없음)',
    CREATED_AT             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY             BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT             DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY             BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN             CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT             DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY             BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (COMBO_PRICE_ID),
    CONSTRAINT UK_STEAM_COMBO_MONTHLY_PRICE_KEY UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 복합보일러 월 연료단가·벤트 손실 단가 (연/월별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_COMBO_MONTHLY_PRICE_DELETED_YN ON steam_combo_monthly_price (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_combo_recovery_loss (
    COMBO_RECOVERY_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '복합보일러 스팀 회수 감소 ID (PK)',
    YEAR_NO            INT           NOT NULL COMMENT '연도',
    MONTH_NO           INT           NOT NULL COMMENT '월 (1~12)',
    LINE_NO            INT           NOT NULL COMMENT '줄 번호 (1부터, 그 달의 감소 건 순번)',
    LOSS_DATE          VARCHAR(50)   NULL COMMENT '날짜 (입력한 글자 그대로, 예: 3/26)',
    LOSS_TIME          VARCHAR(100)  NULL COMMENT '스팀회수 감소시간 (입력한 글자 그대로, 예: 17:00~18:25)',
    LOSS_NOTE          VARCHAR(500)  NULL COMMENT '내용',
    LOSS_STEAM_RATE    DECIMAL(18,6) NULL COMMENT '스팀 감소량 기준(T/H)',
    LOSS_BASE_RATE     DECIMAL(18,6) NULL COMMENT '기준(T/H)',
    LOSS_MINUTES       DECIMAL(18,6) NULL COMMENT '감소시간(분)',
    CREATED_AT         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY         BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT         DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY         BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN         CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT         DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY         BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (COMBO_RECOVERY_ID),
    CONSTRAINT UK_STEAM_COMBO_RECOVERY_LOSS_KEY UNIQUE (YEAR_NO, MONTH_NO, LINE_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 복합보일러 스팀 회수 감소 내역 (연/월 · 줄 번호별, 한 건 한 행)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_COMBO_RECOVERY_LOSS_DELETED_YN ON steam_combo_recovery_loss (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_combo_plan (
    COMBO_PLAN_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '복합보일러 연간 계획 ID (PK)',
    YEAR_NO        INT           NOT NULL COMMENT '연도',
    TARGET_YM      VARCHAR(7)    NOT NULL COMMENT '대상 월 (YYYY-MM, 기준연도 화면의 월 칸)',
    COMBO_BASIC    DECIMAL(18,6) NULL COMMENT '복합보일러 계획: 기본 (전년 6월~당해 8월)',
    COMBO_SPECIAL  DECIMAL(18,6) NULL COMMENT '복합보일러 계획: 특약',
    COMBO_ACTUAL   DECIMAL(18,6) NULL COMMENT '복합보일러 계획: 실적',
    JESCO_PLAN     DECIMAL(18,6) NULL COMMENT '제스코 계획: 계획 (전년 5월~당해 5월)',
    JESCO_ACTUAL   DECIMAL(18,6) NULL COMMENT '제스코 계획: 실적',
    LNG_BOILER     DECIMAL(18,6) NULL COMMENT 'LNG사용량: 보일러 (당해 1~12월)',
    LNG_COMBO      DECIMAL(18,6) NULL COMMENT 'LNG사용량: 복합',
    CREATED_AT     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY     BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT     DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY     BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN     CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT     DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY     BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (COMBO_PLAN_ID),
    CONSTRAINT UK_STEAM_COMBO_PLAN_KEY UNIQUE (YEAR_NO, TARGET_YM)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 복합보일러·제스코 연간 계획과 LNG 사용량 (기준연도 · 대상 월별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_COMBO_PLAN_DELETED_YN ON steam_combo_plan (DELETED_YN);


-- ============================================================
-- 10) 소각로 위탁 그룹 (2026-11 규격 전환, 묶음 3-B)
--    옛 steam_cell_value 의 incinerator_contract / _plan_vs / _operation / _total_cost 를 대체한다.
--    계산값(운영비용·운휴비용·소계·달성율·증감)은 저장하지 않는다. 전년대비는 저장하는 값이 없다.
-- ============================================================

CREATE TABLE IF NOT EXISTS steam_incinerator_contract_daily (
    CONTRACT_DAILY_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '소각로 도급내역 ID (PK)',
    USAGE_DATE         DATE          NOT NULL COMMENT '일자',
    U1_STEAM           DECIMAL(18,6) NULL COMMENT '결산 1호기 스팀량(톤/일)',
    U1_UNIT            DECIMAL(18,6) NULL COMMENT '결산 1호기 운영단가(원/톤)',
    U1_RUNTIME         DECIMAL(18,6) NULL COMMENT '결산 1호기 운휴시간(Hr) — 비어 있으면 화면·집계에서 24 로 본다',
    U1_FIXED           DECIMAL(18,6) NULL COMMENT '결산 1호기 운휴시간당 고정비(원/시간) — 단가 입력 waste1_fixed_hr 이 그 달 모든 날에 기록',
    U2_STEAM           DECIMAL(18,6) NULL COMMENT '결산 2호기 스팀량(톤/일) — 실 2호기 스팀량과 상한 336 중 작은 값 (화면 계산값, 과거 값 보존)',
    U2_UNIT            DECIMAL(18,6) NULL COMMENT '결산 2호기 운영단가(원/톤) — 단가 입력 waste2_steam',
    U2_RUNTIME         DECIMAL(18,6) NULL COMMENT '결산 2호기 운휴시간(Hr)',
    U2_FIXED           DECIMAL(18,6) NULL COMMENT '결산 2호기 운휴시간당 고정비(원/시간) — 단가 입력 waste2_fixed_hr',
    REAL_STEAM1        DECIMAL(18,6) NULL COMMENT '실 스팀량 기준 1호기 스팀량(톤/일)',
    REAL_UNIT1         DECIMAL(18,6) NULL COMMENT '실 스팀량 기준 1호기 단가(원/톤)',
    REAL_STEAM2        DECIMAL(18,6) NULL COMMENT '실 스팀량 기준 2호기 스팀량(톤/일) — 복합보일러 스팀생산실적의 폐합성 2호 기본값',
    REAL_UNIT2         DECIMAL(18,6) NULL COMMENT '실 스팀량 기준 2호기 단가(원/톤) — 단가 입력 waste2_steam',
    CREATED_AT         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY         BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT         DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY         BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN         CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT         DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY         BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (CONTRACT_DAILY_ID),
    CONSTRAINT UK_STEAM_INCINERATOR_CONTRACT_DAILY_KEY UNIQUE (USAGE_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 폐합성수지 소각로 도급내역 — 결산 1·2호기와 실 스팀량 기준 (일자별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_INCINERATOR_CONTRACT_DAILY_DELETED_YN ON steam_incinerator_contract_daily (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_incinerator_plan_monthly (
    PLAN_MONTHLY_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '소각로 사업계획대비 ID (PK)',
    YEAR_NO           INT           NOT NULL COMMENT '연도',
    MONTH_NO          INT           NOT NULL COMMENT '월 (1~12)',
    CONTRACT_LABOR    DECIMAL(18,6) NULL COMMENT '도급내역: 인건비(원/월)',
    LABOR_PLAN        DECIMAL(18,6) NULL COMMENT '인건비 계획(천원)',
    LABOR_ACTUAL      DECIMAL(18,6) NULL COMMENT '인건비 실적(천원)',
    OPERATION_PLAN    DECIMAL(18,6) NULL COMMENT '운영비 계획(천원)',
    OPERATION_ACTUAL  DECIMAL(18,6) NULL COMMENT '운영비 실적(천원) — 도급내역으로 계산할 수 없는 달에 쓰는 직접 입력·엑셀 값',
    TOTAL_PLAN        DECIMAL(18,6) NULL COMMENT '합계 계획(천원) — 직접 입력',
    NOTE_U1           VARCHAR(500)  NULL COMMENT '비고: #1 폐합성수지소각로',
    NOTE_U2           VARCHAR(500)  NULL COMMENT '비고: #2 폐합성수지소각로',
    BRK_MONTH_PLAN    DECIMAL(18,6) NULL COMMENT '위탁운영비 이달 계획(백만원)',
    BRK_MONTH_ACTUAL  DECIMAL(18,6) NULL COMMENT '위탁운영비 이달 실적(백만원)',
    BRK_YTD_PLAN      DECIMAL(18,6) NULL COMMENT '위탁운영비 누계 계획(백만원)',
    BRK_YTD_ACTUAL    DECIMAL(18,6) NULL COMMENT '위탁운영비 누계 실적(백만원)',
    BRK_PREV_ACTUAL   DECIMAL(18,6) NULL COMMENT '위탁운영비 전월 실적(백만원)',
    BRK_NOTE          VARCHAR(500)  NULL COMMENT '위탁운영비 비고',
    CREATED_AT        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY        BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT        DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY        BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN        CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT        DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY        BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (PLAN_MONTHLY_ID),
    CONSTRAINT UK_STEAM_INCINERATOR_PLAN_MONTHLY_KEY UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 폐합성수지 소각로 사업계획대비 실적·비고와 도급내역 월 인건비 (연/월별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_INCINERATOR_PLAN_MONTHLY_DELETED_YN ON steam_incinerator_plan_monthly (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_incinerator_operation_monthly (
    OPERATION_MONTHLY_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '소각로 계획대비 가동실적 ID (PK)',
    YEAR_NO               INT           NOT NULL COMMENT '연도',
    MONTH_NO              INT           NOT NULL COMMENT '월 (1~12)',
    U1_PLAN               DECIMAL(18,6) NULL COMMENT '1호기 가동계획(hr)',
    U1_ACTUAL             DECIMAL(18,6) NULL COMMENT '1호기 가동실적(hr) — 총 운영비용 화면이 읽음',
    U2_PLAN               DECIMAL(18,6) NULL COMMENT '2호기 가동계획(hr)',
    U2_ACTUAL             DECIMAL(18,6) NULL COMMENT '2호기 가동실적(hr) — 총 운영비용 화면이 읽음',
    STEAM_PLAN            DECIMAL(18,6) NULL COMMENT '스팀생산량 계획(톤)',
    STEAM_ACTUAL          DECIMAL(18,6) NULL COMMENT '스팀생산량 실적(톤)',
    RATE_PLAN             DECIMAL(24,12) NULL COMMENT '운영단가(위탁) 계획(원/톤)',
    RATE_ACTUAL           DECIMAL(24,12) NULL COMMENT '운영단가(위탁) 실적(원/톤) — 전체 자릿수 보존',
    COST_PLAN             DECIMAL(18,6) NULL COMMENT '위탁운영비용 계획(천원)',
    COST_ACTUAL           DECIMAL(18,6) NULL COMMENT '위탁운영비용 실적(천원)',
    CREATED_AT            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY            BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT            DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY            BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN            CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT            DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY            BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (OPERATION_MONTHLY_ID),
    CONSTRAINT UK_STEAM_INCINERATOR_OPERATION_MONTHLY_KEY UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 폐합성수지 소각로 계획대비 가동실적 (연/월별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_INCINERATOR_OPERATION_MONTHLY_DELETED_YN ON steam_incinerator_operation_monthly (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_incinerator_cost_monthly (
    COST_MONTHLY_ID         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '소각로 총 운영비용 ID (PK)',
    YEAR_NO                 INT           NOT NULL COMMENT '연도',
    MONTH_NO                INT           NOT NULL COMMENT '월 (1~12)',
    OP_BURN_BURN_1          DECIMAL(18,6) NULL COMMENT '운영현황: 폐기물 실소각량 1호기(톤)',
    OP_BURN_BURN_2          DECIMAL(18,6) NULL COMMENT '운영현황: 폐기물 실소각량 2호기(톤)',
    OP_BURN_RATE_1          DECIMAL(18,6) NULL COMMENT '운영현황: 스팀생산원단위 1호기',
    OP_BURN_RATE_2          DECIMAL(18,6) NULL COMMENT '운영현황: 스팀생산원단위 2호기',
    OP_COST_UNIT            DECIMAL(18,6) NULL COMMENT '위탁운영비: 위탁계약단가(원/톤)',
    OP_COST_DEDUCT          DECIMAL(18,6) NULL COMMENT '위탁운영비: 차감비(천원)',
    SELF_SRF_IN_QTY         DECIMAL(18,6) NULL COMMENT '자체비용: 외부 폐합성수지 입고량(톤)',
    SELF_SRF_IN_REV         DECIMAL(18,6) NULL COMMENT '자체비용: 외부 폐합성수지 수익비용(천원)',
    SELF_SRF_FLOOR_RECYCLE  DECIMAL(18,6) NULL COMMENT '자체비용: 바닥재 재활용(톤)',
    SELF_SRF_FLOOR_BURY     DECIMAL(18,6) NULL COMMENT '자체비용: 바닥재 매립(톤)',
    SELF_SRF_FLY_SOLID      DECIMAL(18,6) NULL COMMENT '자체비용: 비산재 고형화(톤)',
    SELF_SRF_FLY_BURY       DECIMAL(18,6) NULL COMMENT '자체비용: 비산재 매립(톤)',
    SELF_SRF_COST_FLOOR     DECIMAL(18,6) NULL COMMENT '자체비용: 폐기물처리비용 바닥재(천원)',
    SELF_SRF_COST_FLY       DECIMAL(18,6) NULL COMMENT '자체비용: 폐기물처리비용 비산재(천원)',
    SELF_SRF_COST_MISC      DECIMAL(18,6) NULL COMMENT '자체비용: 폐기물처리비용 기타(천원)',
    SELF_UTIL_P1            DECIMAL(18,6) NULL COMMENT '유틸리티: 전력 1호기 사용량(Kw)',
    SELF_UTIL_P2            DECIMAL(18,6) NULL COMMENT '유틸리티: 전력 2호기 사용량(Kw)',
    SELF_UTIL_PRATE         DECIMAL(18,6) NULL COMMENT '유틸리티: 전력단가',
    SELF_UTIL_NAOH_QTY      DECIMAL(18,6) NULL COMMENT '유틸리티: 가성소다 구입량(Kg)',
    SELF_UTIL_NAOH_UNIT     DECIMAL(18,6) NULL COMMENT '유틸리티: 가성소다 단가',
    SELF_UTIL_WATER         DECIMAL(18,6) NULL COMMENT '유틸리티: 용수사용비(천원)',
    SELF_UTIL_WASTE_CHARGE  DECIMAL(18,6) NULL COMMENT '유틸리티: 폐기물처분부담금(천원)',
    SELF_UTIL_ETC           DECIMAL(18,6) NULL COMMENT '유틸리티: 기타(천원)',
    SELF_TOTAL_TOTAL        DECIMAL(18,6) NULL COMMENT '자체비용 합계: 비용합계(천원) — 화면 입력',
    SELF_TOTAL_BURN_RATE    DECIMAL(18,6) NULL COMMENT '자체비용 합계: 처리량당 비용 — 화면 입력',
    SELF_TOTAL_STEAM_RATE   DECIMAL(18,6) NULL COMMENT '자체비용 합계: 스팀톤당 비용 — 화면 입력',
    MAT_OIL_U1              DECIMAL(18,6) NULL COMMENT '원부자재: 부생유 #1 사용량',
    MAT_OIL_U2              DECIMAL(18,6) NULL COMMENT '원부자재: 부생유 #2 사용량',
    MAT_OIL_UNIT            DECIMAL(18,6) NULL COMMENT '원부자재: 부생유 단가',
    MAT_OIL_CK_QTY          DECIMAL(18,6) NULL COMMENT '원부자재: 청관제 구입량',
    MAT_OIL_CK_UNIT         DECIMAL(18,6) NULL COMMENT '원부자재: 청관제 단가',
    MAT_CHEM_U_QTY          DECIMAL(18,6) NULL COMMENT '원부자재: 요소수 구입량',
    MAT_CHEM_U_UNIT         DECIMAL(18,6) NULL COMMENT '원부자재: 요소수 단가',
    MAT_CHEM_S_QTY          DECIMAL(18,6) NULL COMMENT '원부자재: 소금 구입량',
    MAT_CHEM_S_UNIT         DECIMAL(18,6) NULL COMMENT '원부자재: 소금 단가',
    MAT_MISC_A_QTY          DECIMAL(18,6) NULL COMMENT '원부자재: 분말활성탄 구입량',
    MAT_MISC_A_UNIT         DECIMAL(18,6) NULL COMMENT '원부자재: 분말활성탄 단가',
    MAT_MISC_K_QTY          DECIMAL(18,6) NULL COMMENT '원부자재: 크링커제거제 구입량',
    MAT_MISC_K_UNIT         DECIMAL(18,6) NULL COMMENT '원부자재: 크링커제거제 단가',
    CREATED_AT              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY              BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT              DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY              BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN              CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT              DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY              BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (COST_MONTHLY_ID),
    CONSTRAINT UK_STEAM_INCINERATOR_COST_MONTHLY_KEY UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 폐합성수지 소각로 총 운영비용 입력값 (연/월별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_INCINERATOR_COST_MONTHLY_DELETED_YN ON steam_incinerator_cost_monthly (DELETED_YN);


-- ============================================================
-- 11) SRF 보일러 인보이스 그룹 (2026-11 규격 전환, 묶음 3-C)
--    옛 steam_cell_value 의 srf_boiler_invoice_summary / _client / _operation / _monthly / _accumulated / _bsc / _adjustment 를 대체한다.
--    계산값은 저장하지 않는다. 월별 운영내역·누계는 화면의 회계연도(6월~5월)가 아니라 실제 연/월로 저장한다.
-- ============================================================

CREATE TABLE IF NOT EXISTS steam_srf_invoice_summary (
    INVOICE_SUMMARY_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'SRF 인보이스 Summary ID (PK)',
    YEAR_NO              INT           NOT NULL COMMENT '연도',
    MONTH_NO             INT           NOT NULL COMMENT '월 (1~12)',
    BSC_RATE             DECIMAL(18,6) NULL COMMENT '1. BSC ▸ BASE SERVICE CHARGE (Won/ton steam) — 단가 입력 comb_bsc',
    BSC_FACTOR           DECIMAL(18,6) NULL COMMENT 'BSC ESCALATION FACTOR — 단가 입력 comb_bsc_factor',
    AFBSCR_BSC           DECIMAL(18,6) NULL COMMENT 'AFBSCr - BSC (Won/ton steam)',
    BSC_STEAM_AMOUNT     DECIMAL(18,6) NULL COMMENT 'BSC STEAM SUPPLY AMOUNT (tons/month) — 단가 입력 comb_bsc_guarantee',
    ASC_RATE             DECIMAL(18,6) NULL COMMENT '2. ASC ▸ ADDITIONAL SERVICE CHARGE (Won/ton steam) — 단가 입력 comb_asc',
    ASC_FACTOR           DECIMAL(18,6) NULL COMMENT 'ASC ESCALATION FACTOR — 단가 입력 comb_asc_factor',
    ASC_STEAM_AMOUNT     DECIMAL(18,6) NULL COMMENT 'ASC STEAM SUPPLY AMOUNT (tons/month)',
    OTHERS_0             DECIMAL(18,6) NULL COMMENT '3. OTHERS 첫 금액(Won) — 비어 있으면 Client Invoice 의 깨끗한나라←베올리아 1번째 줄(수량×단가)을 따른다',
    OTHERS_1             DECIMAL(18,6) NULL COMMENT '3. OTHERS 1)(Won) — 비어 있으면 Client Invoice 의 깨끗한나라←베올리아 2번째 줄을 따른다',
    OTHERS_2             DECIMAL(18,6) NULL COMMENT '3. OTHERS 2)(Won)',
    OTHERS_3             DECIMAL(18,6) NULL COMMENT '3. OTHERS 3)(Won)',
    PAYABLE_ADJUST       DECIMAL(18,6) NULL COMMENT 'VAT 와 TOTAL AMOUNT PAYABLE 사이 조정 금액(Won)',
    LIQUIDATED_DAMAGE    DECIMAL(18,6) NULL COMMENT 'DEDUCTIONS: LIQUIDATED DAMAGE(Won)',
    ASC_REFUND_ANNUAL    DECIMAL(18,6) NULL COMMENT 'DEDUCTIONS: ASC REFUND(Annual)(Won)',
    UTILITIES_DEDUCTION  DECIMAL(18,6) NULL COMMENT 'DEDUCTIONS: UTILITIES DEDUCTION COSTS(Won) — 비어 있으면 Client Invoice 유틸리티 소계를 따른다',
    OTHER_DEDUCTION_1    DECIMAL(18,6) NULL COMMENT 'OTHER DEDUCTION COSTS (1)(Won) — 비어 있으면 Client Invoice 깨끗한나라→베올리아 1번째 줄을 따른다',
    OTHER_DEDUCTION_2    DECIMAL(18,6) NULL COMMENT 'OTHER DEDUCTION COSTS (2)(Won) — 비어 있으면 Client Invoice 깨끗한나라→베올리아 2번째 줄을 따른다',
    CREATED_AT           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY           BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT           DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY           BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN           CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT           DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY           BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (INVOICE_SUMMARY_ID),
    CONSTRAINT UK_STEAM_SRF_INVOICE_SUMMARY_KEY UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] SRF 보일러 인보이스 Summary(VK) 입력값 (연/월별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_SRF_INVOICE_SUMMARY_DELETED_YN ON steam_srf_invoice_summary (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_srf_invoice_client_item (
    CLIENT_ITEM_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'SRF 인보이스 Client Invoice ID (PK)',
    YEAR_NO         INT           NOT NULL COMMENT '연도',
    MONTH_NO        INT           NOT NULL COMMENT '월 (1~12)',
    SECTION         VARCHAR(20)   NOT NULL COMMENT '구분 (utilities: 유틸리티 10줄 / other-out: 깨끗한나라→베올리아 3줄 / other-in: 깨끗한나라←베올리아 3줄)',
    LINE_NO         INT           NOT NULL COMMENT '줄 번호 (0부터)',
    ITEM_LABEL      VARCHAR(200)  NULL COMMENT '항목 이름 (other-out · other-in 만 입력)',
    UNIT_LABEL      VARCHAR(50)   NULL COMMENT '단위 (other-out · other-in 만 입력)',
    QTY             DECIMAL(18,6) NULL COMMENT '수량',
    UNIT_PRICE      DECIMAL(18,6) NULL COMMENT '단가(원)',
    NOTE            VARCHAR(500)  NULL COMMENT '비고',
    CREATED_AT      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY      BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT      DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY      BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN      CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT      DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY      BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (CLIENT_ITEM_ID),
    CONSTRAINT UK_STEAM_SRF_INVOICE_CLIENT_ITEM_KEY UNIQUE (YEAR_NO, MONTH_NO, SECTION, LINE_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] SRF 보일러 인보이스 Client Invoice 줄 (연/월 · 구분 · 줄 번호별, 한 달 16줄 고정)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_SRF_INVOICE_CLIENT_ITEM_DELETED_YN ON steam_srf_invoice_client_item (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_srf_invoice_operation_daily (
    INVOICE_OPERATION_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'SRF 인보이스 운영내역 ID (PK)',
    USAGE_DATE            DATE          NOT NULL COMMENT '일자',
    POWER_KWH             DECIMAL(18,6) NULL COMMENT '전력량(Kw/D)',
    CREATED_AT            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY            BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT            DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY            BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN            CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT            DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY            BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (INVOICE_OPERATION_ID),
    CONSTRAINT UK_STEAM_SRF_INVOICE_OPERATION_DAILY_KEY UNIQUE (USAGE_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] SRF 보일러 인보이스 운영내역 — 일별 전력량 (나머지 열은 복합보일러 스팀생산실적에서 연동·계산)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_SRF_INVOICE_OPERATION_DAILY_DELETED_YN ON steam_srf_invoice_operation_daily (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_srf_invoice_monthly (
    INVOICE_MONTHLY_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'SRF 인보이스 월별 운영내역 ID (PK)',
    YEAR_NO             INT           NOT NULL COMMENT '연도',
    MONTH_NO            INT           NOT NULL COMMENT '월 (1~12)',
    STEAM_QTY           DECIMAL(18,6) NULL COMMENT '스팀 구매량(ton)',
    STEAM_UNIT          DECIMAL(18,6) NULL COMMENT '스팀 단가(원/ton)',
    POWER_QTY           DECIMAL(18,6) NULL COMMENT '전력 사용량(kwh)',
    POWER_UNIT          DECIMAL(18,6) NULL COMMENT '전력 단가(원)',
    LNG_QTY             DECIMAL(18,6) NULL COMMENT 'LNG 사용량(Nm3)',
    LNG_UNIT            DECIMAL(18,6) NULL COMMENT 'LNG 단가(원)',
    PROCESS_QTY         DECIMAL(18,6) NULL COMMENT '공정수 사용량(m3)',
    PROCESS_UNIT        DECIMAL(18,6) NULL COMMENT '공정수 단가(원)',
    SOFT_QTY            DECIMAL(18,6) NULL COMMENT '청수 사용량(m3)',
    SOFT_UNIT           DECIMAL(18,6) NULL COMMENT '청수 단가(원)',
    SOFTEN_QTY          DECIMAL(18,6) NULL COMMENT '연수 사용량(m3)',
    SOFTEN_UNIT         DECIMAL(18,6) NULL COMMENT '연수 단가(원)',
    LIFE_QTY            DECIMAL(18,6) NULL COMMENT '생활용수 사용량(m3)',
    LIFE_UNIT           DECIMAL(18,6) NULL COMMENT '생활용수 단가(원)',
    ETC_SPECIAL         DECIMAL(18,6) NULL COMMENT '기타: 특약 미달금액(원, +)',
    ETC_BSC7            DECIMAL(18,6) NULL COMMENT '기타: 7차연도 BSC물량 정산금(원, +)',
    ETC_SRF_SHARE       DECIMAL(18,6) NULL COMMENT '기타: SRF이익 분배금(원, −)',
    ETC_INSPECT         DECIMAL(18,6) NULL COMMENT '기타: 보일러 검사수수료(원, −)',
    ETC_OVER7           DECIMAL(18,6) NULL COMMENT '기타: 7차연도 단가 초과 지급분(원, −)',
    ETC_AIR_OVER        DECIMAL(18,6) NULL COMMENT '기타: 대기초과배출부과금(원, −)',
    ETC_WASTE           DECIMAL(18,6) NULL COMMENT '기타: 폐기물 처분부담금(원, −)',
    CREATED_AT          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY          BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT          DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY          BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN          CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT          DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY          BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (INVOICE_MONTHLY_ID),
    CONSTRAINT UK_STEAM_SRF_INVOICE_MONTHLY_KEY UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] SRF 보일러 인보이스 월별 운영내역 (연/월별 — 화면은 6월~다음해 5월을 한 해로 본다)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_SRF_INVOICE_MONTHLY_DELETED_YN ON steam_srf_invoice_monthly (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_srf_invoice_accumulated (
    INVOICE_ACCUMULATED_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'SRF 인보이스 누계 스팀량 ID (PK)',
    YEAR_NO                 INT           NOT NULL COMMENT '연도',
    MONTH_NO                INT           NOT NULL COMMENT '월 (1~12)',
    CONTRACT_VOLUME         DECIMAL(18,6) NULL COMMENT 'Contract Supply Volume(ton)',
    BSC_VOLUME              DECIMAL(18,6) NULL COMMENT 'BSC steam supply volume(ton)',
    ASC_VOLUME              DECIMAL(18,6) NULL COMMENT 'ASC steam supply volume(ton)',
    CREATED_AT              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY              BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT              DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY              BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN              CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT              DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY              BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (INVOICE_ACCUMULATED_ID),
    CONSTRAINT UK_STEAM_SRF_INVOICE_ACCUMULATED_KEY UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] SRF 보일러 인보이스 누계 스팀 공급량 (연/월별 — 화면은 6월~다음해 5월을 한 해로 본다)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_SRF_INVOICE_ACCUMULATED_DELETED_YN ON steam_srf_invoice_accumulated (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_srf_invoice_bsc (
    INVOICE_BSC_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'SRF 인보이스 BSC 단가 ID (PK)',
    YEAR_NO         INT           NOT NULL COMMENT '연도',
    BSC_RATE        DECIMAL(18,6) NULL COMMENT 'Base Service Charge Rate(Won/ton)',
    CREATED_AT      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY      BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT      DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY      BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN      CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT      DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY      BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (INVOICE_BSC_ID),
    CONSTRAINT UK_STEAM_SRF_INVOICE_BSC_KEY UNIQUE (YEAR_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] SRF 보일러 인보이스 Base Service Charge Rate (연도별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_SRF_INVOICE_BSC_DELETED_YN ON steam_srf_invoice_bsc (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_srf_invoice_adjustment (
    INVOICE_ADJUSTMENT_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'SRF 인보이스 Adjustment Factor ID (PK)',
    YEAR_NO                INT           NOT NULL COMMENT '연도',
    SECTION                VARCHAR(20)   NOT NULL COMMENT '표 구분 (afbsc / afbsc-rebase / afasc)',
    ITEM_CODE              VARCHAR(20)   NOT NULL COMMENT '항목 (a, b, c, d, e, e-bottom, e-fly, f, g, h)',
    COEFFICIENT            DECIMAL(18,6) NULL COMMENT '계수',
    BASE_VALUE             DECIMAL(18,6) NULL COMMENT '기준값(Base)',
    PRESENT_VALUE          DECIMAL(18,6) NULL COMMENT '현재값(Present)',
    CREATED_AT             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY             BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT             DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY             BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN             CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT             DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY             BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (INVOICE_ADJUSTMENT_ID),
    CONSTRAINT UK_STEAM_SRF_INVOICE_ADJUSTMENT_KEY UNIQUE (YEAR_NO, SECTION, ITEM_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] SRF 보일러 인보이스 Adjustment Factor 항목 (연도 · 표 · 항목별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_SRF_INVOICE_ADJUSTMENT_DELETED_YN ON steam_srf_invoice_adjustment (DELETED_YN);


-- ============================================================
-- 12) 유동상 운영 그룹 (2026-11 규격 전환, 묶음 3-D)
--    옛 steam_cell_value 의 fluidized_plan_actual / _plan_summary / _year_note / _contract_daily / _contract_cost / _srf_inbound / _monthly_cache 를 대체한다.
--    계산값은 저장하지 않되, 다른 화면이 읽는 도급비·총 운영비는 화면 계산값 컬럼으로 둔다.
-- ============================================================

CREATE TABLE IF NOT EXISTS steam_fluidized_month (
    FLUIDIZED_MONTH_ID    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '유동상 월별 계획·비용 ID (PK)',
    YEAR_NO               INT           NOT NULL COMMENT '연도',
    MONTH_NO              INT           NOT NULL COMMENT '월 (1~12)',
    PLAN_OPERATING        DECIMAL(18,6) NULL COMMENT '사업계획: 운영비(백만원)',
    PLAN_POWER            DECIMAL(18,6) NULL COMMENT '사업계획: 전력비(백만원)',
    PLAN_WASTE            DECIMAL(18,6) NULL COMMENT '사업계획: 폐기물처리비(백만원)',
    PLAN_SRF_INCOME       DECIMAL(18,6) NULL COMMENT '사업계획: SRF수입금(백만원)',
    SUMMARY_REMARK        VARCHAR(1000) NULL COMMENT '사업계획대비 요약 비고',
    MONTH_NOTE            VARCHAR(1000) NULL COMMENT '실적비교 월별 특이사항',
    LABOR_COST            DECIMAL(18,6) NULL COMMENT '도급비용 지급현황: 인건비(원)',
    OPERATING_PAY_COST    DECIMAL(18,6) NULL COMMENT '도급비용 지급현황: 운영비(원)',
    IMPROVEMENT_PAY_COST  DECIMAL(18,6) NULL COMMENT '도급비용 지급현황: 설비개선비용(원)',
    IMPROVEMENT_COST      DECIMAL(18,6) NULL COMMENT '월별 운영내역: 설비개선비용(천원)',
    DEDUCTION_COST        DECIMAL(18,6) NULL COMMENT '월별 운영내역: 차감비(천원)',
    EXTRA_PAYMENT         DECIMAL(18,6) NULL COMMENT '월별 운영내역: 추가 지급분(천원)',
    WASTEWATER_QTY        DECIMAL(18,6) NULL COMMENT '월별 운영내역: 폐수량',
    SOIL_QTY              DECIMAL(18,6) NULL COMMENT '월별 운영내역: 폐토사 처리량',
    SOIL_COST             DECIMAL(18,6) NULL COMMENT '월별 운영내역: 폐토사 처리금액(천원)',
    CONTRACT_COST         DECIMAL(18,6) NULL COMMENT '도급비(천원) = 스팀 운영비용 + 고정비 + 설비개선비용 − 차감비 + 추가 지급분 (화면 계산값, 마감비교의 유동상 스팀구매가 읽음)',
    OPERATING_COST        DECIMAL(18,6) NULL COMMENT '총 운영비(천원) (화면 계산값)',
    CREATED_AT            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY            BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT            DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY            BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN            CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT            DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY            BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (FLUIDIZED_MONTH_ID),
    CONSTRAINT UK_STEAM_FLUIDIZED_MONTH_KEY UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 유동상 운영 — 월별 사업계획, 비고, 도급비용 지급현황, 월별 운영내역 입력값 (연/월별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_FLUIDIZED_MONTH_DELETED_YN ON steam_fluidized_month (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_fluidized_contract_daily (
    FLUIDIZED_CONTRACT_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '유동상 소각로 도급비용 ID (PK)',
    USAGE_DATE             DATE          NOT NULL COMMENT '일자',
    STEAM_TON              DECIMAL(18,6) NULL COMMENT '스팀 생산량(T/D)',
    STEAM_UNIT_PRICE       DECIMAL(18,6) NULL COMMENT '스팀단가(원/톤)',
    STOP_HOURS             DECIMAL(18,6) NULL COMMENT '운휴시간(Hr)',
    FIXED_COST_PER_HOUR    DECIMAL(18,6) NULL COMMENT '운휴시간당 고정비(원/시간)',
    CREATED_AT             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY             BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT             DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY             BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN             CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT             DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY             BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (FLUIDIZED_CONTRACT_ID),
    CONSTRAINT UK_STEAM_FLUIDIZED_CONTRACT_DAILY_KEY UNIQUE (USAGE_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 유동상 소각로 도급비용 (일자별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_FLUIDIZED_CONTRACT_DAILY_DELETED_YN ON steam_fluidized_contract_daily (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_fluidized_srf_inbound_daily (
    SRF_INBOUND_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '유동상 SRF 입고내역 ID (PK)',
    USAGE_DATE      DATE          NOT NULL COMMENT '일자',
    VENDOR01_KG     DECIMAL(18,6) NULL COMMENT '업체 1번 칸 입고량(kg) — 화면 기본 업체: 대일인터내셔널',
    VENDOR02_KG     DECIMAL(18,6) NULL COMMENT '업체 2번 칸 입고량(kg) — 화면 기본 업체: 그린플러스',
    VENDOR03_KG     DECIMAL(18,6) NULL COMMENT '업체 3번 칸 입고량(kg) — 화면 기본 업체: 대림종합개발',
    VENDOR04_KG     DECIMAL(18,6) NULL COMMENT '업체 4번 칸 입고량(kg) — 화면 기본 업체: 케이제이환경',
    VENDOR05_KG     DECIMAL(18,6) NULL COMMENT '업체 5번 칸 입고량(kg) — 화면 기본 업체: 태창',
    VENDOR06_KG     DECIMAL(18,6) NULL COMMENT '업체 6번 칸 입고량(kg) — 화면 기본 업체: 평일',
    VENDOR07_KG     DECIMAL(18,6) NULL COMMENT '업체 7번 칸 입고량(kg) — 화면 기본 업체: 케이엠',
    VENDOR08_KG     DECIMAL(18,6) NULL COMMENT '업체 8번 칸 입고량(kg) — 화면 기본 업체: 두제',
    VENDOR09_KG     DECIMAL(18,6) NULL COMMENT '업체 9번 칸 입고량(kg) — 화면 기본 업체: 세종스틸',
    VENDOR10_KG     DECIMAL(18,6) NULL COMMENT '업체 10번 칸 입고량(kg) — 화면 기본 업체: 리에나',
    CREATED_AT      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY      BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT      DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY      BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN      CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT      DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY      BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (SRF_INBOUND_ID),
    CONSTRAINT UK_STEAM_FLUIDIZED_SRF_INBOUND_DAILY_KEY UNIQUE (USAGE_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 유동상 SRF(비성형) 입고량 — 업체 칸 10개 (일자별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_FLUIDIZED_SRF_INBOUND_DAILY_DELETED_YN ON steam_fluidized_srf_inbound_daily (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_fluidized_srf_rate (
    SRF_RATE_ID    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '유동상 SRF 처리단가 ID (PK)',
    YEAR_NO        INT           NOT NULL COMMENT '연도',
    MONTH_NO       INT           NOT NULL COMMENT '월 (1~12)',
    VENDOR01_RATE  DECIMAL(18,6) NULL COMMENT '업체 1번 칸 처리단가(원/톤) — 화면 기본 업체: 대일인터내셔널',
    VENDOR02_RATE  DECIMAL(18,6) NULL COMMENT '업체 2번 칸 처리단가(원/톤) — 화면 기본 업체: 그린플러스',
    VENDOR03_RATE  DECIMAL(18,6) NULL COMMENT '업체 3번 칸 처리단가(원/톤) — 화면 기본 업체: 대림종합개발',
    VENDOR04_RATE  DECIMAL(18,6) NULL COMMENT '업체 4번 칸 처리단가(원/톤) — 화면 기본 업체: 케이제이환경',
    VENDOR05_RATE  DECIMAL(18,6) NULL COMMENT '업체 5번 칸 처리단가(원/톤) — 화면 기본 업체: 태창',
    VENDOR06_RATE  DECIMAL(18,6) NULL COMMENT '업체 6번 칸 처리단가(원/톤) — 화면 기본 업체: 평일',
    VENDOR07_RATE  DECIMAL(18,6) NULL COMMENT '업체 7번 칸 처리단가(원/톤) — 화면 기본 업체: 케이엠',
    VENDOR08_RATE  DECIMAL(18,6) NULL COMMENT '업체 8번 칸 처리단가(원/톤) — 화면 기본 업체: 두제',
    VENDOR09_RATE  DECIMAL(18,6) NULL COMMENT '업체 9번 칸 처리단가(원/톤) — 화면 기본 업체: 세종스틸',
    VENDOR10_RATE  DECIMAL(18,6) NULL COMMENT '업체 10번 칸 처리단가(원/톤) — 화면 기본 업체: 리에나',
    CREATED_AT     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY     BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT     DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY     BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN     CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT     DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY     BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (SRF_RATE_ID),
    CONSTRAINT UK_STEAM_FLUIDIZED_SRF_RATE_KEY UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 유동상 SRF(비성형) 처리단가 — 업체 칸 10개 (연/월별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_FLUIDIZED_SRF_RATE_DELETED_YN ON steam_fluidized_srf_rate (DELETED_YN);


-- ============================================================
-- 13) 사고·정비 이력 · 유동상 설비개선 (2026-11 규격 전환, 묶음 3-E)
--    옛 steam_cell_value (표 이름 steam_cell_value, 월 키 YYYY-00, 키 접두어:번호) 의 목록을 대체한다. 한 건 한 행.
-- ============================================================

CREATE TABLE IF NOT EXISTS steam_incident (
    INCIDENT_ID     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '사고·정비 이력 ID (PK)',
    YEAR_NO         INT           NOT NULL COMMENT '연도',
    RECORD_KEY      VARCHAR(80)   NOT NULL COMMENT '기록 키 (구분 접두어:번호 — 예: accident-record:1763600000000, improvement-item:xlsx-r12)',
    FACILITY        VARCHAR(20)   NULL COMMENT '시설 (FLUIDIZED: 유동상 / SRF: SRF 보일러 / INCINERATOR: 폐합성수지 소각로)',
    KIND            VARCHAR(20)   NULL COMMENT '종류 (ACCIDENT: 사고 / MAINTENANCE: 정비)',
    OCCUR_DATE      VARCHAR(100)  NULL COMMENT '일자 — 입력한 글자 그대로 (예: 2025-02-16, 3/16~17)',
    REASON          VARCHAR(1000) NULL COMMENT '사유',
    ACTION_TAKEN    VARCHAR(1000) NULL COMMENT '조치내역',
    DOWNTIME_HOURS  VARCHAR(100)  NULL COMMENT '운휴시간(Hr) — 입력한 글자 그대로',
    LOSS_AMOUNT     VARCHAR(100)  NULL COMMENT '손실금액(백만원) — 입력한 글자 그대로, 사고만',
    NOTE            VARCHAR(500)  NULL COMMENT '비고',
    SORT_ORDER      INT           NULL COMMENT '화면 정렬 순서',
    CREATED_AT      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY      BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT      DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY      BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN      CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT      DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY      BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (INCIDENT_ID),
    CONSTRAINT UK_STEAM_INCIDENT_KEY UNIQUE (YEAR_NO, RECORD_KEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 사고·정비 이력 — 유동상 · SRF 보일러 · 폐합성수지 소각로 (연도 · 기록별, 한 건 한 행)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_INCIDENT_DELETED_YN ON steam_incident (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_fluidized_improvement_payment (
    IMPROVEMENT_PAYMENT_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '유동상 설비개선 월별 비용 ID (PK)',
    YEAR_NO                 INT           NOT NULL COMMENT '연도',
    RECORD_KEY              VARCHAR(80)   NOT NULL COMMENT '기록 키 (구분 접두어:번호 — 예: accident-record:1763600000000, improvement-item:xlsx-r12)',
    PAY_MONTH               VARCHAR(10)   NULL COMMENT '월 (01~12)',
    PAYMENT_AMOUNT          DECIMAL(18,6) NULL COMMENT '지급액(원)',
    SPENT_AMOUNT            DECIMAL(18,6) NULL COMMENT '사용액(원)',
    VENDOR_NAME             VARCHAR(200)  NULL COMMENT '업체명',
    USAGE_DESC              VARCHAR(500)  NULL COMMENT '사용처',
    SORT_ORDER              INT           NULL COMMENT '화면 정렬 순서',
    CREATED_AT              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY              BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT              DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY              BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN              CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT              DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY              BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (IMPROVEMENT_PAYMENT_ID),
    CONSTRAINT UK_STEAM_FLUIDIZED_IMPROVEMENT_PAYMENT_KEY UNIQUE (YEAR_NO, RECORD_KEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 유동상 설비개선 비용현황 — 월별 지급·사용 내역 (연도 · 기록별, 한 건 한 행)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_FLUIDIZED_IMPROVEMENT_PAYMENT_DELETED_YN ON steam_fluidized_improvement_payment (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_fluidized_improvement_item (
    IMPROVEMENT_ITEM_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '유동상 설비개선 항목 ID (PK)',
    YEAR_NO              INT           NOT NULL COMMENT '연도',
    RECORD_KEY           VARCHAR(80)   NOT NULL COMMENT '기록 키 (구분 접두어:번호 — 예: accident-record:1763600000000, improvement-item:xlsx-r12)',
    CATEGORY             VARCHAR(50)   NULL COMMENT '구분 (설비개선 / 유지보수 / 추가 작업사항)',
    ITEM_NO              VARCHAR(50)   NULL COMMENT 'No. (화면에 보이는 번호, 글자)',
    ITEM_NAME            VARCHAR(500)  NULL COMMENT '항목',
    PLAN_COST            DECIMAL(18,6) NULL COMMENT '계획비용',
    ACTUAL_COST          DECIMAL(18,6) NULL COMMENT '실비용',
    STATUS               VARCHAR(50)   NULL COMMENT '완료여부 (완료 / 진행중 / 빈 값)',
    REMARK               VARCHAR(500)  NULL COMMENT '비고',
    SORT_ORDER           INT           NULL COMMENT '화면 정렬 순서',
    CREATED_AT           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY           BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT           DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY           BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN           CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT           DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY           BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (IMPROVEMENT_ITEM_ID),
    CONSTRAINT UK_STEAM_FLUIDIZED_IMPROVEMENT_ITEM_KEY UNIQUE (YEAR_NO, RECORD_KEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 유동상 설비개선 리스트 — 항목별 계획·실비용·완료여부 (연도 · 기록별, 한 건 한 행)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_FLUIDIZED_IMPROVEMENT_ITEM_DELETED_YN ON steam_fluidized_improvement_item (DELETED_YN);


-- ============================================================
-- 14) 폐합성소각로 운전일지 (2026-11 규격 전환, 묶음 4-A)
--    옛 steam_cell_value (표 이름 waste_incin_log, 월 키 YYYY-MM, 키 = 일, col_index = 항목 번호) 를 대체한다. 하루 한 행.
-- ============================================================

CREATE TABLE IF NOT EXISTS steam_waste_incin_daily_log (
    WASTE_INCIN_LOG_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '폐합성소각로 운전일지 ID (PK)',
    USAGE_DATE          DATE          NOT NULL COMMENT '일자',
    BURN_U1_SELF        DECIMAL(18,6) NULL COMMENT '소각량 폐1 자폐',
    BURN_U1_RECYCLE     DECIMAL(18,6) NULL COMMENT '소각량 폐1 재활용',
    BURN_U1_GENERATED   DECIMAL(18,6) NULL COMMENT '소각량 폐1 발생량',
    BURN_U1_RUN_HOURS   DECIMAL(18,6) NULL COMMENT '소각량 폐1 가동시간',
    BURN_U1_EXT_STOCK   DECIMAL(18,6) NULL COMMENT '소각량 폐1 외폐재고',
    BURN_U2_SELF        DECIMAL(18,6) NULL COMMENT '소각량 폐2 자폐',
    BURN_U2_RECYCLE     DECIMAL(18,6) NULL COMMENT '소각량 폐2 재활용',
    BURN_U2_GENERATED   DECIMAL(18,6) NULL COMMENT '소각량 폐2 발생량',
    BURN_U2_RUN_HOURS   DECIMAL(18,6) NULL COMMENT '소각량 폐2 가동시간',
    BURN_U2_SELF_STOCK  DECIMAL(18,6) NULL COMMENT '소각량 폐2 자폐재고',
    BURN_EXT_TOTAL      DECIMAL(18,6) NULL COMMENT '소각량 합계 외폐',
    STEAM_U1            DECIMAL(18,6) NULL COMMENT '스팀 폐1 생산량',
    STEAM_U2_FIRST      DECIMAL(18,6) NULL COMMENT '스팀 폐2-1 (폐#2 1차)',
    STEAM_U2_SECOND     DECIMAL(18,6) NULL COMMENT '스팀 폐2-2 (폐#2 2차)',
    STEAM_AGENCY        DECIMAL(18,6) NULL COMMENT '스팀 기관',
    STEAM_PRESSURE      VARCHAR(20)   NULL COMMENT '스팀 저/고 (글자)',
    WATER_U1_FIRST      DECIMAL(18,6) NULL COMMENT '급수 폐1-1',
    WATER_U1_SECOND     DECIMAL(18,6) NULL COMMENT '급수 폐1-2',
    WATER_U2_FIRST      DECIMAL(18,6) NULL COMMENT '급수 폐2-1',
    WATER_U2_SECOND     DECIMAL(18,6) NULL COMMENT '급수 폐2-2',
    POWER_METER         DECIMAL(18,6) NULL COMMENT '전력 지침',
    POWER_U1            DECIMAL(18,6) NULL COMMENT '전력 폐#1',
    POWER_U2            DECIMAL(18,6) NULL COMMENT '전력 폐#2',
    POWER_EP            DECIMAL(18,6) NULL COMMENT '전력 EP',
    FLY_ASH_U1          DECIMAL(18,6) NULL COMMENT '비산재 폐#1',
    FLY_ASH_U2          DECIMAL(18,6) NULL COMMENT '비산재 폐#2',
    BOTTOM_ASH_U1       DECIMAL(18,6) NULL COMMENT '바닥재 폐#1',
    BOTTOM_ASH_U2       DECIMAL(18,6) NULL COMMENT '바닥재 폐#2',
    UREA_IN             DECIMAL(18,6) NULL COMMENT '요소수 반입',
    UREA_USE            DECIMAL(18,6) NULL COMMENT '요소수 사용',
    UREA_STOCK          DECIMAL(18,6) NULL COMMENT '요소수 재고 — 1일 행에 월초 재고를 넣는다 (2일부터는 화면 계산)',
    BOILER_CHEM_IN      DECIMAL(18,6) NULL COMMENT '청관재 반입',
    BOILER_CHEM_USE     DECIMAL(18,6) NULL COMMENT '청관재 사용',
    BOILER_CHEM_STOCK   DECIMAL(18,6) NULL COMMENT '청관재 재고 — 1일 행에 월초 재고를 넣는다 (2일부터는 화면 계산)',
    SALT_IN             DECIMAL(18,6) NULL COMMENT '소금 반입',
    SALT_USE            DECIMAL(18,6) NULL COMMENT '소금 사용',
    SALT_FLUIDIZED      DECIMAL(18,6) NULL COMMENT '소금 유동상',
    SALT_STOCK          DECIMAL(18,6) NULL COMMENT '소금 재고 — 1일 행에 월초 재고를 넣는다 (2일부터는 화면 계산)',
    CAUSTIC_IN          DECIMAL(18,6) NULL COMMENT '가성 반입',
    CAUSTIC_USE         DECIMAL(18,6) NULL COMMENT '가성 사용',
    CAUSTIC_STOCK       DECIMAL(18,6) NULL COMMENT '가성 재고 — 1일 행에 월초 재고를 넣는다 (2일부터는 화면 계산)',
    CARBON_IN           DECIMAL(18,6) NULL COMMENT '활성탄 반입',
    CARBON_USE          DECIMAL(18,6) NULL COMMENT '활성탄 사용',
    CARBON_STOCK        DECIMAL(18,6) NULL COMMENT '활성탄 재고 — 1일 행에 월초 재고를 넣는다 (2일부터는 화면 계산)',
    OIL_IN              DECIMAL(18,6) NULL COMMENT '부생유 반입',
    OIL_U1              DECIMAL(18,6) NULL COMMENT '부생유 폐1',
    OIL_U2              DECIMAL(18,6) NULL COMMENT '부생유 폐2',
    OIL_SLUDGE1         DECIMAL(18,6) NULL COMMENT '부생유 슬1',
    OIL_SLUDGE2         DECIMAL(18,6) NULL COMMENT '부생유 슬2',
    OIL_STOCK           DECIMAL(18,6) NULL COMMENT '부생유 재고량 — 1일 행에 월초 재고를 넣는다 (2일부터는 화면 계산)',
    CLINKER_SIDE_WALL   DECIMAL(18,6) NULL COMMENT '크링커 측벽',
    CLINKER_DROP        DECIMAL(18,6) NULL COMMENT '크링커 낙차',
    CLINKER_IN          DECIMAL(18,6) NULL COMMENT '크링커 반입',
    CLINKER_USE         DECIMAL(18,6) NULL COMMENT '크링커 사용',
    CLINKER_STOCK       DECIMAL(18,6) NULL COMMENT '크링커 재고 — 1일 행에 월초 재고를 넣는다 (2일부터는 화면 계산)',
    TMS_U1_DUST         DECIMAL(18,6) NULL COMMENT 'TMS 폐#1 분진',
    TMS_U1_CO           DECIMAL(18,6) NULL COMMENT 'TMS 폐#1 CO',
    TMS_U1_NOX          DECIMAL(18,6) NULL COMMENT 'TMS 폐#1 Nox',
    TMS_U1_SOX          DECIMAL(18,6) NULL COMMENT 'TMS 폐#1 Sox',
    TMS_U1_HCL          DECIMAL(18,6) NULL COMMENT 'TMS 폐#1 HCl',
    TMS_U1_TEMP         DECIMAL(18,6) NULL COMMENT 'TMS 폐#1 온도',
    TMS_U2_DUST         DECIMAL(18,6) NULL COMMENT 'TMS 폐#2 분진',
    TMS_U2_CO           DECIMAL(18,6) NULL COMMENT 'TMS 폐#2 CO',
    TMS_U2_NOX          DECIMAL(18,6) NULL COMMENT 'TMS 폐#2 Nox',
    TMS_U2_SOX          DECIMAL(18,6) NULL COMMENT 'TMS 폐#2 Sox',
    TMS_U2_HCL          DECIMAL(18,6) NULL COMMENT 'TMS 폐#2 HCl',
    TMS_U2_TEMP         DECIMAL(18,6) NULL COMMENT 'TMS 폐#2 온도',
    INBOUND_VENDOR01    DECIMAL(18,6) NULL COMMENT '외부반입 업체 1번 칸 (보노아)',
    INBOUND_VENDOR02    DECIMAL(18,6) NULL COMMENT '외부반입 업체 2번 칸 (스라이브)',
    INBOUND_VENDOR03    DECIMAL(18,6) NULL COMMENT '외부반입 업체 3번 칸 (나투라)',
    INBOUND_VENDOR04    DECIMAL(18,6) NULL COMMENT '외부반입 업체 4번 칸 (대한제지)',
    INBOUND_VENDOR05    DECIMAL(18,6) NULL COMMENT '외부반입 업체 5번 칸 (신성)',
    INBOUND_VENDOR06    DECIMAL(18,6) NULL COMMENT '외부반입 업체 6번 칸 (그린이엔텍)',
    INBOUND_VENDOR07    DECIMAL(18,6) NULL COMMENT '외부반입 업체 7번 칸 (금보산업)',
    INBOUND_VENDOR08    DECIMAL(18,6) NULL COMMENT '외부반입 업체 8번 칸 (청원es)',
    INBOUND_VENDOR09    DECIMAL(18,6) NULL COMMENT '외부반입 업체 9번 칸 (티와이)',
    INBOUND_VENDOR10    DECIMAL(18,6) NULL COMMENT '외부반입 업체 10번 칸 (재운산업)',
    INBOUND_VENDOR11    DECIMAL(18,6) NULL COMMENT '외부반입 업체 11번 칸 (현진RC)',
    INBOUND_VENDOR12    DECIMAL(18,6) NULL COMMENT '외부반입 업체 12번 칸 (중앙이엔비)',
    INBOUND_VENDOR13    DECIMAL(18,6) NULL COMMENT '외부반입 업체 13번 칸 (주원)',
    INBOUND_VENDOR14    DECIMAL(18,6) NULL COMMENT '외부반입 업체 14번 칸 (태창)',
    GEN_WOOD            DECIMAL(18,6) NULL COMMENT '폐기물발생 폐목재',
    GEN_SAENGBUL        DECIMAL(18,6) NULL COMMENT '폐기물발생 생불',
    GEN_PLASTIC         DECIMAL(18,6) NULL COMMENT '폐기물발생 폐합성',
    GEN_REJECT          DECIMAL(18,6) NULL COMMENT '폐기물발생 리젝트',
    GEN_RAGGER          DECIMAL(18,6) NULL COMMENT '폐기물발생 라가',
    GEN_STOCK           DECIMAL(18,6) NULL COMMENT '폐기물발생 재고량',
    CREATED_AT          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY          BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT          DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY          BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN          CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT          DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY          BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (WASTE_INCIN_LOG_ID),
    CONSTRAINT UK_STEAM_WASTE_INCIN_DAILY_LOG_KEY UNIQUE (USAGE_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 폐합성소각로 운전일지 — 소각량·스팀·급수·전력·폐기물·약품·TMS·외부반입·폐기물발생 (일자별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_WASTE_INCIN_DAILY_LOG_DELETED_YN ON steam_waste_incin_daily_log (DELETED_YN);


-- ============================================================
-- 15) 유동상 세부 운영내역 (2026-11 규격 전환, 묶음 4-B)
--    옛 steam_cell_value (표 이름 fluidized_detail_main, 월 키 YYYY-MM, 키 = 엑셀 셀 주소) 를 대체한다.
--    일별 입력(하루 한 행) · 월별 입력(단가·비용) · 사용자가 추가한 행.
-- ============================================================

CREATE TABLE IF NOT EXISTS steam_fluidized_detail_daily (
    DETAIL_DAILY_ID               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '유동상 세부 운영내역 (일별) ID (PK)',
    USAGE_DATE                    DATE          NOT NULL COMMENT '일자',
    RUN_HOURS                     DECIMAL(18,6) NULL COMMENT '가동시간(HR) — 서식 4행',
    STEAM_PRODUCED_TON            DECIMAL(18,6) NULL COMMENT '스팀 생산량(톤) — 서식 6행',
    SLUDGE_BURN_TON               DECIMAL(18,6) NULL COMMENT '소각량 슬러지(톤) — 서식 8행',
    SRF_BURN_TON                  DECIMAL(18,6) NULL COMMENT '소각량 SRF(톤) — 서식 9행',
    STEAM_UNIT_PRICE              DECIMAL(18,6) NULL COMMENT 'KNE 스팀단가(원/톤) — 서식 11행',
    STOP_HOUR_UNIT_PRICE          DECIMAL(18,6) NULL COMMENT 'KNE 운휴시간 단가(원/시간) — 서식 13행',
    AMMONIUM_CARBONATE_IN_KG      DECIMAL(18,6) NULL COMMENT '탄산암모늄 반입(KG) — 서식 18행',
    AMMONIUM_CARBONATE_USE_KG     DECIMAL(18,6) NULL COMMENT '탄산암모늄 사용(KG) — 서식 19행',
    BOILER_CHEM_IN_KG             DECIMAL(18,6) NULL COMMENT '청관제 반입(KG) — 서식 21행',
    BOILER_CHEM_USE_KG            DECIMAL(18,6) NULL COMMENT '청관제 사용(KG) — 서식 22행',
    ACTIVATED_CARBON_IN_KG        DECIMAL(18,6) NULL COMMENT '활성탄 반입(KG) — 서식 24행',
    ACTIVATED_CARBON_USE_KG       DECIMAL(18,6) NULL COMMENT '활성탄 사용(KG) — 서식 25행',
    CAUSTIC_SODA_IN_KG            DECIMAL(18,6) NULL COMMENT '가성소다 반입(KG) — 서식 27행',
    CAUSTIC_SODA_USE_KG           DECIMAL(18,6) NULL COMMENT '가성소다 사용(KG) — 서식 28행',
    SLAKED_LIME_IN_KG             DECIMAL(18,6) NULL COMMENT '소석회 반입(KG) — 서식 30행',
    SLAKED_LIME_USE_KG            DECIMAL(18,6) NULL COMMENT '소석회 사용(KG) — 서식 31행',
    SALT_IN_KG                    DECIMAL(18,6) NULL COMMENT '소금 반입(KG) — 서식 33행',
    SALT_USE_KG                   DECIMAL(18,6) NULL COMMENT '소금 사용(KG) — 서식 34행',
    DIESEL_IN_L                   DECIMAL(18,6) NULL COMMENT '경유 반입(L) — 서식 36행',
    DIESEL_USE_L                  DECIMAL(18,6) NULL COMMENT '경유 사용(L) — 서식 37행',
    SILICA_SAND_IN_KG             DECIMAL(18,6) NULL COMMENT '규사 반입(KG) — 서식 39행',
    SILICA_SAND_USE_KG            DECIMAL(18,6) NULL COMMENT '규사 사용(KG) — 서식 40행',
    ACTIVATED_CARBON_EXTRA_IN_KG  DECIMAL(18,6) NULL COMMENT '활성탄 반입(KG) — 약품 아래 둘째 줄 — 서식 41행',
    EMERGENCY_DIESEL_IN_L         DECIMAL(18,6) NULL COMMENT '경유(L)-비상발전기 반입 — 서식 42행',
    SRF_IN_TON                    DECIMAL(18,6) NULL COMMENT 'SRF 반입 (서식 이름은 KG, 값은 톤) — 서식 46행',
    FLY_ASH_GENERATED_TON         DECIMAL(18,6) NULL COMMENT '소각재 비산재(발생) — 서식 49행',
    FLY_ASH_OUT_01_TON            DECIMAL(18,6) NULL COMMENT '소각재 비산재(반출) 1번 칸 (조은) — 서식 50행',
    FLY_ASH_OUT_02_TON            DECIMAL(18,6) NULL COMMENT '소각재 비산재(반출) 2번 칸 (씨엠디기술단) — 서식 51행',
    FLY_ASH_OUT_03_TON            DECIMAL(18,6) NULL COMMENT '소각재 비산재(반출) 3번 칸 (대광소재) — 서식 52행',
    FLY_ASH_OUT_04_TON            DECIMAL(18,6) NULL COMMENT '소각재 비산재(반출) 4번 칸 (아이케이) — 서식 53행',
    FLY_ASH_OUT_05_TON            DECIMAL(18,6) NULL COMMENT '소각재 비산재(반출) 5번 칸 (연경기초소재) — 서식 54행',
    FLY_ASH_OUT_06_TON            DECIMAL(18,6) NULL COMMENT '소각재 비산재(반출) 6번 칸 (느티나무) — 서식 55행',
    FLY_ASH_OUT_07_TON            DECIMAL(18,6) NULL COMMENT '소각재 비산재(반출) 7번 칸 (모두그린) — 서식 56행',
    FLY_ASH_OUT_08_TON            DECIMAL(18,6) NULL COMMENT '소각재 비산재(반출) 8번 칸 (주영이엔티) — 서식 57행',
    BOTTOM_ASH_GENERATED_TON      DECIMAL(18,6) NULL COMMENT '소각재 바닥재(발생) — 서식 58행',
    BOTTOM_ASH_OUT_01_TON         DECIMAL(18,6) NULL COMMENT '소각재 바닥재(반출) 1번 칸 (하이콘코리아) — 서식 59행',
    BOTTOM_ASH_OUT_02_TON         DECIMAL(18,6) NULL COMMENT '소각재 바닥재(반출) 2번 칸 (제이에이그린) — 서식 60행',
    BOTTOM_ASH_OUT_03_TON         DECIMAL(18,6) NULL COMMENT '소각재 바닥재(반출) 3번 칸 (새롬산업) — 서식 61행',
    BOTTOM_ASH_OUT_04_TON         DECIMAL(18,6) NULL COMMENT '소각재 바닥재(반출) 4번 칸 (삼영에스엔알) — 서식 62행',
    POWER_MAIN_KWH                DECIMAL(18,6) NULL COMMENT '전력량 Main(KW) — 서식 65행',
    POWER_ASH_VENT_FILTER_KWH     DECIMAL(18,6) NULL COMMENT '전력량 Ash 밴트필터(KW) — 서식 66행',
    POWER_BAG_FILTER_KWH          DECIMAL(18,6) NULL COMMENT '전력량 백필터(KW) — 서식 67행',
    POWER_SCRUBBER_KWH            DECIMAL(18,6) NULL COMMENT '전력량 스크러버(KW) — 서식 68행',
    POWER_CARBON_FEED_KWH         DECIMAL(18,6) NULL COMMENT '전력량 활성탄공급(KW) — 서식 69행',
    POWER_LIME_FEED_KWH           DECIMAL(18,6) NULL COMMENT '전력량 소석회공급(KW) — 서식 70행',
    POWER_UREA_KWH                DECIMAL(18,6) NULL COMMENT '전력량 요소수(KW) — 서식 71행',
    POWER_FD_FAN_KWH              DECIMAL(18,6) NULL COMMENT '전력량 F.D FAN(KW) — 서식 72행',
    REUSE_WATER_TON               DECIMAL(18,6) NULL COMMENT '용수 재이용수 — 서식 74행',
    RIVERBED_WATER_TON            DECIMAL(18,6) NULL COMMENT '용수 복류수 — 서식 75행',
    CONDENSATE_TON                DECIMAL(18,6) NULL COMMENT '용수 응축수 — 서식 76행',
    BOILER_FEED_WATER_TON         DECIMAL(18,6) NULL COMMENT '용수 보일러급수 — 서식 77행',
    WASTE_WATER_TON               DECIMAL(18,6) NULL COMMENT '용수 폐수 — 서식 78행',
    SOFT_WATER_TON                DECIMAL(18,6) NULL COMMENT '용수 연수 — 서식 79행',
    SLUDGE_OUTSOURCE_01_TON       DECIMAL(18,6) NULL COMMENT '슬러지위탁 1번 칸 (에덴녹화) — 서식 81행',
    SLUDGE_OUTSOURCE_02_TON       DECIMAL(18,6) NULL COMMENT '슬러지위탁 2번 칸 (에코비트) — 서식 82행',
    SLUDGE_OUTSOURCE_03_TON       DECIMAL(18,6) NULL COMMENT '슬러지위탁 3번 칸 (신기산업) — 서식 83행',
    SLUDGE_OUTSOURCE_04_TON       DECIMAL(18,6) NULL COMMENT '슬러지위탁 4번 칸 (진들농산) — 서식 84행',
    SLUDGE_OUTSOURCE_05_TON       DECIMAL(18,6) NULL COMMENT '슬러지위탁 5번 칸 (보림씨에스) — 서식 85행',
    SLUDGE_OUTSOURCE_06_TON       DECIMAL(18,6) NULL COMMENT '슬러지위탁 6번 칸 (삼화그린웰) — 서식 86행',
    SLUDGE_OUTSOURCE_07_TON       DECIMAL(18,6) NULL COMMENT '슬러지위탁 7번 칸 (제이농장) — 서식 87행',
    SLUDGE_OUTSOURCE_08_TON       DECIMAL(18,6) NULL COMMENT '슬러지위탁 8번 칸 (오대오바이오) — 서식 88행',
    SLUDGE_OUTSOURCE_09_TON       DECIMAL(18,6) NULL COMMENT '슬러지위탁 9번 칸 (관동녹화) — 서식 89행',
    SLUDGE_OUTSOURCE_10_TON       DECIMAL(18,6) NULL COMMENT '슬러지위탁 10번 칸 (원진영농) — 서식 90행',
    SLUDGE_OUTSOURCE_11_TON       DECIMAL(18,6) NULL COMMENT '슬러지위탁 11번 칸 (산우리) — 서식 91행',
    INSPECTION_FEE_QTY            DECIMAL(18,6) NULL COMMENT '측정검사비 — 서식 92행',
    CREATED_AT                    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY                    BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT                    DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY                    BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN                    CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT                    DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY                    BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (DETAIL_DAILY_ID),
    CONSTRAINT UK_STEAM_FLUIDIZED_DETAIL_DAILY_KEY UNIQUE (USAGE_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 유동상 세부 운영내역 — 일별 입력 (가동·스팀·소각량·단가·약품·소각재·전력·용수·슬러지위탁)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_FLUIDIZED_DETAIL_DAILY_DELETED_YN ON steam_fluidized_detail_daily (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_fluidized_detail_month (
    DETAIL_MONTH_ID                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '유동상 세부 운영내역 (월별 단가·비용) ID (PK)',
    YEAR_NO                          INT           NOT NULL COMMENT '연도',
    MONTH_NO                         INT           NOT NULL COMMENT '월 (1~12)',
    IMPROVEMENT_COST                 DECIMAL(18,6) NULL COMMENT 'KNE 설비개선비용 (AK15)',
    EXTRA_COST                       DECIMAL(18,6) NULL COMMENT 'KNE 추가비용 (AK16) — 엑셀로만 들어온다',
    STEAM_PRODUCED_UNIT_PRICE        DECIMAL(18,6) NULL COMMENT '스팀 생산량 단가 — 서식 AJ6',
    AMMONIUM_CARBONATE_UNIT_PRICE    DECIMAL(18,6) NULL COMMENT '탄산암모늄 단가 (사용 줄도 같은 값) — 서식 AJ18',
    BOILER_CHEM_IN_UNIT_PRICE        DECIMAL(18,6) NULL COMMENT '청관제 반입 단가 — 서식 AJ21',
    BOILER_CHEM_USE_UNIT_PRICE       DECIMAL(18,6) NULL COMMENT '청관제 사용 단가 — 서식 AJ22',
    ACTIVATED_CARBON_IN_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '활성탄 반입 단가 — 서식 AJ24',
    ACTIVATED_CARBON_USE_UNIT_PRICE  DECIMAL(18,6) NULL COMMENT '활성탄 사용 단가 — 서식 AJ25',
    CAUSTIC_SODA_IN_UNIT_PRICE       DECIMAL(18,6) NULL COMMENT '가성소다 반입 단가 — 서식 AJ27',
    CAUSTIC_SODA_USE_UNIT_PRICE      DECIMAL(18,6) NULL COMMENT '가성소다 사용 단가 — 서식 AJ28',
    SLAKED_LIME_UNIT_PRICE           DECIMAL(18,6) NULL COMMENT '소석회 단가 (사용 줄도 같은 값) — 서식 AJ30',
    SALT_UNIT_PRICE                  DECIMAL(18,6) NULL COMMENT '소금 단가 (사용 줄도 같은 값) — 서식 AJ33',
    DIESEL_UNIT_PRICE                DECIMAL(18,6) NULL COMMENT '경유 단가 (사용 줄도 같은 값) — 서식 AJ36',
    SILICA_SAND_USE_UNIT_PRICE       DECIMAL(18,6) NULL COMMENT '규사 사용 단가 — 서식 AJ40',
    SRF_UNIT_PRICE                   DECIMAL(18,6) NULL COMMENT 'SRF 반입 단가 — 값이 있으면 계산(비용÷반입량) 대신 이 값을 보인다 — 서식 AJ46',
    FLY_ASH_GENERATED_UNIT_PRICE     DECIMAL(18,6) NULL COMMENT '비산재(발생) 단가 — 서식 AJ49',
    FLY_ASH_OUT_01_UNIT_PRICE        DECIMAL(18,6) NULL COMMENT '비산재(반출) 1번 칸 단가 (조은) — 서식 AJ50',
    FLY_ASH_OUT_02_UNIT_PRICE        DECIMAL(18,6) NULL COMMENT '비산재(반출) 2번 칸 단가 (씨엠디기술단) — 서식 AJ51',
    FLY_ASH_OUT_03_UNIT_PRICE        DECIMAL(18,6) NULL COMMENT '비산재(반출) 3번 칸 단가 (대광소재) — 서식 AJ52',
    FLY_ASH_OUT_04_UNIT_PRICE        DECIMAL(18,6) NULL COMMENT '비산재(반출) 4번 칸 단가 (아이케이) — 서식 AJ53',
    FLY_ASH_OUT_05_UNIT_PRICE        DECIMAL(18,6) NULL COMMENT '비산재(반출) 5번 칸 단가 (연경기초소재) — 서식 AJ54',
    FLY_ASH_OUT_06_UNIT_PRICE        DECIMAL(18,6) NULL COMMENT '비산재(반출) 6번 칸 단가 (느티나무) — 서식 AJ55',
    FLY_ASH_OUT_07_UNIT_PRICE        DECIMAL(18,6) NULL COMMENT '비산재(반출) 7번 칸 단가 (모두그린) — 서식 AJ56',
    FLY_ASH_OUT_08_UNIT_PRICE        DECIMAL(18,6) NULL COMMENT '비산재(반출) 8번 칸 단가 (주영이엔티) — 서식 AJ57',
    BOTTOM_ASH_GENERATED_UNIT_PRICE  DECIMAL(18,6) NULL COMMENT '바닥재(발생) 단가 — 서식 AJ58',
    BOTTOM_ASH_OUT_01_UNIT_PRICE     DECIMAL(18,6) NULL COMMENT '바닥재(반출) 1번 칸 단가 (하이콘코리아) — 서식 AJ59',
    BOTTOM_ASH_OUT_02_UNIT_PRICE     DECIMAL(18,6) NULL COMMENT '바닥재(반출) 2번 칸 단가 (제이에이그린) — 서식 AJ60',
    BOTTOM_ASH_OUT_03_UNIT_PRICE     DECIMAL(18,6) NULL COMMENT '바닥재(반출) 3번 칸 단가 (새롬산업) — 서식 AJ61',
    BOTTOM_ASH_OUT_04_UNIT_PRICE     DECIMAL(18,6) NULL COMMENT '바닥재(반출) 4번 칸 단가 (삼영에스엔알) — 서식 AJ62',
    POWER_UNIT_PRICE                 DECIMAL(18,6) NULL COMMENT '전력 단가 (Main — 밴트필터~소석회공급 줄도 같은 값) — 서식 AJ65',
    POWER_UREA_UNIT_PRICE            DECIMAL(18,6) NULL COMMENT '전력 요소수 단가 (서식은 Main 단가를 따른다) — 서식 AJ71',
    POWER_FD_FAN_UNIT_PRICE          DECIMAL(18,6) NULL COMMENT '전력 F.D FAN 단가 (서식은 Main 단가를 따른다) — 서식 AJ72',
    REUSE_WATER_UNIT_PRICE           DECIMAL(18,6) NULL COMMENT '재이용수 단가 (복류수도 같은 값) — 서식 AJ74',
    CONDENSATE_UNIT_PRICE            DECIMAL(18,6) NULL COMMENT '응축수 단가 — 서식 AJ76',
    BOILER_FEED_WATER_UNIT_PRICE     DECIMAL(18,6) NULL COMMENT '보일러급수 단가 — 서식 AJ77',
    WASTE_WATER_UNIT_PRICE           DECIMAL(18,6) NULL COMMENT '폐수 단가 — 서식 AJ78',
    SOFT_WATER_UNIT_PRICE            DECIMAL(18,6) NULL COMMENT '연수 단가 — 서식 AJ79',
    SLUDGE_OUTSOURCE_01_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '슬러지위탁 1번 칸 단가 (에덴녹화) — 서식 AJ81',
    SLUDGE_OUTSOURCE_02_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '슬러지위탁 2번 칸 단가 (에코비트) — 서식 AJ82',
    SLUDGE_OUTSOURCE_03_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '슬러지위탁 3번 칸 단가 (신기산업) — 서식 AJ83',
    SLUDGE_OUTSOURCE_04_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '슬러지위탁 4번 칸 단가 (진들농산) — 서식 AJ84',
    SLUDGE_OUTSOURCE_05_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '슬러지위탁 5번 칸 단가 (보림씨에스) — 서식 AJ85',
    SLUDGE_OUTSOURCE_06_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '슬러지위탁 6번 칸 단가 (삼화그린웰) — 서식 AJ86',
    SLUDGE_OUTSOURCE_07_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '슬러지위탁 7번 칸 단가 (제이농장) — 서식 AJ87',
    SLUDGE_OUTSOURCE_08_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '슬러지위탁 8번 칸 단가 (오대오바이오) — 서식 AJ88',
    SLUDGE_OUTSOURCE_09_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '슬러지위탁 9번 칸 단가 (관동녹화) — 서식 AJ89',
    SLUDGE_OUTSOURCE_10_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '슬러지위탁 10번 칸 단가 (원진영농) — 서식 AJ90',
    SLUDGE_OUTSOURCE_11_UNIT_PRICE   DECIMAL(18,6) NULL COMMENT '슬러지위탁 11번 칸 단가 (산우리) — 서식 AJ91',
    INSPECTION_FEE_UNIT_PRICE        DECIMAL(18,6) NULL COMMENT '측정검사비 단가 — 서식 AJ92',
    SILICA_SAND_USE_NOTE             VARCHAR(200)  NULL COMMENT '규사 사용 줄의 글자 (예: 사용량 측정불가) — 서식 C40',
    CREATED_AT                       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY                       BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT                       DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY                       BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN                       CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT                       DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY                       BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (DETAIL_MONTH_ID),
    CONSTRAINT UK_STEAM_FLUIDIZED_DETAIL_MONTH_KEY UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 유동상 세부 운영내역 — 월별 입력 (항목 단가 · 설비개선비용 · 추가비용)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_FLUIDIZED_DETAIL_MONTH_DELETED_YN ON steam_fluidized_detail_month (DELETED_YN);

CREATE TABLE IF NOT EXISTS steam_fluidized_detail_custom_row (
    DETAIL_CUSTOM_ROW_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '유동상 세부 운영내역 (추가 행) ID (PK)',
    YEAR_NO               INT           NOT NULL COMMENT '연도',
    MONTH_NO              INT           NOT NULL COMMENT '월 (1~12)',
    SECTION               VARCHAR(30)   NOT NULL COMMENT '구역 (kne · chemical · waste · power · water · etc)',
    ROW_KEY               VARCHAR(40)   NOT NULL COMMENT '행 식별자 (화면이 만든 값, 예: r1779432186923216)',
    LABEL                 VARCHAR(200)  NULL COMMENT '행 이름',
    INSERT_AFTER          INT           NULL COMMENT '이 서식 행 번호 아래에 끼워 넣는다',
    DAY01                 DECIMAL(18,6) NULL COMMENT '1일 값',
    DAY02                 DECIMAL(18,6) NULL COMMENT '2일 값',
    DAY03                 DECIMAL(18,6) NULL COMMENT '3일 값',
    DAY04                 DECIMAL(18,6) NULL COMMENT '4일 값',
    DAY05                 DECIMAL(18,6) NULL COMMENT '5일 값',
    DAY06                 DECIMAL(18,6) NULL COMMENT '6일 값',
    DAY07                 DECIMAL(18,6) NULL COMMENT '7일 값',
    DAY08                 DECIMAL(18,6) NULL COMMENT '8일 값',
    DAY09                 DECIMAL(18,6) NULL COMMENT '9일 값',
    DAY10                 DECIMAL(18,6) NULL COMMENT '10일 값',
    DAY11                 DECIMAL(18,6) NULL COMMENT '11일 값',
    DAY12                 DECIMAL(18,6) NULL COMMENT '12일 값',
    DAY13                 DECIMAL(18,6) NULL COMMENT '13일 값',
    DAY14                 DECIMAL(18,6) NULL COMMENT '14일 값',
    DAY15                 DECIMAL(18,6) NULL COMMENT '15일 값',
    DAY16                 DECIMAL(18,6) NULL COMMENT '16일 값',
    DAY17                 DECIMAL(18,6) NULL COMMENT '17일 값',
    DAY18                 DECIMAL(18,6) NULL COMMENT '18일 값',
    DAY19                 DECIMAL(18,6) NULL COMMENT '19일 값',
    DAY20                 DECIMAL(18,6) NULL COMMENT '20일 값',
    DAY21                 DECIMAL(18,6) NULL COMMENT '21일 값',
    DAY22                 DECIMAL(18,6) NULL COMMENT '22일 값',
    DAY23                 DECIMAL(18,6) NULL COMMENT '23일 값',
    DAY24                 DECIMAL(18,6) NULL COMMENT '24일 값',
    DAY25                 DECIMAL(18,6) NULL COMMENT '25일 값',
    DAY26                 DECIMAL(18,6) NULL COMMENT '26일 값',
    DAY27                 DECIMAL(18,6) NULL COMMENT '27일 값',
    DAY28                 DECIMAL(18,6) NULL COMMENT '28일 값',
    DAY29                 DECIMAL(18,6) NULL COMMENT '29일 값',
    DAY30                 DECIMAL(18,6) NULL COMMENT '30일 값',
    DAY31                 DECIMAL(18,6) NULL COMMENT '31일 값',
    CREATED_AT            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY            BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT            DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY            BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN            CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT            DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY            BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (DETAIL_CUSTOM_ROW_ID),
    CONSTRAINT UK_STEAM_FLUIDIZED_DETAIL_CUSTOM_ROW_KEY UNIQUE (YEAR_NO, MONTH_NO, SECTION, ROW_KEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 유동상 세부 운영내역 — 사용자가 추가한 행 (월 · 구역 · 행별, 일자 값 31칸)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_FLUIDIZED_DETAIL_CUSTOM_ROW_DELETED_YN ON steam_fluidized_detail_custom_row (DELETED_YN);


-- ============================================================
-- 16) 유동상 운전일지 (2026-11 규격 전환, 묶음 4-C)
--    옛 steam_cell_value (표 이름 fluidized_daily_log, 월 키 YYYY-MM, 키 = 일, col_index = 항목 번호) 를 대체한다. 하루 한 행.
-- ============================================================

CREATE TABLE IF NOT EXISTS steam_fluidized_daily_log (
    FLUIDIZED_LOG_ID              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '유동상 운전일지 ID (PK)',
    USAGE_DATE                    DATE          NOT NULL COMMENT '일자',
    WORKER_AM                     VARCHAR(100)  NULL COMMENT '근무자 오전',
    WORKER_PM                     VARCHAR(100)  NULL COMMENT '근무자 오후',
    WORKER_NIGHT                  VARCHAR(100)  NULL COMMENT '근무자 야간',
    APPR_LEADER                   VARCHAR(50)   NULL COMMENT '결재 반장',
    APPR_ASSISTANT                VARCHAR(50)   NULL COMMENT '결재 대리',
    APPR_MANAGER                  VARCHAR(50)   NULL COMMENT '결재 팀장',
    CHEM_AMMONIUM_CARBONATE_PREV  DECIMAL(18,6) NULL COMMENT '약품 탄산암모늄 전일 재고량 — 비어 있으면 전날 재고를 이어 쓴다',
    CHEM_BOILER_AGENT_PREV        DECIMAL(18,6) NULL COMMENT '약품 청관제 전일 재고량 — 비어 있으면 전날 재고를 이어 쓴다',
    CHEM_ACTIVATED_CARBON_PREV    DECIMAL(18,6) NULL COMMENT '약품 활성탄 전일 재고량 — 비어 있으면 전날 재고를 이어 쓴다',
    CHEM_CAUSTIC_SODA_PREV        DECIMAL(18,6) NULL COMMENT '약품 가성소다 전일 재고량 — 비어 있으면 전날 재고를 이어 쓴다',
    CHEM_SLAKED_LIME_PREV         DECIMAL(18,6) NULL COMMENT '약품 소석회 전일 재고량 — 비어 있으면 전날 재고를 이어 쓴다',
    CHEM_SALT_PREV                DECIMAL(18,6) NULL COMMENT '약품 소금 전일 재고량 — 비어 있으면 전날 재고를 이어 쓴다',
    CHEM_DIESEL_PREV              DECIMAL(18,6) NULL COMMENT '약품 경유(L) 전일 재고량 — 비어 있으면 전날 재고를 이어 쓴다',
    CHEM_SRF_PREV                 DECIMAL(18,6) NULL COMMENT '약품 SRF 전일 재고량 — 비어 있으면 전날 재고를 이어 쓴다',
    CHEM_SILICA_SAND_PREV         DECIMAL(18,6) NULL COMMENT '약품 규사 전일 재고량 — 비어 있으면 전날 재고를 이어 쓴다',
    ASH_FLY_OUT                   DECIMAL(18,6) NULL COMMENT '소각재 비산재 반출량(TON)',
    ASH_BOTTOM_OUT                DECIMAL(18,6) NULL COMMENT '소각재 바닥재 반출량(TON)',
    INCOMBUSTIBLE_DAY             DECIMAL(18,6) NULL COMMENT '불연물 주간(대)',
    INCOMBUSTIBLE_NIGHT           DECIMAL(18,6) NULL COMMENT '불연물 야간(대)',
    POWER_MAIN_PREV               DECIMAL(18,6) NULL COMMENT '전력 MAIN 전일 지침 — 비어 있으면 전날 금일 지침을 이어 쓴다',
    POWER_MAIN_TODAY              DECIMAL(18,6) NULL COMMENT '전력 MAIN 금일 지침',
    POWER_ASH_SILO_PREV           DECIMAL(18,6) NULL COMMENT '전력 ASH 저장조 전일 지침 — 비어 있으면 전날 금일 지침을 이어 쓴다',
    POWER_ASH_SILO_TODAY          DECIMAL(18,6) NULL COMMENT '전력 ASH 저장조 금일 지침',
    POWER_BAG_FILTER_PREV         DECIMAL(18,6) NULL COMMENT '전력 BAG FILTER 전일 지침 — 비어 있으면 전날 금일 지침을 이어 쓴다',
    POWER_BAG_FILTER_TODAY        DECIMAL(18,6) NULL COMMENT '전력 BAG FILTER 금일 지침',
    POWER_SCRUBBER_PREV           DECIMAL(18,6) NULL COMMENT '전력 SCRUBBER 전일 지침 — 비어 있으면 전날 금일 지침을 이어 쓴다',
    POWER_SCRUBBER_TODAY          DECIMAL(18,6) NULL COMMENT '전력 SCRUBBER 금일 지침',
    POWER_CARBON_FEED_PREV        DECIMAL(18,6) NULL COMMENT '전력 활성탄 공급 전일 지침 — 비어 있으면 전날 금일 지침을 이어 쓴다',
    POWER_CARBON_FEED_TODAY       DECIMAL(18,6) NULL COMMENT '전력 활성탄 공급 금일 지침',
    POWER_LIME_FEED_PREV          DECIMAL(18,6) NULL COMMENT '전력 소석회 공급 전일 지침 — 비어 있으면 전날 금일 지침을 이어 쓴다',
    POWER_LIME_FEED_TODAY         DECIMAL(18,6) NULL COMMENT '전력 소석회 공급 금일 지침',
    POWER_UREA_FEED_PREV          DECIMAL(18,6) NULL COMMENT '전력 요소수 공급 전일 지침 — 비어 있으면 전날 금일 지침을 이어 쓴다',
    POWER_UREA_FEED_TODAY         DECIMAL(18,6) NULL COMMENT '전력 요소수 공급 금일 지침',
    POWER_FD_PREV                 DECIMAL(18,6) NULL COMMENT '전력 F.D 전일 지침 — 비어 있으면 전날 금일 지침을 이어 쓴다',
    POWER_FD_TODAY                DECIMAL(18,6) NULL COMMENT '전력 F.D 금일 지침',
    POWER_CARBON_BAG_PREV         DECIMAL(18,6) NULL COMMENT '전력 활성탄 BAG 전일 지침 — 비어 있으면 전날 금일 지침을 이어 쓴다',
    POWER_CARBON_BAG_TODAY        DECIMAL(18,6) NULL COMMENT '전력 활성탄 BAG 금일 지침',
    POWER_LIME_BAG_PREV           DECIMAL(18,6) NULL COMMENT '전력 소석회 BAG 전일 지침 — 비어 있으면 전날 금일 지침을 이어 쓴다',
    POWER_LIME_BAG_TODAY          DECIMAL(18,6) NULL COMMENT '전력 소석회 BAG 금일 지침',
    UTIL_REUSE_WATER_PREV         DECIMAL(18,6) NULL COMMENT '유틸리티 재이용수 전일 사용량 — 비어 있으면 전날 금일 사용량을 이어 쓴다',
    UTIL_RIVER_WATER_PREV         DECIMAL(18,6) NULL COMMENT '유틸리티 복류수 전일 사용량 — 비어 있으면 전날 금일 사용량을 이어 쓴다',
    UTIL_CONDENSATE_PREV          DECIMAL(18,6) NULL COMMENT '유틸리티 응축수 전일 사용량 — 비어 있으면 전날 금일 사용량을 이어 쓴다',
    UTIL_BOILER_FEED_PREV         DECIMAL(18,6) NULL COMMENT '유틸리티 보일러 급수 전일 사용량 — 비어 있으면 전날 금일 사용량을 이어 쓴다',
    UTIL_WASTE_WATER_PREV         DECIMAL(18,6) NULL COMMENT '유틸리티 폐수 전일 사용량 — 비어 있으면 전날 금일 사용량을 이어 쓴다',
    UTIL_SOFT_WATER_PREV          DECIMAL(18,6) NULL COMMENT '유틸리티 연수 전일 사용량 — 비어 있으면 전날 금일 사용량을 이어 쓴다',
    MOIST_AM_1_TIME               VARCHAR(8)    NULL COMMENT '함수율 오전 1번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_AM_1_VALUE              DECIMAL(18,6) NULL COMMENT '함수율 오전 1번 탈수기 (%)',
    MOIST_AM_2_TIME               VARCHAR(8)    NULL COMMENT '함수율 오전 2번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_AM_2_VALUE              DECIMAL(18,6) NULL COMMENT '함수율 오전 2번 탈수기 (%)',
    MOIST_AM_3_TIME               VARCHAR(8)    NULL COMMENT '함수율 오전 3번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_AM_3_VALUE              DECIMAL(18,6) NULL COMMENT '함수율 오전 3번 탈수기 (%)',
    MOIST_AM_4_TIME               VARCHAR(8)    NULL COMMENT '함수율 오전 4번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_AM_4_VALUE              DECIMAL(18,6) NULL COMMENT '함수율 오전 4번 탈수기 (%)',
    MOIST_AM_5_TIME               VARCHAR(8)    NULL COMMENT '함수율 오전 5번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_AM_5_VALUE              DECIMAL(18,6) NULL COMMENT '함수율 오전 5번 탈수기 (%)',
    MOIST_PM_1_TIME               VARCHAR(8)    NULL COMMENT '함수율 오후 1번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_PM_1_VALUE              DECIMAL(18,6) NULL COMMENT '함수율 오후 1번 탈수기 (%)',
    MOIST_PM_2_TIME               VARCHAR(8)    NULL COMMENT '함수율 오후 2번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_PM_2_VALUE              DECIMAL(18,6) NULL COMMENT '함수율 오후 2번 탈수기 (%)',
    MOIST_PM_3_TIME               VARCHAR(8)    NULL COMMENT '함수율 오후 3번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_PM_3_VALUE              DECIMAL(18,6) NULL COMMENT '함수율 오후 3번 탈수기 (%)',
    MOIST_PM_4_TIME               VARCHAR(8)    NULL COMMENT '함수율 오후 4번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_PM_4_VALUE              DECIMAL(18,6) NULL COMMENT '함수율 오후 4번 탈수기 (%)',
    MOIST_PM_5_TIME               VARCHAR(8)    NULL COMMENT '함수율 오후 5번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_PM_5_VALUE              DECIMAL(18,6) NULL COMMENT '함수율 오후 5번 탈수기 (%)',
    MOIST_NIGHT_1_TIME            VARCHAR(8)    NULL COMMENT '함수율 야간 1번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_NIGHT_1_VALUE           DECIMAL(18,6) NULL COMMENT '함수율 야간 1번 탈수기 (%)',
    MOIST_NIGHT_2_TIME            VARCHAR(8)    NULL COMMENT '함수율 야간 2번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_NIGHT_2_VALUE           DECIMAL(18,6) NULL COMMENT '함수율 야간 2번 탈수기 (%)',
    MOIST_NIGHT_3_TIME            VARCHAR(8)    NULL COMMENT '함수율 야간 3번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_NIGHT_3_VALUE           DECIMAL(18,6) NULL COMMENT '함수율 야간 3번 탈수기 (%)',
    MOIST_NIGHT_4_TIME            VARCHAR(8)    NULL COMMENT '함수율 야간 4번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_NIGHT_4_VALUE           DECIMAL(18,6) NULL COMMENT '함수율 야간 4번 탈수기 (%)',
    MOIST_NIGHT_5_TIME            VARCHAR(8)    NULL COMMENT '함수율 야간 5번 탈수기 취득시간 (HH:MM, 글자)',
    MOIST_NIGHT_5_VALUE           DECIMAL(18,6) NULL COMMENT '함수율 야간 5번 탈수기 (%)',
    CREATED_AT                    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY                    BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT                    DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY                    BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN                    CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT                    DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY                    BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (FLUIDIZED_LOG_ID),
    CONSTRAINT UK_STEAM_FLUIDIZED_DAILY_LOG_KEY UNIQUE (USAGE_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 유동상 운전일지 — 근무자·약품 전일 재고·소각재·전력 지침·유틸리티 전일 사용량·함수율 (일자별)';
CREATE INDEX IF NOT EXISTS IDX_STEAM_FLUIDIZED_DAILY_LOG_DELETED_YN ON steam_fluidized_daily_log (DELETED_YN);
