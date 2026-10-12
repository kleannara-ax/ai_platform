-- 13_inventory_transaction_date_issue_link.sql
-- 소모품 입출고 이력(inventory_transaction)에 입출고일·세부 구분·지급 내역 연결을 추가한다.
--   - TRANSACTION_DATE : 입/출고 모달에서 입력한 입고일·출고일 (이력 화면의 '일시'로 표시)
--   - SUB_TYPE         : 세부 구분(신형/구형, 레노버/갤럭시) — 기존에는 입고 비고 앞 "[신형]"으로만 남았음
--   - ISSUE_ID         : 출고 이력이 어떤 지급 내역(supply_issue)인지 연결 — 요청자·지급대상자·부서 표시, 출고 취소·수정에 사용
-- 기존 이력은 아래 백필로 채운다 (출고는 지급 내역과 같은 품목·수량·담당자·5초 이내 생성으로 짝지음).
-- 이번 코드가 이 컬럼들을 사용하므로 코드 배포 전에 실행해야 한다.
-- idempotent: INFORMATION_SCHEMA 체크 후 필요한 경우에만 ALTER, 백필은 비어 있는 행만 (재실행 안전).

SET NAMES utf8mb4;

-- 1) TRANSACTION_DATE
SET @has_col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_transaction' AND COLUMN_NAME = 'TRANSACTION_DATE');
SET @sql := IF(@has_col = 0,
    "ALTER TABLE inventory_transaction ADD COLUMN TRANSACTION_DATE date DEFAULT NULL COMMENT '입출고일 (입고일/출고일, 화면 입력값)' AFTER AFTER_STOCK",
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) SUB_TYPE
SET @has_col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_transaction' AND COLUMN_NAME = 'SUB_TYPE');
SET @sql := IF(@has_col = 0,
    "ALTER TABLE inventory_transaction ADD COLUMN SUB_TYPE varchar(20) DEFAULT NULL COMMENT '세부 구분 (신형/구형/레노버/갤럭시)' AFTER TRANSACTION_DATE",
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) ISSUE_ID (+ 인덱스, FK: 지급 내역이 지워지면 연결만 끊음)
SET @has_col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_transaction' AND COLUMN_NAME = 'ISSUE_ID');
SET @sql := IF(@has_col = 0,
    "ALTER TABLE inventory_transaction ADD COLUMN ISSUE_ID bigint(20) DEFAULT NULL COMMENT '연결된 지급 내역 ID (출고만, FK)' AFTER ITEM_ID",
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_transaction' AND INDEX_NAME = 'IDX_INVENTORY_TX_ISSUE');
SET @sql := IF(@has_idx = 0,
    'CREATE INDEX IDX_INVENTORY_TX_ISSUE ON inventory_transaction (ISSUE_ID)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_transaction' AND INDEX_NAME = 'IDX_INVENTORY_TX_DATE');
SET @sql := IF(@has_idx = 0,
    'CREATE INDEX IDX_INVENTORY_TX_DATE ON inventory_transaction (TRANSACTION_DATE)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_fk := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_transaction' AND CONSTRAINT_NAME = 'FK_INVENTORY_TX_ISSUE');
SET @sql := IF(@has_fk = 0,
    'ALTER TABLE inventory_transaction ADD CONSTRAINT FK_INVENTORY_TX_ISSUE FOREIGN KEY (ISSUE_ID) REFERENCES supply_issue (ISSUE_ID) ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 백필: 출고 이력 ↔ 지급 내역 연결 (같은 품목·수량·담당자, 생성 시각 5초 이내, 아직 연결 안 된 지급 내역)
UPDATE inventory_transaction t
  JOIN supply_issue s
    ON s.ITEM_ID = t.ITEM_ID
   AND s.QUANTITY = t.QUANTITY
   AND s.ISSUED_BY <=> t.CREATED_BY
   AND ABS(TIMESTAMPDIFF(SECOND, s.CREATED_AT, t.CREATED_AT)) <= 5
   SET t.ISSUE_ID = s.ISSUE_ID
 WHERE t.TRANSACTION_TYPE = 'OUTBOUND'
   AND t.ISSUE_ID IS NULL
   AND NOT EXISTS (SELECT 1 FROM (SELECT ISSUE_ID FROM inventory_transaction WHERE ISSUE_ID IS NOT NULL) x
                   WHERE x.ISSUE_ID = s.ISSUE_ID);

-- 5) 백필: 세부 구분 — 출고는 지급 내역에서, 입고는 비고 앞 "[구분]"에서
UPDATE inventory_transaction t
  JOIN supply_issue s ON s.ISSUE_ID = t.ISSUE_ID
   SET t.SUB_TYPE = s.SUB_TYPE
 WHERE t.SUB_TYPE IS NULL AND s.SUB_TYPE IS NOT NULL;

UPDATE inventory_transaction
   SET SUB_TYPE = SUBSTRING(NOTE, 2, LOCATE(']', NOTE) - 2)
 WHERE TRANSACTION_TYPE = 'INBOUND' AND SUB_TYPE IS NULL
   AND NOTE LIKE '[%]%' AND LOCATE(']', NOTE) BETWEEN 3 AND 22;

-- 입고 비고에서 "[구분] " 접두어 제거 (구분은 SUB_TYPE 컬럼으로 표시)
UPDATE inventory_transaction
   SET NOTE = NULLIF(TRIM(SUBSTRING(NOTE, CHAR_LENGTH(SUB_TYPE) + 3)), '')
 WHERE TRANSACTION_TYPE = 'INBOUND' AND SUB_TYPE IS NOT NULL
   AND NOTE LIKE CONCAT('[', SUB_TYPE, ']%');

-- 지급 내역과 연결된 예전 직접 지급 출고의 자동 비고("요청자 OOO 직접 지급") 제거 — 요청자·지급대상자는 지급 내역에서 표시
UPDATE inventory_transaction
   SET NOTE = NULL
 WHERE TRANSACTION_TYPE = 'OUTBOUND' AND ISSUE_ID IS NOT NULL
   AND NOTE LIKE '요청자 % 직접 지급';

-- 6) 백필: 입출고일 — 출고는 지급 내역의 지급일, 그 외는 생성일
UPDATE inventory_transaction t
  JOIN supply_issue s ON s.ISSUE_ID = t.ISSUE_ID
   SET t.TRANSACTION_DATE = s.ISSUED_AT
 WHERE t.TRANSACTION_DATE IS NULL;

UPDATE inventory_transaction
   SET TRANSACTION_DATE = DATE(CREATED_AT)
 WHERE TRANSACTION_DATE IS NULL;
