-- ============================================================
--  [module-epr] EPR 포장재 중량 신고 — 업무 테이블 4개
--
--  epr_part_category        파트 코드 (제품코드 앞 3자리 → 신고 파트명)
--  epr_product_master       제품 마스터 (입수량 · BAG/BOX 포장중량(g) · 자사/타사)
--  epr_annual_sales         연도별 판매 실적 집계 (신고 연도당 1건)
--  epr_annual_sales_detail  연도별 판매 실적 SKU 상세 (1:N)
--
--  · 재실행 안전 (CREATE TABLE IF NOT EXISTS). DROP · TRUNCATE · 무조건 DELETE 없음.
--  · core 테이블(core_user 등)에는 FK 를 걸지 않는다. CREATED_BY 등은 사용자 ID 값만 저장한다.
--  · 원래 프로젝트의 epr_user · epr_user_identity(자체 로그인)는 만들지 않는다 — 플랫폼 계정을 쓴다.
--  · 원래 프로젝트의 Flyway(V1~V4)는 쓰지 않는다. 이 파일이 그 스키마를 대신한다.
-- ============================================================

SET NAMES utf8mb4;

-- 1) 파트 코드
CREATE TABLE IF NOT EXISTS epr_part_category (
    PART_CATEGORY_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '파트 코드 ID',
    PART_PREFIX       VARCHAR(10)   NOT NULL                COMMENT '파트코드 (제품코드 앞 3자리, 영문 대문자/숫자)',
    PART_NAME         VARCHAR(255)  NOT NULL                COMMENT '파트명 (신고 파트명, 자동 생성 시 미분류(코드))',

    CREATED_AT        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY        BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT        DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY        BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN        CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT        DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY        BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (PART_CATEGORY_ID),
    CONSTRAINT UK_EPR_PART_CATEGORY_PREFIX UNIQUE (PART_PREFIX),
    INDEX IDX_EPR_PART_CATEGORY_NAME (PART_NAME),
    INDEX IDX_EPR_PART_CATEGORY_DELETED_YN (DELETED_YN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-epr] 파트 코드';

-- 2) 제품 마스터
CREATE TABLE IF NOT EXISTS epr_product_master (
    PRODUCT_ID        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '제품 마스터 ID',
    PRODUCT_CODE      VARCHAR(30)   NOT NULL                COMMENT '제품코드 (trim · 대문자로 저장)',
    PART_PREFIX       VARCHAR(10)   NOT NULL                COMMENT '파트코드 (제품코드 앞 3자리, 자동)',
    PRODUCT_NAME      VARCHAR(255)  NOT NULL                COMMENT '제품명',
    EA_PER_BAG        INT           NOT NULL DEFAULT 0      COMMENT '입수량 EA/BAG',
    BAG_PER_BOX       INT           NOT NULL DEFAULT 0      COMMENT '입수량 BAG/BOX',
    EA_PER_BOX        INT           NOT NULL DEFAULT 0      COMMENT '입수량 EA/BOX',
    BAG_PER_PLT       INT           NOT NULL DEFAULT 0      COMMENT '적재량 BAG/PLT',
    BOX_PER_PLT       INT           NOT NULL DEFAULT 0      COMMENT '적재량 BOX/PLT',
    BAG_WEIGHT        DECIMAL(15,4) NOT NULL DEFAULT 0      COMMENT 'BAG 포장중량(g)',
    BOX_WEIGHT        DECIMAL(15,4) NOT NULL DEFAULT 0      COMMENT 'BOX 포장중량(g)',
    COMPANY_TYPE      VARCHAR(10)   NOT NULL DEFAULT '자사' COMMENT '구분 (자사/타사)',

    CREATED_AT        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY        BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT        DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY        BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN        CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT        DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY        BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (PRODUCT_ID),
    CONSTRAINT UK_EPR_PRODUCT_MASTER_CODE UNIQUE (PRODUCT_CODE),
    INDEX IDX_EPR_PRODUCT_MASTER_PART (PART_PREFIX),
    INDEX IDX_EPR_PRODUCT_MASTER_NAME (PRODUCT_NAME),
    INDEX IDX_EPR_PRODUCT_MASTER_DELETED_YN (DELETED_YN),
    CONSTRAINT FK_EPR_PRODUCT_MASTER_PART FOREIGN KEY (PART_PREFIX)
        REFERENCES epr_part_category (PART_PREFIX)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-epr] 제품 마스터';

-- 3) 연도별 판매 실적 집계
CREATE TABLE IF NOT EXISTS epr_annual_sales (
    ANNUAL_SALES_ID          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '연도별 판매 실적 ID',
    REPORT_YEAR              INT           NOT NULL                COMMENT '신고 연도',
    ACCEPTED_ROWS            INT           NOT NULL DEFAULT 0      COMMENT '계산에 쓴 엑셀 행 수',
    UNIQUE_SKU_COUNT         INT           NOT NULL DEFAULT 0      COMMENT '계산 SKU 수 (중복 코드 합산 후)',
    UNREGISTERED_SKU_COUNT   INT           NOT NULL DEFAULT 0      COMMENT '미등록 제품코드 수',
    MISSING_WEIGHT_SKU_COUNT INT           NOT NULL DEFAULT 0      COMMENT '중량 미입력 SKU 수',
    TOTAL_SALES_EA           DECIMAL(18,4) NOT NULL DEFAULT 0      COMMENT '총 판매수량(EA)',
    TOTAL_REPORT_WEIGHT_KG   DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '총 신고중량(kg)',
    CALCULATED_AT            DATETIME(6)   NOT NULL                COMMENT '계산 일시',

    CREATED_AT               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY               BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT               DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY               BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN               CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT               DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY               BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (ANNUAL_SALES_ID),
    CONSTRAINT UK_EPR_ANNUAL_SALES_YEAR UNIQUE (REPORT_YEAR),
    INDEX IDX_EPR_ANNUAL_SALES_DELETED_YN (DELETED_YN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-epr] 연도별 판매 실적 집계';

-- 4) 연도별 판매 실적 SKU 상세 — 계산 당시의 입수량 · 포장중량 · 신고중량을 그대로 남긴다.
--    같은 연도를 다시 올리면 기존 행은 DELETED_YN='Y' 로 바꾸고 새로 쓴다.
CREATE TABLE IF NOT EXISTS epr_annual_sales_detail (
    ANNUAL_SALES_DETAIL_ID BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'SKU 상세 ID',
    ANNUAL_SALES_ID        BIGINT        NOT NULL                COMMENT '연도별 판매 실적 ID',
    REPORT_YEAR            INT           NOT NULL                COMMENT '신고 연도',
    PRODUCT_CODE           VARCHAR(30)   NOT NULL                COMMENT '제품코드',
    PART_PREFIX            VARCHAR(10)   NOT NULL                COMMENT '파트코드',
    PART_NAME              VARCHAR(255)  NOT NULL                COMMENT '계산 당시 파트명',
    MASTER_PRODUCT_NAME    VARCHAR(255)  NOT NULL DEFAULT ''     COMMENT '제품명(마스터)',
    UPLOADED_PRODUCT_NAME  VARCHAR(255)  NOT NULL DEFAULT ''     COMMENT '업로드 제품명',
    SALES_EA               DECIMAL(18,4) NOT NULL DEFAULT 0      COMMENT '판매수량(EA)',
    EA_PER_BAG             INT           NOT NULL DEFAULT 0      COMMENT '계산 당시 EA/BAG',
    BAG_WEIGHT_G           DECIMAL(15,4) NOT NULL DEFAULT 0      COMMENT '계산 당시 BAG 포장중량(g)',
    CONVERTED_BAG          DECIMAL(18,6) NOT NULL DEFAULT 0      COMMENT '환산 BAG 수량',
    BAG_REPORT_WEIGHT_KG   DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT 'BAG 신고중량(kg)',
    EA_PER_BOX             INT           NOT NULL DEFAULT 0      COMMENT '계산 당시 EA/BOX',
    BOX_WEIGHT_G           DECIMAL(15,4) NOT NULL DEFAULT 0      COMMENT '계산 당시 BOX 포장중량(g)',
    CONVERTED_BOX          DECIMAL(18,6) NOT NULL DEFAULT 0      COMMENT '환산 BOX 수량',
    BOX_REPORT_WEIGHT_KG   DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT 'BOX 신고중량(kg)',
    TOTAL_REPORT_WEIGHT_KG DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT 'SKU 총 신고중량(kg)',
    STATUS                 VARCHAR(30)   NOT NULL                COMMENT '상태 (정상/미등록코드/중량미입력)',
    SOURCE_ROWS            INT           NOT NULL DEFAULT 1      COMMENT '합산된 엑셀 행 수',

    CREATED_AT             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY             BIGINT        NULL COMMENT '생성자 사용자 ID',
    UPDATED_AT             DATETIME      NULL COMMENT '수정일시',
    UPDATED_BY             BIGINT        NULL COMMENT '수정자 사용자 ID',
    DELETED_YN             CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
    DELETED_AT             DATETIME      NULL COMMENT '삭제일시',
    DELETED_BY             BIGINT        NULL COMMENT '삭제자 사용자 ID',

    PRIMARY KEY (ANNUAL_SALES_DETAIL_ID),
    INDEX IDX_EPR_ANNUAL_DETAIL_SALES (ANNUAL_SALES_ID, DELETED_YN),
    INDEX IDX_EPR_ANNUAL_DETAIL_YEAR_PART (REPORT_YEAR, PART_PREFIX),
    INDEX IDX_EPR_ANNUAL_DETAIL_CODE (PRODUCT_CODE),
    CONSTRAINT FK_EPR_ANNUAL_DETAIL_SALES FOREIGN KEY (ANNUAL_SALES_ID)
        REFERENCES epr_annual_sales (ANNUAL_SALES_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[module-epr] 연도별 판매 실적 SKU 상세';
