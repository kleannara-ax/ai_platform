-- ============================================================
-- V40: 이산화탄소소화기 교체주기 99년 고정 (기존 데이터 보정)
--      이산화탄소소화기는 반영구적으로 사용하므로 교체 주기를
--      99년으로 고정한다. 다른 소화기 종류(분말소화기 등)는
--      영향을 받지 않는다.
--      애플리케이션 로직(Extinguisher 엔티티)에도 동일 규칙이
--      적용되어 있어, 이 스크립트는 이미 등록된 기존 데이터를
--      일괄 보정하기 위한 것이다.
-- ============================================================

SET NAMES utf8mb4;

UPDATE extinguisher
SET REPLACEMENT_CYCLE_YEARS = 99,
    REPLACEMENT_DUE_DATE = DATE_ADD(MANUFACTURE_DATE, INTERVAL 99 YEAR)
WHERE EXTINGUISHER_TYPE = '이산화탄소소화기'
  AND (REPLACEMENT_CYCLE_YEARS <> 99 OR REPLACEMENT_CYCLE_YEARS IS NULL);
