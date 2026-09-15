-- 09_service_request_return_type_column.sql
-- IP 변경/생성 요청에 "반납" 기능 추가 — service_request.RETURN_TYPE 컬럼 추가.
-- 요청목록(IP_KIND)에 신규 값 PC_RETURN(반납)이 추가되며, 이 값일 때만 RETURN_TYPE(RETURN:반납/RECLAIM:회수)이 사용된다.
-- IP_KIND 컬럼 자체는 이미 varchar(20)이라 별도 변경 없이 새 코드값(PC_RETURN, 9자)을 담을 수 있다.
-- idempotent: INFORMATION_SCHEMA 체크 후 없을 때만 ALTER (재실행 안전).

SET NAMES utf8mb4;

SET @has_return_type_column := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'service_request'
      AND COLUMN_NAME = 'RETURN_TYPE'
);
SET @sql := IF(@has_return_type_column = 0,
    "ALTER TABLE service_request ADD COLUMN RETURN_TYPE VARCHAR(20) DEFAULT NULL COMMENT '반납유형 (RETURN:반납/RECLAIM:회수) — 요청목록이 PC_RETURN(반납) 일 때' AFTER IP_KIND",
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
