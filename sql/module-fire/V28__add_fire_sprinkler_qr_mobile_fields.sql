SET NAMES utf8mb4;

-- ============================================================
-- V28: 스프링클러 QR/모바일 점검 연동 필드 추가
--   - fire_sprinkler.QR_KEY: QR 스캔용 고유 키
--   - fire_sprinkler.IMAGE_PATH: 모바일 등록/점검 사진 확장 대비
--   - 기존 스프링클러 데이터에는 QR_KEY 자동 부여
-- ============================================================

ALTER TABLE fire_sprinkler
    ADD COLUMN IF NOT EXISTS QR_KEY VARCHAR(64) NULL COMMENT 'QR 스캔용 고유 키' AFTER NOTE,
    ADD COLUMN IF NOT EXISTS IMAGE_PATH VARCHAR(500) NULL COMMENT '스프링클러 이미지 경로' AFTER QR_KEY;

UPDATE fire_sprinkler
SET QR_KEY = REPLACE(UUID(), '-', '')
WHERE QR_KEY IS NULL OR QR_KEY = '';

CREATE UNIQUE INDEX IF NOT EXISTS UK_FIRE_SPRINKLER_QR_KEY
    ON fire_sprinkler (QR_KEY);
