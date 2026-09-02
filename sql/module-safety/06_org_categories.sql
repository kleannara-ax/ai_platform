-- ============================================================
--  06_org_categories.sql
--  안전작업 매뉴얼 — 조직(팀) 기준 대분류 일괄 등록
--
--  실행: mysql -u platform_user --default-character-set=utf8mb4 platform_db < 06_org_categories.sql
--        (utf8mb4 옵션을 빼면 한글이 mojibake 로 저장된다 — README 참고)
--
--  성격:
--    - 대분류(LEVEL_NO=1, PARENT_ID=NULL)만 등록한다. 중분류는 화면에서 추가한다.
--    - 같은 이름의 활성 대분류가 이미 있으면 건너뛴다 → 몇 번을 다시 실행해도 중복이 생기지 않는다.
--    - 기존 데이터를 지우지 않는다 (DROP/TRUNCATE/무조건 DELETE 없음).
--      이미 있던 분류를 정리하려면 소프트 삭제(DELETED_YN='Y')로 따로 처리한다.
-- ============================================================

-- 대분류 12개. SORT_ORDER 는 화면 표시 순서.
INSERT INTO safety_manual_category (NAME, PARENT_ID, LEVEL_NO, SORT_ORDER, DELETED_YN, CREATED_AT, CREATED_BY)
SELECT t.NAME, NULL, 1, t.SORT_ORDER, 'N', NOW(), 'system'
FROM (
    SELECT 'HL물류팀'      AS NAME,  1 AS SORT_ORDER
    UNION ALL SELECT 'HL 품질보증팀',  2
    UNION ALL SELECT 'PS 물류팀',      3
    UNION ALL SELECT 'PS 품질보증팀',  4
    UNION ALL SELECT '공무팀',         5
    UNION ALL SELECT '구매1팀',        6
    UNION ALL SELECT '기술혁신연구소',  7
    UNION ALL SELECT '전기팀',         8
    UNION ALL SELECT '제지생산팀',     9
    UNION ALL SELECT '화장지생산팀',  10
    UNION ALL SELECT '패드생산팀',    11
    UNION ALL SELECT '환경에너지팀',  12
) AS t
WHERE NOT EXISTS (
    SELECT 1 FROM safety_manual_category c
    WHERE c.NAME = t.NAME
      AND c.PARENT_ID IS NULL
      AND c.DELETED_YN = 'N'
);

-- 확인용 출력
SELECT '--- 등록된 대분류 ---' AS '';
SELECT CATEGORY_ID, NAME, LEVEL_NO, SORT_ORDER
FROM safety_manual_category
WHERE PARENT_ID IS NULL AND DELETED_YN = 'N'
ORDER BY SORT_ORDER, CATEGORY_ID;
