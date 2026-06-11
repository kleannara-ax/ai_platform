-- V18: 수신기/소방펌프 설비 대표사진 최신 1장 경로 추가
--   - 점검 이력 사진과 별개로 설비 마스터의 최신 대표사진 경로를 1개만 유지

ALTER TABLE fire_receiver
    ADD COLUMN IF NOT EXISTS IMAGE_PATH VARCHAR(600) NULL;

ALTER TABLE fire_pump
    ADD COLUMN IF NOT EXISTS IMAGE_PATH VARCHAR(600) NULL;
