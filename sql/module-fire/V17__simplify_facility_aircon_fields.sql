-- V17: 기타설비 에어컨 입력 단순화
--   - 중복 설치연도 제거 (제조/설치월 MANUFACTURE_DATE만 사용)
--   - 실외기 좌표와 실내기-실외기 연결 정보 제거 (실외기 대수만 유지)
--   - 설비는 개별 등록 방식으로 관리하므로 공통 수량 컬럼 제거

ALTER TABLE facility_equipment
    DROP COLUMN IF EXISTS INSTALLATION_YEAR,
    DROP COLUMN IF EXISTS OUTDOOR_X,
    DROP COLUMN IF EXISTS OUTDOOR_Y,
    DROP COLUMN IF EXISTS QUANTITY;
