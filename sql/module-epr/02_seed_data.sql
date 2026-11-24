-- ============================================================
--  [module-epr] 기본 파트 코드 16개
--
--  원래 프로젝트가 기동할 때 넣던 기본 파트 매핑(PartCategorySeeder)을 SQL 로 옮긴 것.
--  플랫폼 모듈은 기동 시 데이터를 넣지 않는다.
--
--  · 재실행 안전 — 이미 있는 파트코드는 건너뛴다 (운영자가 바꾼 파트명을 덮어쓰지 않는다).
--  · 여기에 없는 코드는 제품을 등록할 때 "미분류(코드)" 로 자동 생성된다.
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO epr_part_category (PART_PREFIX, PART_NAME, CREATED_AT) VALUES
    ('FRT', '화장지 제품',   NOW()),
    ('FKT', '키친타올 제품', NOW()),
    ('SKT', '키친타올 상품', NOW()),
    ('SRT', '화장지 상품',   NOW()),
    ('SWT', '물티슈',        NOW()),
    ('SBW', '유아 물티슈',   NOW()),
    ('SAF', '기타',          NOW()),
    ('FSN', '생리대 제품',   NOW()),
    ('SSN', '생리대 상품',   NOW()),
    ('FBD', '기저귀 제품',   NOW()),
    ('SBD', '기저귀 상품',   NOW()),
    ('SAD', '메디 상품',     NOW()),
    ('HKT', '원단 판매',     NOW()),
    ('HOF', '원단 판매',     NOW()),
    ('HRT', '원단 판매',     NOW()),
    ('FET', '선물세트',      NOW());

SELECT COUNT(*) AS EPR_파트코드_수 FROM epr_part_category WHERE DELETED_YN = 'N';
