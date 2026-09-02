-- ============================================================
--  module-safety : 분류 2단계(대분류/중분류) 고정 구조
--
--  [배경] safety_manual_category 는 깊이 제한이 없는 자기참조 트리였다.
--  요구사항에 따라 정확히 2단계(대분류 → 중분류)로 고정하고,
--  매뉴얼은 항상 중분류(2단계)에만 등록되도록 한다.
--  (대/중분류 이름 자체는 사용자가 화면에서 자유롭게 추가/수정한다.
--   "화장지생산팀", "초지 5호기" 는 예시일 뿐 스키마에 값으로 고정하지 않는다.)
--
--  변경 내용:
--   1) safety_manual_category 에 LEVEL_NO(1=대분류, 2=중분류) 컬럼 추가
--   2) 기존 데이터의 LEVEL_NO 를 PARENT_ID 체인을 따라 역산하여 채움
--   3) 중분류(2단계)가 아닌 분류에 직접 매뉴얼이 남아있으면(=기존 평면 데이터),
--      "미분류" 중분류를 자동 생성해 그 아래로 옮긴다(매뉴얼 데이터 보존 최우선).
--      실제 재분류(분류 이름 확정)는 이후 화면에서 관리자가 진행한다.
--
--  ※ 예전에는 소분류(3단계)까지 뒀으나 쓰지 않기로 해서 중분류까지만 만든다.
--    이미 3단계로 만들어진 DB 는 09_two_level_categories.sql 이 정리한다.
--
--  재실행 안전: 컬럼은 IF NOT EXISTS, 데이터 이동은 조건에 해당하는 행이 없으면 아무 것도 하지 않음.
-- ============================================================

SET NAMES utf8mb4;

-- ────────────────────────────────────────────
-- 1) LEVEL_NO 컬럼 추가
-- ────────────────────────────────────────────
ALTER TABLE safety_manual_category
    ADD COLUMN IF NOT EXISTS LEVEL_NO TINYINT NOT NULL DEFAULT 1 COMMENT '분류 단계(1=대분류, 2=중분류)' AFTER PARENT_ID;

ALTER TABLE safety_manual_category
    ADD INDEX IF NOT EXISTS IDX_SAFETY_CATEGORY_LEVEL (LEVEL_NO);

-- ────────────────────────────────────────────
-- 2) 기존 데이터 LEVEL_NO 역산 (최상위=1, 그 자식=2)
-- ────────────────────────────────────────────
UPDATE safety_manual_category SET LEVEL_NO = 1 WHERE PARENT_ID IS NULL;

UPDATE safety_manual_category c
  JOIN safety_manual_category p ON c.PARENT_ID = p.CATEGORY_ID
   SET c.LEVEL_NO = 2
 WHERE p.PARENT_ID IS NULL;

-- ────────────────────────────────────────────
-- 3) 대분류에 매뉴얼이 직접 붙어 있으면 "미분류" 중분류를 만들어 옮긴다
--    (2단계 구조라 한 단계만 내려가면 된다)
-- ────────────────────────────────────────────
DROP PROCEDURE IF EXISTS safety_move_manuals_to_leaf;

DELIMITER $$
CREATE PROCEDURE safety_move_manuals_to_leaf()
BEGIN
  DECLARE v_done BOOLEAN DEFAULT FALSE;
  DECLARE v_cat_id BIGINT;
  DECLARE v_leaf_id BIGINT;

  DECLARE cur CURSOR FOR
    SELECT DISTINCT c.CATEGORY_ID
      FROM safety_manual_category c
      JOIN safety_manual m ON m.CATEGORY_ID = c.CATEGORY_ID AND m.DELETED_YN = 'N'
     WHERE c.LEVEL_NO <> 2
       AND c.DELETED_YN = 'N';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_cat_id;
    IF v_done THEN LEAVE read_loop; END IF;

    -- 이 대분류 아래 "미분류" 중분류를 찾고, 없으면 만든다
    SET v_leaf_id = NULL;
    SELECT CATEGORY_ID INTO v_leaf_id FROM safety_manual_category
     WHERE PARENT_ID = v_cat_id AND NAME = '미분류' AND DELETED_YN = 'N' LIMIT 1;

    IF v_leaf_id IS NULL THEN
      INSERT INTO safety_manual_category (NAME, PARENT_ID, LEVEL_NO, SORT_ORDER, CREATED_AT, UPDATED_AT, CREATED_BY)
      VALUES ('미분류', v_cat_id, 2, 0, NOW(), NOW(), 'SYSTEM');
      SET v_leaf_id = LAST_INSERT_ID();
    END IF;

    UPDATE safety_manual SET CATEGORY_ID = v_leaf_id
     WHERE CATEGORY_ID = v_cat_id AND DELETED_YN = 'N';
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL safety_move_manuals_to_leaf();
DROP PROCEDURE IF EXISTS safety_move_manuals_to_leaf;

SELECT '--- 05_category_level.sql 실행 완료 ---' AS '';
SELECT CATEGORY_ID, NAME, PARENT_ID, LEVEL_NO, SORT_ORDER
  FROM safety_manual_category WHERE DELETED_YN = 'N' ORDER BY LEVEL_NO, CATEGORY_ID;
