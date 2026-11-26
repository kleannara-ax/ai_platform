-- ============================================================
-- module-security_log : 13_dlp_external_ip_rule.sql
-- 탐지 룰 매칭 방식 '내부 IP 외 접속'(EXTERNAL_IP) 추가 + DLP 전용 룰 1개 등록
--
--   - EXTERNAL_IP 매칭: 로그 한 줄에서 처음 나오는 IPv4 가 [관리 › 내부 IP 설정]의
--     사용 중 대역에 없고, PATTERN(대상 줄 조건, Java 정규식)에도 맞으면 탐지.
--     PATTERN '.*' = IP 가 있는 모든 줄 (로그인·조회·추가 등 행위 구분 없음).
--   - 탐지 결과는 다른 룰과 같이 sec_log_detection 에 저장 → DLP 점검 보고서 항목 + 검토 대상
--     (미검토 탐지가 남으면 최종 검토완료 불가).
--   - 내부 IP 설정을 바꾸면 이미 분석한 로그는 상세 화면 [재분석] 후 반영.
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (ALTER 는 COMMENT 만 변경, INSERT IGNORE + UK_SEC_LOG_RULE_CODE)
--           운영자가 화면에서 바꾼 룰(위험도·사용 여부 등)은 덮어쓰지 않음
-- 주의    : DROP / TRUNCATE / DELETE 없음, core 테이블 FK 없음
--           SYSTEM_ID 는 SYSTEM_CODE('SEC_DLP') 로 찾아 넣음 (DLP 시스템이 없으면 건너뜀)
-- 선행    : 01 ~ 12 (특히 03 시스템 등록, 07 SYSTEM_ID 컬럼, 09 내부 IP 설정)
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 0. 매칭 방식 컬럼 설명에 EXTERNAL_IP 추가 (컬럼 정의는 그대로, COMMENT 만 변경)
-- ------------------------------------------------------------
ALTER TABLE sec_log_rule
    MODIFY COLUMN MATCH_TYPE VARCHAR(20) NOT NULL DEFAULT 'KEYWORD'
        COMMENT '매칭 방식 (KEYWORD: 쉼표구분 키워드 포함, REGEX: 정규식, EXTERNAL_IP: 줄의 첫 IPv4 가 내부 IP 설정 대역 밖 + 대상 줄 조건 정규식)';

-- ------------------------------------------------------------
-- 1. DLP 전용 룰: 내부 IP 외 접속 (모든 행위)
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, 'DLP_EXTERNAL_IP_ACCESS', '내부 IP 외 접속 (모든 행위)',
       '[관리 › 내부 IP 설정]에 등록된 대역 밖 IP 에서 관리 콘솔을 사용한 기록 (로그인·조회·추가·수정 등 모든 행위). 관리자 PC 이외 장소 접속·IP 변경 여부 확인, 사내 IP 라면 내부 IP 설정에 대역 추가 후 재분석 (ISMS 2.6.6 원격접근 통제)',
       'SECURITY', 'EXTERNAL_IP', '.*', 'HIGH', 1, 'Y', 480, NULL
  FROM sec_log_system s
 WHERE s.SYSTEM_CODE = 'SEC_DLP' AND s.DELETED_YN = 'N';
