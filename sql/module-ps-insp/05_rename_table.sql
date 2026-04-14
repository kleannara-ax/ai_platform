-- ============================================================
-- module-ps-insp: 테이블명 변경 마이그레이션
-- ps_insp_inspection -> ps_inspection
-- ============================================================

-- 1. 테이블명 변경
ALTER TABLE ps_insp_inspection RENAME TO ps_inspection;

-- 2. 기존 인덱스 삭제 후 재생성 (인덱스명 정리)
DROP INDEX IF EXISTS IDX_PS_INSP_IND_BCD       ON ps_inspection;
DROP INDEX IF EXISTS IDX_PS_INSP_LOTNR         ON ps_inspection;
DROP INDEX IF EXISTS IDX_PS_INSP_MATNR         ON ps_inspection;
DROP INDEX IF EXISTS IDX_PS_INSP_WERKS         ON ps_inspection;
DROP INDEX IF EXISTS IDX_PS_INSP_MSRM_DATE     ON ps_inspection;
DROP INDEX IF EXISTS IDX_PS_INSP_INSPECTED_AT  ON ps_inspection;
DROP INDEX IF EXISTS IDX_PS_INSP_OPERATOR_ID   ON ps_inspection;
DROP INDEX IF EXISTS IDX_PS_INSP_STATUS        ON ps_inspection;

CREATE INDEX IDX_PS_INSP_IND_BCD       ON ps_inspection (IND_BCD);
CREATE INDEX IDX_PS_INSP_LOTNR         ON ps_inspection (LOTNR);
CREATE INDEX IDX_PS_INSP_MATNR         ON ps_inspection (MATNR);
CREATE INDEX IDX_PS_INSP_WERKS         ON ps_inspection (WERKS);
CREATE INDEX IDX_PS_INSP_MSRM_DATE     ON ps_inspection (MSRM_DATE DESC);
CREATE INDEX IDX_PS_INSP_INSPECTED_AT  ON ps_inspection (INSPECTED_AT DESC);
CREATE INDEX IDX_PS_INSP_OPERATOR_ID   ON ps_inspection (OPERATOR_ID);
CREATE INDEX IDX_PS_INSP_STATUS        ON ps_inspection (STATUS);
