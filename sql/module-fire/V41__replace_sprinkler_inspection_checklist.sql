-- ============================================================
-- V41: 스프링클러 점검표 교체
--      - 점검 이력에 점검표 유형(CHECKLIST_TYPE) 컬럼 추가
--          STANDARD      : 표준 점검표 (주차타워 외, 교체 이후 신규 점검)
--          PARKING_TOWER : 주차타워 건물 — 기존 점검표 그대로 사용
--          STATUS_ONLY   : 교체 이전 이력(주차타워 제외) — 정상/비정상 결과만 표시
--      - 기존 이력 분류: 주차타워 → PARKING_TOWER, 그 외 → STATUS_ONLY
--      항목 정의는 애플리케이션(SprinklerChecklist)에서 관리한다.
--      재실행 안전: 컬럼은 IF NOT EXISTS, 분류는 미분류(NULL) 이력만 대상.
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE fire_sprinkler_inspection
    ADD COLUMN IF NOT EXISTS CHECKLIST_TYPE VARCHAR(30) NULL
        COMMENT '점검표 유형 (STANDARD/PARKING_TOWER/STATUS_ONLY)'
        AFTER INSPECTION_STATUS;

UPDATE fire_sprinkler_inspection i
JOIN fire_sprinkler s ON s.SPRINKLER_ID = i.SPRINKLER_ID
LEFT JOIN building b ON b.BUILDING_ID = s.BUILDING_ID
SET i.CHECKLIST_TYPE = CASE
        WHEN REPLACE(b.BUILDING_NAME, ' ', '') = '주차타워' THEN 'PARKING_TOWER'
        ELSE 'STATUS_ONLY'
    END
WHERE i.CHECKLIST_TYPE IS NULL;
