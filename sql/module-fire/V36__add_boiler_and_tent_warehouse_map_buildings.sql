SET NAMES utf8mb4;

-- ============================================================
-- V36: 신규 보일러/천막창고 메인 도면 구역의 층별 도면 연동용 건물 마스터 추가
--   - 메인 도면 polygon data-name과 동일한 building.BUILDING_NAME 보장
--   - 각 건물은 1층 도면만 제공
--   - 기존 row가 있으면 중복 삽입하지 않음
-- ============================================================

INSERT INTO building (BUILDING_NAME, IS_ACTIVE)
SELECT '60톤 보일러', 1
WHERE NOT EXISTS (
    SELECT 1 FROM building WHERE BUILDING_NAME = '60톤 보일러'
);

INSERT INTO building (BUILDING_NAME, IS_ACTIVE)
SELECT '20톤 보일러', 1
WHERE NOT EXISTS (
    SELECT 1 FROM building WHERE BUILDING_NAME = '20톤 보일러'
);

INSERT INTO building (BUILDING_NAME, IS_ACTIVE)
SELECT '천막창고 5,6동', 1
WHERE NOT EXISTS (
    SELECT 1 FROM building WHERE BUILDING_NAME = '천막창고 5,6동'
);
