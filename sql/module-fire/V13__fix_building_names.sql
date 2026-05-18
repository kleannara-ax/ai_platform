-- =============================================================================
-- V13: 건물명 보정 (잘못된 건물명 수정)
-- 실행 대상: platform_db
-- 최종 업데이트: 2026-05-14
-- =============================================================================

-- -----------------------------------------------------------------------
-- 1. "저장" → "제지" 건물명 수정
--    원인: 최초 데이터 입력 시 오타
-- -----------------------------------------------------------------------
UPDATE building SET BUILDING_NAME = '제지1,2호기' WHERE BUILDING_NAME = '저장 1,2호기';
UPDATE building SET BUILDING_NAME = '제지1,2호기' WHERE BUILDING_NAME = '저장1,2호기';
UPDATE building SET BUILDING_NAME = '제지1,2호기' WHERE BUILDING_NAME = '저장12호기';
UPDATE building SET BUILDING_NAME = '제지1,2호기' WHERE BUILDING_NAME = '저장 12호기';
UPDATE building SET BUILDING_NAME = '제지3호기'   WHERE BUILDING_NAME = '저장 3호기';
UPDATE building SET BUILDING_NAME = '제지3호기'   WHERE BUILDING_NAME = '저장3호기';

-- -----------------------------------------------------------------------
-- 2. "현장저장" → "화장지" 건물명 수정
--    원인: 잘못된 건물명 등록 (현장저장은 실제 존재하지 않는 건물)
--    올바른 이름: 화장지 3,6호기 / 화장지 4,5호기
-- -----------------------------------------------------------------------
UPDATE building SET BUILDING_NAME = '화장지 3,6호기' WHERE BUILDING_NAME = '현장저장 3,6호기';
UPDATE building SET BUILDING_NAME = '화장지 3,6호기' WHERE BUILDING_NAME = '현장저장3,6호기';
UPDATE building SET BUILDING_NAME = '화장지 4,5호기' WHERE BUILDING_NAME = '현장저장 4,5호기';
UPDATE building SET BUILDING_NAME = '화장지 4,5호기' WHERE BUILDING_NAME = '현장저장4,5호기';

-- -----------------------------------------------------------------------
-- 확인
-- -----------------------------------------------------------------------
-- SELECT BUILDING_ID, BUILDING_NAME FROM building ORDER BY BUILDING_ID;
