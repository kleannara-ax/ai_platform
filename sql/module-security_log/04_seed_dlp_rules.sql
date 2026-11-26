-- ============================================================
-- module-security_log : 04_seed_dlp_rules.sql
-- DLP(정보보호솔루션) 관리 콘솔 "관리자 감사" 로그 전용 탐지 룰
--
-- 대상 로그 : DLP 관리 콘솔 > 시스템 로그 > 관리자 감사 > 엑셀 내보내기
--            열: 발생 일시 | 사용자(관리자) 이름 | 사용자(관리자) 아이디 | IP | 유형 | 내용 | 상세 정보
--            (엑셀 업로드 시 탭(TAB) 구분 텍스트로 변환되어 한 행 = 한 줄로 분석됨)
-- 적용 유형 : SECURITY (점검 대상 시스템 'DLP' 의 기본 로그 유형)
-- 재실행    : 가능 (INSERT IGNORE + UK_SEC_LOG_RULE_CODE), 운영자가 수정한 룰은 덮어쓰지 않음
-- 주의      : DROP / TRUNCATE / DELETE 없음
-- 정규식    : Java Pattern, 대소문자 무시. SQL 문자열 안 역슬래시는 '\\' 로 이스케이프
--            (NO_BACKSLASH_ESCAPES 미설정 기준). '>' 는 엑셀에 '&gt;' 로 저장되는 경우가 있어 함께 매칭
-- 선행      : 01_schema.sql, 02_seed_data.sql, 03_system_access_account.sql
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sec_log_rule
    (RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
VALUES
('DLP_IP_RESTRICT_DENY', '허용되지 않은 IP에서 관리 콘솔 접속 시도',
 '관리자 접속 IP 제한에 걸린 로그인 실패. 관리자 PC 이외 장소에서의 접속 시도 또는 IP 변경 여부 확인 (ISMS 2.6.6 원격접근 통제)',
 'SECURITY', 'REGEX', '로그인 실패.{0,20}(접속 ?IP ?제한|IP ?차단|허용되지 않은 ?IP)', 'HIGH', 1, 'Y', 400, NULL),

('DLP_ADMIN_PW_CHANGE', '관리자 비밀번호 변경',
 '관리 콘솔 관리자 비밀번호 변경. 본인 변경인지, 정기 변경 주기에 맞는지 확인 (ISMS 2.5.4 비밀번호 관리)',
 'SECURITY', 'REGEX', '(관리자 ?비밀번호 ?(변경|초기화)|admin password (change|reset))', 'MEDIUM', 1, 'Y', 410, NULL),

('DLP_ADMIN_ACCOUNT_CHANGE', '관리자 계정/권한 변경',
 '관리 콘솔 [관리자] 메뉴에서 추가·수정·삭제. 관리자 계정 생성, 권한 변경, 접속 IP 변경 등은 승인 내역과 대조 (ISMS 2.5.1/2.5.5)',
 'SECURITY', 'REGEX', '(>|&gt;) ?관리자에[(]에서[)] ?(추가|수정|삭제)했습니다', 'HIGH', 1, 'Y', 420, NULL),

('DLP_POLICY_CHANGE', 'DLP 보안 정책 변경/실행',
 '유출 통제, PC 보안, 검사, 탐지 규칙, 워터마크 등 정책 추가·수정·삭제·실행. 변경 요청/승인 내역과 대조 (ISMS 2.10.1 보안시스템 운영)',
 'SECURITY', 'REGEX', '정책 ?(>|&gt;).{0,80}에[(]에서[)] ?(추가|수정|삭제|실행)했습니다', 'MEDIUM', 1, 'Y', 430, NULL),

('DLP_LOG_DELETE', '로그/이력 삭제',
 '로그 조회·내보내기 이력·시스템 로그 메뉴에서 삭제. 감사 증적 훼손 여부 확인 (ISMS 2.9.4 로그 및 접속기록 관리)',
 'SECURITY', 'REGEX', '(로그|이력|감사).{0,60}에[(]에서[)] ?삭제했습니다', 'HIGH', 1, 'Y', 440, NULL),

('DLP_AUDIT_SETTING_CHANGE', '로그·백업·메일 보고 설정 변경',
 '로그 상태, 백업/복원, 일반 설정, 주기적 리포트 설정의 추가·수정·삭제. 로그 수집 중단이나 보고 누락으로 이어질 수 있음',
 'SECURITY', 'REGEX', '(설정 ?(>|&gt;) ?(로그 상태|백업/복원|일반)|주기적 (리포트|메일)).{0,40}에[(]에서[)] ?(추가|수정|삭제)했습니다', 'HIGH', 1, 'Y', 450, NULL),

('DLP_DATA_EXPORT', '관리 콘솔 데이터 반출 (다운로드/내보내기/프린트)',
 '유출 통제 로그, 사용자 목록, 에이전트 현황 등을 다운로드·내보내기·프린트. 개인정보·탐지 원문이 포함될 수 있어 업무 목적 확인 (ISMS 2.10.8 데이터 유출 방지)',
 'SECURITY', 'REGEX', '에[(]에서[)] ?(다운로드|내보내기|프린트)했습니다', 'MEDIUM', 1, 'Y', 460, NULL),

('DLP_USER_INFO_CHANGE', 'DLP 사용자(에이전트 대상자) 정보 변경',
 '[사용자] 메뉴에서 추가·수정·삭제. 예외 사용자 지정, 정책 적용 대상 제외 등 통제 우회 여부 확인',
 'SECURITY', 'REGEX', '(>|&gt;) ?사용자에[(]에서[)] ?(추가|수정|삭제)했습니다', 'LOW', 1, 'Y', 470, NULL);
