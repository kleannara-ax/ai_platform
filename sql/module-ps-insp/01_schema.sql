-- ============================================================
-- module-ps-insp: PS 지분 검사 (점보롤 지분 검사) DDL
-- Database: MariaDB 10.11+ (utf8mb4)
-- Naming: table = lower_snake_case, column = UPPER_SNAKE_CASE
-- ============================================================

-- 기존 테이블 존재 시 DROP (최초 설치만 해당, 운영 환경에서는 주석 처리)
-- DROP TABLE IF EXISTS ps_insp_inspection;

CREATE TABLE IF NOT EXISTS ps_insp_inspection (
    INSPECTION_ID   BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '검사 결과 PK (자동 증가)',
    SEQ             INT             NOT NULL DEFAULT 0       COMMENT '글로벌 시퀀스 번호 (INSERT 시 증가)',
    INSP_ITEM_GRP_CD VARCHAR(100)   NULL                     COMMENT '검사항목그룹코드',
    MATNR           VARCHAR(100)    NULL                     COMMENT '자재코드',
    MATNR_NM        VARCHAR(500)    NULL                     COMMENT '자재명',
    WERKS           VARCHAR(100)    NULL                     COMMENT '플랜트',
    MSRM_DATE       DATETIME        NULL                     COMMENT '측정일시',
    PRC_SEQNO       INT             NULL                     COMMENT '공정순번',
    LOTNR           VARCHAR(200)    NULL                     COMMENT 'LOT 번호',
    IND_BCD         VARCHAR(200)    NULL                     COMMENT '개별바코드',
    IND_BCD_SEQ     VARCHAR(100)    NULL                     COMMENT '검사 차수 (동일 자재+LOT+바코드 재검사 시 증가)',
    INSPECTED_AT    DATETIME        NULL                     COMMENT '검사 시각',
    THRESHOLD_MAX   INT             NULL DEFAULT 115         COMMENT '이진화 임계값 (0-255)',
    TOTAL_COUNT     INT             NOT NULL DEFAULT 0       COMMENT '총 지분 수',
    COVERAGE_RATIO  DECIMAL(12,6)   NULL                     COMMENT '커버리지 비율 (%)',
    DENSITY_COUNT   INT             NULL DEFAULT 0           COMMENT '밀도 지분 수 (직경 3-7px)',
    DENSITY_RATIO   DECIMAL(8,4)    NULL                     COMMENT '밀도 비율',
    SIZE_UNIFORMITY_SCORE       DECIMAL(8,4)  NULL           COMMENT '크기 균일도 점수',
    DISTRIBUTION_UNIFORMITY_SCORE DECIMAL(8,4) NULL          COMMENT '분포 균일도 점수',
    MEAN_SIZE       DECIMAL(12,4)   NULL                     COMMENT '평균 지분 크기 (px)',
    STD_SIZE        DECIMAL(12,4)   NULL                     COMMENT '표준편차 (px)',
    AUTO_COUNT      INT             NOT NULL DEFAULT 0       COMMENT '자동 검출 수',
    MANUAL_COUNT    INT             NOT NULL DEFAULT 0       COMMENT '수동 보정 수',
    REMOVED_AUTO_COUNT INT          NOT NULL DEFAULT 0       COMMENT '삭제된 자동 검출 수',
    BUCKET_UP_TO_3  INT             NULL DEFAULT 0           COMMENT '크기 버킷: ~3px 이하',
    BUCKET_UP_TO_5  INT             NULL DEFAULT 0           COMMENT '크기 버킷: ~5px 이하',
    BUCKET_UP_TO_7  INT             NULL DEFAULT 0           COMMENT '크기 버킷: ~7px 이하',
    BUCKET_OVER_7   INT             NULL DEFAULT 0           COMMENT '크기 버킷: 7px 초과',
    QUADRANT_TOP_LEFT     INT       NULL DEFAULT 0           COMMENT '사분면: 좌상',
    QUADRANT_TOP_RIGHT    INT       NULL DEFAULT 0           COMMENT '사분면: 우상',
    QUADRANT_BOTTOM_LEFT  INT       NULL DEFAULT 0           COMMENT '사분면: 좌하',
    QUADRANT_BOTTOM_RIGHT INT       NULL DEFAULT 0           COMMENT '사분면: 우하',
    OBJECT_PIXEL_COUNT    BIGINT    NULL DEFAULT 0           COMMENT '지분 총 픽셀 수',
    TOTAL_PIXELS          BIGINT    NULL DEFAULT 0           COMMENT '이미지 전체 픽셀 수',
    MANUAL_ADDED_COUNT    INT       NOT NULL DEFAULT 0       COMMENT '수동 추가 수',
    MANUAL_REMOVED_COUNT  INT       NOT NULL DEFAULT 0       COMMENT '수동 삭제 수',
    ORIGINAL_IMAGE_PATH   VARCHAR(1000) NULL                 COMMENT '원본 이미지 URL 경로',
    ORIGINAL_IMAGE_NAME   VARCHAR(500)  NULL                 COMMENT '원본 이미지 파일명',
    ORIGINAL_IMAGE_DIR    VARCHAR(1000) NULL                 COMMENT '원본 이미지 디렉토리',
    RESULT_IMAGE_PATH     VARCHAR(1000) NULL                 COMMENT '결과 이미지 URL 경로',
    RESULT_IMAGE_NAME     VARCHAR(500)  NULL                 COMMENT '결과 이미지 파일명',
    RESULT_IMAGE_DIR      VARCHAR(1000) NULL                 COMMENT '결과 이미지 디렉토리',
    OPERATOR_ID     VARCHAR(200)    NULL                     COMMENT '검사자 ID',
    OPERATOR_NM     VARCHAR(200)    NULL                     COMMENT '검사자 이름',
    DEVICE_ID       VARCHAR(200)    NULL                     COMMENT '장비 ID',
    STATUS          VARCHAR(100)    NULL                     COMMENT '검사 상태',
    CREATED_AT      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT      DATETIME        NULL                     COMMENT '수정일시 (재검사 UPDATE 시 자동 갱신)',

    PRIMARY KEY (INSPECTION_ID),
    CONSTRAINT UK_PS_INSP_MATNR_LOTNR_INDBCD UNIQUE (MATNR, LOTNR, IND_BCD)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci
  COMMENT='[module-ps-insp] PS 지분 검사 결과 (점보롤 지분 검사)';

-- ── 인덱스 ──
CREATE INDEX IDX_PS_INSP_IND_BCD       ON ps_insp_inspection (IND_BCD);
CREATE INDEX IDX_PS_INSP_LOTNR         ON ps_insp_inspection (LOTNR);
CREATE INDEX IDX_PS_INSP_MATNR         ON ps_insp_inspection (MATNR);
CREATE INDEX IDX_PS_INSP_WERKS         ON ps_insp_inspection (WERKS);
CREATE INDEX IDX_PS_INSP_MSRM_DATE     ON ps_insp_inspection (MSRM_DATE DESC);
CREATE INDEX IDX_PS_INSP_INSPECTED_AT  ON ps_insp_inspection (INSPECTED_AT DESC);
CREATE INDEX IDX_PS_INSP_OPERATOR_ID   ON ps_insp_inspection (OPERATOR_ID);
CREATE INDEX IDX_PS_INSP_STATUS        ON ps_insp_inspection (STATUS);
