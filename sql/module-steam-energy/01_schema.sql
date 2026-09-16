-- ============================================================
-- module-steam-energy: 스팀에너지관리 DDL
-- Database: MariaDB 10.11+ (utf8mb4)
-- Naming: table = lower_snake_case, column = UPPER_SNAKE_CASE
-- ddl-auto=none 이므로 반드시 본 스크립트로 테이블을 생성한다.
-- ============================================================

-- 기존 테이블 존재 시 DROP (최초 설치만 해당, 운영 환경에서는 주석 처리)

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

CREATE INDEX IF NOT EXISTS IDX_STEAM_EQUIPMENT_CATEGORY ON steam_equipment (CATEGORY);

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
