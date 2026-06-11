-- V19: 기타설비 정수기 입력 단순화 및 에어컨/정수기 최종 DB 정책 정리
--   - 정수기는 종류가 단일하므로 EQUIPMENT_TYPE을 '정수기'로 통일
--   - 정수기 화면/저장 정책은 설치일, 건물, 층, X/Y 좌표만 사용자 입력으로 사용
--   - 에어컨은 V17 기준 단순화 상태 유지: 제조사, 상세 위치, 실외기 대수(최대 2대), 제조/설치일 사용
--   - MANUFACTURE_DATE는 공통 DATE 컬럼으로 유지하되 에어컨은 월 입력값의 1일, 정수기는 설치일을 저장

UPDATE facility_equipment
   SET EQUIPMENT_TYPE = '정수기',
       MANUFACTURER = NULL,
       LOCATION_DESCRIPTION = NULL,
       OUTDOOR_UNIT_COUNT = 1,
       REPLACEMENT_CYCLE_YEARS = 10,
       NOTE = NULL
 WHERE CATEGORY = 'WATER_PURIFIER';

ALTER TABLE facility_equipment
    MODIFY COLUMN EQUIPMENT_TYPE VARCHAR(100) NOT NULL COMMENT '설비 종류(에어컨 종류, 정수기는 정수기 고정)',
    MODIFY COLUMN MANUFACTURE_DATE DATE NOT NULL COMMENT '제조/설치일(에어컨: 제조/설치월 1일, 정수기: 설치일)',
    MODIFY COLUMN MANUFACTURER VARCHAR(100) NULL COMMENT '제조사(에어컨 전용)',
    MODIFY COLUMN LOCATION_DESCRIPTION VARCHAR(200) NULL COMMENT '상세 위치(에어컨 전용)',
    MODIFY COLUMN OUTDOOR_UNIT_COUNT INT NOT NULL DEFAULT 1 COMMENT '실외기 대수(에어컨 전용, 1~2)';
