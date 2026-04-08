-- ============================================================
--  fire_hydrant 테이블에 qr_key 컬럼 추가
--  MariaDB 10.11+ (utf8mb4)
--  최종 갱신: 2026-04-08
-- ============================================================
--
-- 문제: FireHydrant JPA 엔티티에 qr_key 필드가 매핑되어 있으나
--       fire_hydrant DDL(03_ddl_fire.sql)에는 해당 컬럼이 누락됨.
--       → floor-data API 호출 시 "Unknown column 'fh1_0.qr_key'" SQL 오류 발생.
--
-- 해결: fire_hydrant 테이블에 qr_key 컬럼을 추가하고,
--       기존 행에 UUID 기반 고유 값을 자동 부여한다.
--
-- 참고: fire_receiver, fire_pump 테이블에는 이미 qr_key 컬럼이 존재.
--       extinguisher 테이블은 note_key를 QR 키로 사용 (별도 구조).
--
-- 실행 방법:
--   mariadb -u platform_user -p platform_db < sql/module-fire/12_add_hydrant_qr_key.sql
-- ============================================================

-- ── 1. qr_key 컬럼 추가 (이미 존재하면 무시) ──
-- MariaDB 10.5+ 에서 IF NOT EXISTS 지원
ALTER TABLE fire_hydrant
    ADD COLUMN IF NOT EXISTS qr_key VARCHAR(100) NULL COMMENT 'QR 고유키'
    AFTER image_path;

-- ── 2. 기존 행에 UUID 값 부여 ──
-- qr_key가 NULL인 기존 데이터에 고유 UUID 생성
UPDATE fire_hydrant
   SET qr_key = REPLACE(UUID(), '-', '')
 WHERE qr_key IS NULL;

-- ── 3. NOT NULL + UNIQUE 제약 추가 ──
ALTER TABLE fire_hydrant
    MODIFY COLUMN qr_key VARCHAR(100) NOT NULL COMMENT 'QR 고유키';

-- UNIQUE KEY 추가 (이미 존재하면 무시)
-- MariaDB에서는 CREATE UNIQUE INDEX IF NOT EXISTS 지원
CREATE UNIQUE INDEX IF NOT EXISTS uk_hydrant_qr_key ON fire_hydrant (qr_key);

-- ── 4. 06_restore_from_backup.sql 실행 후에도 qr_key가 비어있을 수 있으므로 ──
-- 백업 복원 시에는 이 스크립트를 다시 실행하여 qr_key를 채워야 합니다.

-- ── 확인 쿼리 ──
-- SELECT hydrant_id, serial_number, qr_key FROM fire_hydrant;
-- SHOW COLUMNS FROM fire_hydrant LIKE 'qr_key';
