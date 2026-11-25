-- ============================================================
-- module-steam-energy: 운영 최적화 메뉴 반영 (2026-11-25)
--
-- 이미 스팀 모듈이 설치된 DB(개발서버 · 운영)에 이번 작업분만 더한다. 여러 번 실행해도 된다.
--   1) 표 4개: steam_move_plan_month · steam_move_plan_machine · steam_move_plan_boiler · steam_move_plan_day
--      (CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / ADD COLUMN IF NOT EXISTS)
--   2) 메뉴 1개: 스팀에너지관리 › 운영 최적화 (STEAM_MOVE_PLAN, /steam/energy-move-plan.html)
--      — 있으면 이름 · 위치 · 순서만 맞추고, 없으면 넣는다. 다른 스팀 메뉴는 건드리지 않는다.
--   3) 역할 권한: 스팀 메뉴를 쓰는 6개 역할 — 이미 있으면 넣지 않는다.
--
-- 내용은 01_schema.sql 17절 · 05_submenus.sql 의 STEAM_MOVE_PLAN 줄과 같다
-- (새로 설치하는 DB 는 01 ~ 06 만 실행하면 되고 이 파일은 필요 없다).
-- DROP · 무조건 DELETE 없음.
-- ============================================================

-- ────────────────────────────────────────────────
-- 17) 운영 최적화 (2026-11 신규)
--     화면: 스팀에너지관리 › 운영 최적화 (energy-move-plan.html)
--     생산계획(호기별 생산량) × 전년 원단위 = 필요 스팀량 → 폐합성 → 복합 → 유동상 → LNG 순으로 배분한다.
--     입력값만 저장한다. 필요 스팀량·배분량·비용은 화면이 계산하고 저장하지 않는다.
-- ────────────────────────────────────────────────
-- steam_move_plan_month — 연·월별 한 행. 비운 칸은 화면이 기본값(단가 입력·실적에서 구한 값)을 쓴다.
CREATE TABLE IF NOT EXISTS steam_move_plan_month (
    MOVE_PLAN_MONTH_ID       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '이동계획 월 기준값 ID (PK)',
    YEAR_NO                  INT           NOT NULL COMMENT '연도',
    MONTH_NO                 INT           NOT NULL COMMENT '월 (1~12)',
    ACTUAL_UNTIL_DAY         INT           NULL COMMENT '실적 반영 마지막 날 (1~31, 0 = 실적 반영 안 함, 비우면 실적이 있는 마지막 날)',
    ADJUST_YN                CHAR(1)       NULL COMMENT '남은 날 보정 여부 (Y = 월 필요 스팀량에 맞춰 남은 날을 보정, N = 보정 안 함, 비우면 Y)',
    WASTE2_BILL_CAP_TON      DECIMAL(18,6) NULL COMMENT '폐합성 2호기 정산 상한(톤/일) — 비우면 336',
    COMBO_GUARANTEE_TON      DECIMAL(18,6) NULL COMMENT '복합보일러 보증량(톤/월) — 비우면 단가 입력의 BSC 보증량',
    COMBO_BSC_PRICE          DECIMAL(18,6) NULL COMMENT '복합보일러 BSC 적용단가(원/톤) — 비우면 단가 입력의 BSC 단가 × FACTOR',
    COMBO_ASC_PRICE          DECIMAL(18,6) NULL COMMENT '복합보일러 ASC 적용단가(원/톤) — 비우면 단가 입력의 ASC 단가 × FACTOR',
    FLUID_BILL_TON_HOUR      DECIMAL(18,6) NULL COMMENT '유동상 정산 스팀량(톤/시간) — 비우면 최근 실적(도급 스팀량 ÷ 가동시간)',
    FLUID_IMPROVEMENT_COST   DECIMAL(18,6) NULL COMMENT '유동상 설비개선비(원/월)',
    LNG_M3_PER_TON           DECIMAL(18,6) NULL COMMENT 'LNG 보일러 스팀 톤당 LNG 사용량(N㎥/톤) — 비우면 최근 실적',
    LNG_UNIT_PRICE           DECIMAL(18,6) NULL COMMENT 'LNG 단가(원/N㎥) — 비우면 단가 입력의 LNG 단가',
    NOTE                     VARCHAR(500)  NULL COMMENT '비고',
    CREATED_AT               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY               BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT               DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY               BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN               CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT               DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY               BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (MOVE_PLAN_MONTH_ID),
    CONSTRAINT UK_STEAM_MOVE_PLAN_MONTH_KEY UNIQUE (YEAR_NO, MONTH_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 이동계획 월 기준값';
CREATE INDEX IF NOT EXISTS IDX_STEAM_MOVE_PLAN_MONTH_DELETED_YN ON steam_move_plan_month (DELETED_YN);

-- steam_move_plan_machine — 연·월·호기별 한 행. 생산계획 엑셀(생산계획 시트)을 올리거나 화면에서 직접 넣는다.
CREATE TABLE IF NOT EXISTS steam_move_plan_machine (
    MOVE_PLAN_MACHINE_ID     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '이동계획 호기별 생산계획 ID (PK)',
    YEAR_NO                  INT           NOT NULL COMMENT '연도',
    MONTH_NO                 INT           NOT NULL COMMENT '월 (1~12)',
    MACHINE_CODE             VARCHAR(20)   NOT NULL COMMENT '호기 코드 (PM-2 / PM-3 / TM-3 / TM-4 / TM-5)',
    PRODUCTION_TON           DECIMAL(18,6) NULL COMMENT '생산량(톤)',
    RUNTIME_HOURS            DECIMAL(12,4) NULL COMMENT '계획 가동시간(Hr)',
    PLAN_KIND                VARCHAR(10)   NULL COMMENT '구분 (예상/실적) — 생산계획 엑셀의 월 머리글 아랫줄',
    UNIT_OVERRIDE            DECIMAL(18,6) NULL COMMENT '스팀 원단위 직접 입력(톤·스팀/톤·제품) — 비우면 전년 같은 달 종합 원단위(기타 스팀량 포함)',
    CREATED_AT               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY               BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT               DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY               BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN               CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT               DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY               BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (MOVE_PLAN_MACHINE_ID),
    CONSTRAINT UK_STEAM_MOVE_PLAN_MACHINE_KEY UNIQUE (YEAR_NO, MONTH_NO, MACHINE_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 이동계획 호기별 생산계획';
CREATE INDEX IF NOT EXISTS IDX_STEAM_MOVE_PLAN_MACHINE_DELETED_YN ON steam_move_plan_machine (DELETED_YN);

-- steam_move_plan_boiler — 연·월·보일러별 한 행. 비운 칸은 화면이 기본값(최근 실적·단가 입력)을 쓴다.
CREATE TABLE IF NOT EXISTS steam_move_plan_boiler (
    MOVE_PLAN_BOILER_ID      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '이동계획 보일러별 기준값 ID (PK)',
    YEAR_NO                  INT           NOT NULL COMMENT '연도',
    MONTH_NO                 INT           NOT NULL COMMENT '월 (1~12)',
    BOILER_CODE              VARCHAR(20)   NOT NULL COMMENT '보일러 코드 (WASTE1 / WASTE2 / COMBO / FLUID / LNG — LNG 는 20톤·50톤 보일러를 합친 하나)',
    USE_YN                   CHAR(1)       NULL COMMENT '배분 대상 여부 (Y/N) — 비우면 폐합성 1호기만 N',
    MAX_TON_DAY              DECIMAL(18,6) NULL COMMENT '하루 최대 공급량(톤/일) — 비우면 최근 12개월 일 실적 상위 10% 평균',
    MIN_TON_DAY              DECIMAL(18,6) NULL COMMENT '하루 최소 운용량(톤/일) — 비우면 기본값',
    STEAM_UNIT_PRICE         DECIMAL(18,6) NULL COMMENT '스팀단가(원/톤) — 비우면 단가 입력',
    IDLE_HOUR_PRICE          DECIMAL(18,6) NULL COMMENT '운휴시간당 고정비(원/시간) — 비우면 단가 입력',
    CREATED_AT               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY               BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT               DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY               BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN               CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT               DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY               BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (MOVE_PLAN_BOILER_ID),
    CONSTRAINT UK_STEAM_MOVE_PLAN_BOILER_KEY UNIQUE (YEAR_NO, MONTH_NO, BOILER_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 이동계획 보일러별 기준값';
CREATE INDEX IF NOT EXISTS IDX_STEAM_MOVE_PLAN_BOILER_DELETED_YN ON steam_move_plan_boiler (DELETED_YN);

-- steam_move_plan_day — 일자별 한 행. 생산설비 운휴시간 · 보일러 정비 운휴시간 · 보일러 우리 사정 운휴시간을 날짜별로 고른다 (하루 종일 = 24).
CREATE TABLE IF NOT EXISTS steam_move_plan_day (
    MOVE_PLAN_DAY_ID         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '이동계획 일자별 운휴 ID (PK)',
    PLAN_DATE                DATE          NOT NULL COMMENT '일자',
    DOWN_PM2                 DECIMAL(6,2)  NULL COMMENT '제지 2호기 운휴시간(0~24) — 스팀 필요량이 줄어든다',
    DOWN_PM3                 DECIMAL(6,2)  NULL COMMENT '제지 3호기 운휴시간(0~24)',
    DOWN_TM3                 DECIMAL(6,2)  NULL COMMENT '화장지 3호기 운휴시간(0~24)',
    DOWN_TM4                 DECIMAL(6,2)  NULL COMMENT '화장지 4호기 운휴시간(0~24)',
    DOWN_TM5                 DECIMAL(6,2)  NULL COMMENT '화장지 5호기 운휴시간(0~24)',
    STOP_WASTE1              DECIMAL(6,2)  NULL COMMENT '폐합성 1호기 보일러 정비 운휴시간(0~24) — 보일러 사정이라 고정비가 없다',
    STOP_WASTE2              DECIMAL(6,2)  NULL COMMENT '폐합성 2호기 보일러 정비 운휴시간(0~24)',
    STOP_COMBO               DECIMAL(6,2)  NULL COMMENT '복합보일러 정비 운휴시간(0~24)',
    STOP_FLUID               DECIMAL(6,2)  NULL COMMENT '유동상 보일러 정비 운휴시간(0~24)',
    STOP_LNG                 DECIMAL(6,2)  NULL COMMENT 'LNG 보일러(20톤 + 50톤 합침) 정비 운휴시간(0~24)',
    IDLE_WASTE1               DECIMAL(6,2)  NULL COMMENT '폐합성 1호기 우리 사정 운휴시간(0~24) — 일별 계획 T/D 칸을 눌러 고른다. 그 시간은 배분하지 않고 운휴 고정비가 나간다',
    IDLE_WASTE2               DECIMAL(6,2)  NULL COMMENT '폐합성 2호기 우리 사정 운휴시간(0~24)',
    IDLE_COMBO                DECIMAL(6,2)  NULL COMMENT '복합보일러 우리 사정 운휴시간(0~24)',
    IDLE_FLUID                DECIMAL(6,2)  NULL COMMENT '유동상 우리 사정 운휴시간(0~24)',
    IDLE_LNG                 DECIMAL(6,2)  NULL COMMENT 'LNG 보일러(20톤 + 50톤 합침) 우리 사정 운휴시간(0~24)',
    NOTE                     VARCHAR(500)  NULL COMMENT '비고',
    CREATED_AT               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY               BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT               DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY               BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN               CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (Y/N)',
    DELETED_AT               DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY               BIGINT        NULL COMMENT '삭제자 사용자 ID',
    PRIMARY KEY (MOVE_PLAN_DAY_ID),
    CONSTRAINT UK_STEAM_MOVE_PLAN_DAY_KEY UNIQUE (PLAN_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-steam-energy] 이동계획 일자별 운휴';
CREATE INDEX IF NOT EXISTS IDX_STEAM_MOVE_PLAN_DAY_DELETED_YN ON steam_move_plan_day (DELETED_YN);
-- 2026-11-25 우리 사정 운휴시간 컬럼 추가 — 위 CREATE 전에 만든 표에도 붙인다 (재실행 안전)
ALTER TABLE steam_move_plan_day
    ADD COLUMN IF NOT EXISTS IDLE_WASTE1 DECIMAL(6,2) NULL COMMENT '폐합성 1호기 우리 사정 운휴시간(0~24) — 일별 계획 T/D 칸을 눌러 고른다. 그 시간은 배분하지 않고 운휴 고정비가 나간다' AFTER STOP_FLUID,
    ADD COLUMN IF NOT EXISTS IDLE_WASTE2 DECIMAL(6,2) NULL COMMENT '폐합성 2호기 우리 사정 운휴시간(0~24)' AFTER IDLE_WASTE1,
    ADD COLUMN IF NOT EXISTS IDLE_COMBO DECIMAL(6,2) NULL COMMENT '복합보일러 우리 사정 운휴시간(0~24)' AFTER IDLE_WASTE2,
    ADD COLUMN IF NOT EXISTS IDLE_FLUID DECIMAL(6,2) NULL COMMENT '유동상 우리 사정 운휴시간(0~24)' AFTER IDLE_COMBO,
    ADD COLUMN IF NOT EXISTS IDLE_LNG DECIMAL(6,2) NULL COMMENT 'LNG 보일러(20톤 + 50톤 합침) 우리 사정 운휴시간(0~24)' AFTER IDLE_FLUID,
    ADD COLUMN IF NOT EXISTS STOP_LNG DECIMAL(6,2) NULL COMMENT 'LNG 보일러(20톤 + 50톤 합침) 정비 운휴시간(0~24)' AFTER STOP_FLUID;
-- 2026-11-25 LNG 20톤·50톤을 하나(STOP_LNG · IDLE_LNG)로 합쳤다. 그 전에 만든 표에 남은 STOP_LNG20 · STOP_LNG50 ·
-- IDLE_LNG20 · IDLE_LNG50 은 쓰지 않는다 (지우지 않음 — 값이 들어간 적 없음).

-- ────────────────────────────────────────────────
-- 메뉴: 스팀에너지관리 바로 아래, 그룹 6개 뒤 (정렬 7)
-- ────────────────────────────────────────────────
UPDATE core_menu m
  JOIN core_menu p ON p.MENU_CODE = 'STEAM_ENERGY_MGMT'
   SET m.MENU_NAME = '운영 최적화', m.PARENT_ID = p.MENU_ID, m.MENU_URL = '/steam/energy-move-plan.html',
       m.ICON = 'steam_energy', m.MENU_TYPE = 'MENU', m.SORT_ORDER = 7, m.IS_VISIBLE = 1, m.IS_ACTIVE = 1
 WHERE m.MENU_CODE = 'STEAM_MOVE_PLAN';

INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, IS_VISIBLE, IS_ACTIVE, CREATED_AT)
SELECT 'STEAM_MOVE_PLAN', '운영 최적화', p.MENU_ID, '/steam/energy-move-plan.html', 'steam_energy', 'MENU', 7, 1, 1, NOW()
  FROM core_menu p
 WHERE p.MENU_CODE = 'STEAM_ENERGY_MGMT'
   AND NOT EXISTS (SELECT 1 FROM core_menu x WHERE x.MENU_CODE = 'STEAM_MOVE_PLAN');

-- 역할 권한 (05_submenus.sql 과 같은 6개 역할)
INSERT INTO core_role_menu (ROLE, MENU_ID)
SELECT r.ROLE, m.MENU_ID
  FROM core_menu m
  JOIN (SELECT 'ROLE_ADMIN' AS ROLE UNION ALL SELECT 'ROLE_MANAGER' UNION ALL SELECT 'ROLE_USER'
        UNION ALL SELECT 'ROLE_STEAM_MANAGER' UNION ALL SELECT 'ROLE_STEAM_BOILER' UNION ALL SELECT 'ROLE_STEAM_KNE') r
 WHERE m.MENU_CODE = 'STEAM_MOVE_PLAN'
   AND NOT EXISTS (SELECT 1 FROM core_role_menu rm WHERE rm.ROLE = r.ROLE AND rm.MENU_ID = m.MENU_ID);
