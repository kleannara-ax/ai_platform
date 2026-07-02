SET NAMES utf8mb4;

-- ============================================================
-- V25: 패드동 천막창고 메인 도면 구역의 층별 도면 연동용 건물 마스터 추가
--   - 메인 도면 polygon data-name과 동일한 building.BUILDING_NAME 보장
--   - 기존 row가 있으면 중복 삽입하지 않음
-- ============================================================

INSERT INTO building (BUILDING_NAME, IS_ACTIVE)
SELECT '패드동 천막창고', 1
WHERE NOT EXISTS (
    SELECT 1 FROM building WHERE BUILDING_NAME = '패드동 천막창고'
);
