-- ============================================================
--  module-safety : 분류 3단계(대분류/중분류/소분류) 고정 구조 마이그레이션
--
--  [배경] 기존 safety_manual_category 는 깊이 제한이 없는 자기참조 트리였다.
--  요구사항에 따라 정확히 3단계(대분류 → 중분류 → 소분류)로 고정하고,
--  매뉴얼은 항상 소분류(3단계)에만 등록되도록 한다.
--  (대/중/소분류 이름 자체는 사용자가 화면에서 자유롭게 추가/수정한다.
--   "제지/화장지/패드", "3호기/4호기/5호기", "설비/안전/작업/원료" 는 예시일 뿐
--   스키마에 값으로 고정하지 않는다.)
--
--  변경 내용:
--   1) safety_manual_category 에 LEVEL_NO(1=대분류,2=중분류,3=소분류) 컬럼 추가
--   2) 기존 데이터의 LEVEL_NO 를 PARENT_ID 체인을 따라 역산하여 채움
--   3) 소분류(3단계)가 아닌 분류에 직접 매뉴얼이 남아있으면(=기존 평면 데이터),
--      "미분류" 중/소분류를 자동 생성해 그 아래로 옮긴다(매뉴얼 데이터 보존 최우선).
--      실제 재분류(대분류/중분류/소분류 이름 확정)는 이후 화면에서 관리자가 진행한다.
--
--  재실행 안전: 컬럼은 IF NOT EXISTS, 데이터 이동은 조건에 해당하는 행이 없으면 아무 것도 하지 않음.
-- ============================================================

SET NAMES utf8mb4;

-- ────────────────────────────────────────────
-- 1) LEVEL_NO 컬럼 추가
-- ────────────────────────────────────────────
ALTER TABLE safety_manual_category
    ADD COLUMN IF NOT EXISTS LEVEL_NO TINYINT NOT NULL DEFAULT 1 COMMENT '분류 단계(1=대분류,2=중분류,3=소분류)' AFTER PARENT_ID;

ALTER TABLE safety_manual_category
    ADD INDEX IF NOT EXISTS IDX_SAFETY_CATEGORY_LEVEL (LEVEL_NO);

-- ────────────────────────────────────────────
-- 2) 기존 데이터 LEVEL_NO 역산 (최상위=1, 그 자식=2, 그 자식=3)
-- ────────────────────────────────────────────
UPDATE safety_manual_category SET LEVEL_NO = 1 WHERE PARENT_ID IS NULL;

UPDATE safety_manual_category c
  JOIN safety_manual_category p ON c.PARENT_ID = p.CATEGORY_ID
   SET c.LEVEL_NO = 2
 WHERE p.PARENT_ID IS NULL;

UPDATE safety_manual_category c
  JOIN safety_manual_category p ON c.PARENT_ID = p.CATEGORY_ID
   SET c.LEVEL_NO = 3
 WHERE p.LEVEL_NO = 2;

-- ────────────────────────────────────────────
-- 3) 소분류(3단계)가 아닌 분류에 직접 매뉴얼이 남아있으면 "미분류" 경로로 이동
--    (예: 기존 평면 구조에서 최상위 분류에 매뉴얼이 바로 달려 있던 경우)
-- ────────────────────────────────────────────
DELIMITER $$
DROP PROCEDURE IF EXISTS _safety_migrate_category_levels $$
CREATE PROCEDURE _safety_migrate_category_levels()
BEGIN
  DECLARE v_done INT DEFAULT 0;
  DECLARE v_cat_id BIGINT;
  DECLARE v_cat_level TINYINT;
  DECLARE v_next_id BIGINT;
  DECLARE v_leaf_id BIGINT;
  DECLARE cur CURSOR FOR
    SELECT DISTINCT c.CATEGORY_ID, c.LEVEL_NO
      FROM safety_manual_category c
      JOIN safety_manual m ON m.CATEGORY_ID = c.CATEGORY_ID AND m.DELETED_YN = 'N'
     WHERE c.LEVEL_NO <> 3;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_cat_id, v_cat_level;
    IF v_done THEN LEAVE read_loop; END IF;

    -- 다음 단계("미분류") 찾기, 없으면 생성
    SELECT CATEGORY_ID INTO v_next_id FROM safety_manual_category
     WHERE PARENT_ID = v_cat_id AND NAME = '미분류' AND DELETED_YN = 'N' LIMIT 1;

    IF v_next_id IS NULL THEN
      INSERT INTO safety_manual_category (NAME, PARENT_ID, LEVEL_NO, SORT_ORDER, CREATED_AT, UPDATED_AT, CREATED_BY)
      VALUES ('미분류', v_cat_id, v_cat_level + 1, 0, NOW(), NOW(), 'SYSTEM');
      SET v_next_id = LAST_INSERT_ID();
    END IF;

    IF v_cat_level + 1 = 3 THEN
      SET v_leaf_id = v_next_id;
    ELSE
      -- v_next_id 는 아직 2단계(중분류) → 그 아래 소분류("미분류")까지 한 단계 더 생성
      SELECT CATEGORY_ID INTO v_leaf_id FROM safety_manual_category
       WHERE PARENT_ID = v_next_id AND NAME = '미분류' AND DELETED_YN = 'N' LIMIT 1;
      IF v_leaf_id IS NULL THEN
        INSERT INTO safety_manual_category (NAME, PARENT_ID, LEVEL_NO, SORT_ORDER, CREATED_AT, UPDATED_AT, CREATED_BY)
        VALUES ('미분류', v_next_id, 3, 0, NOW(), NOW(), 'SYSTEM');
        SET v_leaf_id = LAST_INSERT_ID();
      END IF;
    END IF;

    UPDATE safety_manual SET CATEGORY_ID = v_leaf_id WHERE CATEGORY_ID = v_cat_id AND DELETED_YN = 'N';

    SET v_next_id = NULL;
    SET v_leaf_id = NULL;
  END LOOP;
  CLOSE cur;
END $$
DELIMITER ;

CALL _safety_migrate_category_levels();
DROP PROCEDURE _safety_migrate_category_levels;

SELECT '=== 05_category_level.sql 실행 완료 ===' AS message;
SELECT CATEGORY_ID, NAME, PARENT_ID, LEVEL_NO, SORT_ORDER FROM safety_manual_category WHERE DELETED_YN = 'N' ORDER BY LEVEL_NO, CATEGORY_ID;
