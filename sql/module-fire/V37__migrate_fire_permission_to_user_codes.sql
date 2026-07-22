SET NAMES utf8mb4;

-- ============================================================
-- V37: FIRE_PERM 권한자를 사용자별 코드값으로 전환
--
-- 기존: FIRE_ADMIN.EXTRA_VALUE1 = 로그인 ID 목록(콤마 구분)
-- 변경: FIRE_PERM 하위의 활성 CODE 한 건이 권한자 로그인 ID 한 명을 의미
--         CODE_NAME은 공통코드 관리 화면에서 표시할 사용자명으로 관리
-- ============================================================

DELIMITER //

CREATE PROCEDURE migrate_fire_perm_to_user_codes()
BEGIN
    DECLARE v_group_id BIGINT DEFAULT NULL;
    DECLARE v_user_ids TEXT DEFAULT '';
    DECLARE v_user_id VARCHAR(50) DEFAULT '';
    DECLARE v_comma_pos INT DEFAULT 0;

    SELECT g.GROUP_ID, COALESCE(d.EXTRA_VALUE1, '')
      INTO v_group_id, v_user_ids
      FROM code_group g
      LEFT JOIN code_detail d
        ON d.GROUP_ID = g.GROUP_ID
       AND d.CODE = 'FIRE_ADMIN'
     WHERE g.GROUP_CODE = 'FIRE_PERM'
     LIMIT 1;

    IF v_group_id IS NOT NULL THEN
        WHILE CHAR_LENGTH(TRIM(v_user_ids)) > 0 DO
            SET v_comma_pos = INSTR(v_user_ids, ',');
            IF v_comma_pos = 0 THEN
                SET v_user_id = TRIM(v_user_ids);
                SET v_user_ids = '';
            ELSE
                SET v_user_id = TRIM(SUBSTRING(v_user_ids, 1, v_comma_pos - 1));
                SET v_user_ids = SUBSTRING(v_user_ids, v_comma_pos + 1);
            END IF;

            IF v_user_id <> '' THEN
                INSERT INTO code_detail (
                    GROUP_ID, CODE, CODE_NAME, DESCRIPTION, EXTRA_VALUE1, EXTRA_VALUE2, IS_ACTIVE, SORT_ORDER
                ) VALUES (
                    v_group_id, UPPER(v_user_id), v_user_id,
                    '소방시설관리 권한 사용자 로그인 ID', NULL, NULL, 1, 100
                )
                ON DUPLICATE KEY UPDATE
                    CODE_NAME = VALUES(CODE_NAME),
                    DESCRIPTION = VALUES(DESCRIPTION),
                    EXTRA_VALUE1 = NULL,
                    EXTRA_VALUE2 = NULL,
                    IS_ACTIVE = 1,
                    SORT_ORDER = VALUES(SORT_ORDER);
            END IF;
        END WHILE;

        -- 레거시 목록 행은 조회/권한 대상에서 제외한다.
        UPDATE code_detail
           SET EXTRA_VALUE1 = NULL,
               EXTRA_VALUE2 = NULL,
               IS_ACTIVE = 0,
               DESCRIPTION = '레거시 권한 목록 - 사용자별 코드값 방식으로 전환됨'
         WHERE GROUP_ID = v_group_id
           AND CODE = 'FIRE_ADMIN';
    END IF;
END //

CALL migrate_fire_perm_to_user_codes() //
DROP PROCEDURE migrate_fire_perm_to_user_codes //

DELIMITER ;
