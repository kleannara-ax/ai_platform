-- ============================================================
--  09_two_level_categories.sql
--  분류를 3단계(대/중/소)에서 2단계(대/중)로 줄인다
--
--  실행: mysql -u platform_user --default-character-set=utf8mb4 platform_db < 09_two_level_categories.sql
--
--  배경:
--    소분류를 쓰지 않기로 해서 중분류까지만 두고, 매뉴얼은 중분류에 바로 붙인다.
--
--  하는 일:
--    1) 소분류(LEVEL_NO=3)에 달려 있던 매뉴얼을 그 부모인 중분류로 옮긴다
--    2) 소분류를 소프트 삭제한다 (DELETED_YN='Y')
--
--  주의:
--    행을 물리 삭제하지 않는다. 매뉴얼이 참조하던 분류를 지워 버리면 되돌릴 수 없고,
--    운영 안전 규칙상 DROP/TRUNCATE/무조건 DELETE 를 쓰지 않는다.
--    되살리려면 DELETED_YN='N' 으로 돌리고 매뉴얼의 CATEGORY_ID 를 다시 지정하면 된다.
--
--  재실행 안전: 옮길 매뉴얼이 없고 살아 있는 소분류가 없으면 아무 것도 하지 않는다.
-- ============================================================

SET NAMES utf8mb4;

SELECT '--- 실행 전 ---' AS '';
SELECT LEVEL_NO, COUNT(*) AS 분류수 FROM safety_manual_category WHERE DELETED_YN = 'N' GROUP BY LEVEL_NO;

-- ────────────────────────────────────────────
-- 1) 소분류에 붙어 있던 매뉴얼을 부모(중분류)로 옮긴다
--    부모가 이미 지워졌다면 옮기지 않는다(고아 방지) — 그런 매뉴얼은 아래 조회로 확인한다.
-- ────────────────────────────────────────────
UPDATE safety_manual m
  JOIN safety_manual_category c ON c.CATEGORY_ID = m.CATEGORY_ID
  JOIN safety_manual_category p ON p.CATEGORY_ID = c.PARENT_ID AND p.DELETED_YN = 'N'
   SET m.CATEGORY_ID = p.CATEGORY_ID,
       m.UPDATED_AT  = NOW(),
       m.UPDATED_BY  = 'system'
 WHERE m.DELETED_YN = 'N'
   AND c.LEVEL_NO = 3;

-- ────────────────────────────────────────────
-- 2) 소분류를 소프트 삭제
-- ────────────────────────────────────────────
UPDATE safety_manual_category
   SET DELETED_YN = 'Y',
       DELETED_AT = NOW(),
       DELETED_BY = 'system'
 WHERE LEVEL_NO = 3
   AND DELETED_YN = 'N';

SELECT '--- 실행 후 ---' AS '';
SELECT LEVEL_NO, COUNT(*) AS 분류수 FROM safety_manual_category WHERE DELETED_YN = 'N' GROUP BY LEVEL_NO;

SELECT '--- 분류 단계별 매뉴얼 수 (3단계가 남아 있으면 안 된다) ---' AS '';
SELECT c.LEVEL_NO, COUNT(*) AS 매뉴얼수
  FROM safety_manual m JOIN safety_manual_category c ON c.CATEGORY_ID = m.CATEGORY_ID
 WHERE m.DELETED_YN = 'N'
 GROUP BY c.LEVEL_NO;

SELECT '--- 삭제된 분류를 참조하는 매뉴얼 (있으면 화면에서 분류를 다시 지정해야 한다) ---' AS '';
SELECT m.MANUAL_ID, m.TITLE
  FROM safety_manual m JOIN safety_manual_category c ON c.CATEGORY_ID = m.CATEGORY_ID
 WHERE m.DELETED_YN = 'N' AND c.DELETED_YN = 'Y';
