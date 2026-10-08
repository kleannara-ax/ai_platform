-- ============================================================
-- module-security_log : 02_seed_data.sql
-- 기본 이상징후 탐지 룰셋 초기 데이터
--
-- 재실행  : 가능 (INSERT IGNORE + UK_SEC_LOG_RULE_CODE)
--           이미 존재하는 룰(운영자가 수정한 룰 포함)은 덮어쓰지 않음
-- 주의    : core 메뉴/권한 테이블 INSERT 없음 (README.md 의 등록 요청 정보 참고)
--           DROP / TRUNCATE / DELETE 없음
-- 정규식  : Java Pattern 문법, 대소문자 무시(CASE_INSENSITIVE)로 매칭
--           SQL 문자열 내 역슬래시는 '\\' 로 이스케이프 (NO_BACKSLASH_ESCAPES 미설정 기준)
-- ============================================================

-- 클라이언트 문자셋을 utf8mb4 로 고정 (한글 COMMENT/데이터 깨짐 방지)
SET NAMES utf8mb4;

INSERT IGNORE INTO sec_log_rule
    (RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
VALUES
-- ---------------- 공통 (ALL) ----------------
('COM_AUTH_FAIL_BRUTE', '반복 인증 실패 (무차별 대입 의심)',
 '파일 내 인증 실패 로그가 10건 이상 발생한 경우. ISMS 2.5.1/2.9.4 접근기록 점검',
 'ALL', 'REGEX', '(authentication fail|login fail|failed password|invalid password|로그인 실패|인증 실패)', 'HIGH', 10, 'Y', 10, NULL),

('COM_PRIV_ACCOUNT_LOGIN', '특권 계정 로그인',
 'root/administrator/admin/sa 등 특권 계정 로그인 성공 기록. 사전 승인 여부 확인 필요',
 'ALL', 'REGEX', '(accepted (password|publickey) for root|session opened for user root|logon.*(administrator|admin)\\b|login.*\\b(sa|admin|root)\\b.*success)', 'MEDIUM', 1, 'Y', 20, NULL),

('COM_OFF_HOUR_ACCESS', '심야 시간대 접근 (00~05시)',
 '타임스탬프가 00:00~05:59 사이인 로그인/접속 기록',
 'ALL', 'REGEX', '\\b0[0-5]:[0-5][0-9]:[0-5][0-9]\\b.*(login|logon|accepted|session opened|connect)', 'LOW', 1, 'Y', 30, NULL),

('COM_ACCOUNT_CHANGE', '계정 생성/삭제/권한 변경',
 '계정 추가·삭제·권한 변경 이력. 승인 내역과 대조 필요 (ISMS 2.5.1 사용자 계정 관리)',
 'ALL', 'REGEX', '(useradd|userdel|usermod|new user|delete user|grant |revoke |4720|4726|4732|4728|계정 생성|계정 삭제|권한 변경)', 'MEDIUM', 1, 'Y', 40, NULL),

('COM_LOG_TAMPER', '로그 삭제/감사 설정 변경',
 '로그 삭제 또는 감사 정책 변경 흔적 (Windows 1102, auditctl, history -c 등)',
 'ALL', 'REGEX', '(event ?id[:= ]*1102|audit log was cleared|auditctl -D|history -c|rm -rf /var/log|logrotate.*-f|감사 로그 삭제)', 'CRITICAL', 1, 'Y', 50, NULL),

('COM_MALWARE_KEYWORD', '악성코드/해킹도구 키워드',
 '대표적인 해킹도구 및 악성 명령 키워드',
 'ALL', 'KEYWORD', 'mimikatz,meterpreter,cobaltstrike,nc -e,ncat -e,/bin/sh -i,powershell -enc,wget http,curl http', 'CRITICAL', 1, 'Y', 60, NULL),

-- ---------------- Linux ----------------
('LNX_SSH_INVALID_USER', 'SSH 존재하지 않는 계정 접속 시도',
 'sshd invalid user 로그 5건 이상 (계정 스캐닝 의심)',
 'LINUX', 'KEYWORD', 'invalid user', 'HIGH', 5, 'Y', 100, NULL),

('LNX_SUDO_FAIL', 'sudo 권한 상승 실패',
 'sudo 인증 실패 또는 sudoers 미등록 사용자의 권한 상승 시도',
 'LINUX', 'REGEX', '(sudo:.*authentication failure|sudo:.*not in sudoers|sudo:.*incorrect password)', 'HIGH', 1, 'Y', 110, NULL),

('LNX_SU_ROOT', 'su 를 통한 root 전환',
 'su 명령으로 root 세션 전환',
 'LINUX', 'REGEX', 'su(\\[\\d+\\])?:.*(session opened for user root|to root)', 'MEDIUM', 1, 'Y', 120, NULL),

-- ---------------- Windows ----------------
('WIN_LOGON_FAIL_4625', 'Windows 로그온 실패 (4625) 반복',
 'Event ID 4625 로그온 실패 10건 이상',
 'WINDOWS', 'REGEX', '\\b4625\\b', 'HIGH', 10, 'Y', 200, NULL),

('WIN_ACCOUNT_LOCKOUT', 'Windows 계정 잠금 (4740)',
 'Event ID 4740 계정 잠금 발생',
 'WINDOWS', 'REGEX', '\\b4740\\b|account was locked out', 'MEDIUM', 1, 'Y', 210, NULL),

-- ---------------- Web ----------------
('WEB_SQL_INJECTION', 'SQL Injection 공격 시도',
 '요청 URL 에 SQL Injection 패턴 포함',
 'WEB', 'REGEX', '(union(\\s|%20|\\+)+select|or(\\s|%20|\\+)+1(\\s|%20)*=(\\s|%20)*1|%27(\\s|%20)*or|sleep\\(|benchmark\\(|information_schema)', 'CRITICAL', 1, 'Y', 300, NULL),

('WEB_XSS', 'XSS 공격 시도',
 '요청 URL 에 스크립트 삽입 패턴 포함',
 'WEB', 'REGEX', '(<script|%3cscript|javascript:|onerror=|onload=|alert\\()', 'HIGH', 1, 'Y', 310, NULL),

('WEB_PATH_TRAVERSAL', '경로 조작(Path Traversal) 시도',
 '../ 또는 인코딩된 상위 경로 접근 시도',
 'WEB', 'REGEX', '(\\.\\./|\\.\\.%2f|%2e%2e%2f|%2e%2e/|/etc/passwd|win\\.ini)', 'HIGH', 1, 'Y', 320, NULL),

('WEB_SCANNER', '취약점 스캐너 접근',
 '대표적인 웹 취약점 스캐너 User-Agent',
 'WEB', 'KEYWORD', 'sqlmap,nikto,nmap,acunetix,nessus,dirbuster,wpscan,masscan,zgrab', 'MEDIUM', 1, 'Y', 330, NULL),

('WEB_HTTP_5XX_BURST', 'HTTP 5xx 오류 다발',
 '서버 오류 응답(5xx) 50건 이상 - 장애 또는 공격 영향 확인',
 'WEB', 'REGEX', '"\\s5\\d\\d\\s', 'LOW', 50, 'Y', 340, NULL),

-- ---------------- DB ----------------
('DB_MASS_EXPORT', 'DB 대량 조회/반출 의심',
 'SELECT * / INTO OUTFILE / mysqldump / exp 등 대량 반출 명령',
 'DB', 'REGEX', '(into outfile|mysqldump|\\bexpdp?\\b|select \\* from .* limit \\d{5,})', 'HIGH', 1, 'Y', 400, NULL),

('DB_DDL_CHANGE', 'DB 구조 변경 (DDL)',
 '운영 DB 에 대한 DROP/TRUNCATE/ALTER 실행 기록',
 'DB', 'REGEX', '\\b(drop\\s+(table|database)|truncate\\s+table|alter\\s+table)\\b', 'HIGH', 1, 'Y', 410, NULL),

-- ---------------- Network ----------------
('NET_DENY_BURST', '방화벽 차단 다발',
 '방화벽 deny/drop 로그 100건 이상 (스캐닝/공격 의심)',
 'NETWORK', 'REGEX', '\\b(deny|denied|drop|blocked)\\b', 'MEDIUM', 100, 'Y', 500, NULL);
