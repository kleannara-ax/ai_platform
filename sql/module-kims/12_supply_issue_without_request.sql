-- 12_supply_issue_without_request.sql
-- 소모품 지급(출고)을 업무요청 없이도 등록할 수 있도록 supply_issue 변경.
--   - REQUEST_ID: NOT NULL → NULL 허용 (업무요청 없이 '요청자명'만으로 지급)
--   - REQUESTER_NAME 컬럼 추가: 요청자명 (업무요청 연결 시 그 요청의 요청자, 없으면 직접 입력값)
--   - 기존 지급 내역은 연결된 업무요청의 요청자명으로 채움
-- 이번 코드가 REQUESTER_NAME 컬럼을 사용하므로 코드 배포 전에 실행해야 한다.
-- idempotent: INFORMATION_SCHEMA 체크 후 필요한 경우에만 ALTER, 백필은 비어 있는 행만 (재실행 안전).

SET NAMES utf8mb4;

-- 1) REQUEST_ID NULL 허용
SET @request_id_not_null := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'supply_issue'
      AND COLUMN_NAME = 'REQUEST_ID'
      AND IS_NULLABLE = 'NO'
);
SET @sql := IF(@request_id_not_null = 1,
    "ALTER TABLE supply_issue MODIFY COLUMN REQUEST_ID bigint(20) DEFAULT NULL COMMENT '연결된 업무요청 ID (FK, 선택 — 업무요청 없이 지급하면 NULL)'",
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) REQUESTER_NAME 컬럼 추가
SET @has_requester_name := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'supply_issue'
      AND COLUMN_NAME = 'REQUESTER_NAME'
);
SET @sql := IF(@has_requester_name = 0,
    "ALTER TABLE supply_issue ADD COLUMN REQUESTER_NAME varchar(50) DEFAULT NULL COMMENT '요청자명 (업무요청 연결 시 그 요청의 요청자, 없으면 직접 입력)' AFTER REQUEST_ID",
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 기존 지급 내역의 요청자명 백필 (연결된 업무요청 기준, 비어 있는 행만)
UPDATE supply_issue s
  JOIN service_request r ON r.REQUEST_ID = s.REQUEST_ID
   SET s.REQUESTER_NAME = r.REQUESTER_NAME
 WHERE s.REQUESTER_NAME IS NULL;
