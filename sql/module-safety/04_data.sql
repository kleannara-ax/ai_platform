-- ============================================================
--  module-safety : 초기 데이터(선택) — 데모/테스트용 기본 분류 1건
--  재실행 안전 (이미 있으면 건너뜀).
-- ============================================================

INSERT INTO safety_manual_category (NAME, PARENT_ID, SORT_ORDER, CREATED_AT, UPDATED_AT, CREATED_BY)
SELECT '안전작업방식 매뉴얼', NULL, 1, NOW(), NOW(), 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM safety_manual_category WHERE NAME = '안전작업방식 매뉴얼' AND PARENT_ID IS NULL
);
