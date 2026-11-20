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

