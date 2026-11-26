-- ============================================================
-- module-security_log : 11_mall_login_rules.sql
-- 자사몰(깨끗한나라몰, SVC_MALL) 관리자 로그인 기록 분석 룰 9개
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (INSERT IGNORE + UK_SEC_LOG_RULE_CODE)
--           운영자가 화면에서 수정한 룰(패턴·위험도·사용여부)은 덮어쓰지 않음
-- 주의    : DROP / TRUNCATE / DELETE 없음, core 테이블 FK 없음
--           SYSTEM_ID 는 SYSTEM_CODE 'SVC_MALL' 로 찾아 넣음 (시스템이 없으면 건너뜀)
-- 정규식  : Java Pattern, 대소문자 무시, 한 줄 단위 매칭. 엑셀은 탭(\t) 구분 한 줄로 변환된 뒤 매칭
--           SQL 문자열 안 역슬래시는 '\\' 로 이스케이프 (NO_BACKSLASH_ESCAPES 미설정 기준)
-- 적용    : 이후 업로드·재분석부터. 이미 분석한 업로드는 상세 화면 [재분석] (검토완료 건은 잠김)
-- 선행    : 01 ~ 10 (특히 03 시스템 등록, 07 SYSTEM_ID 컬럼)
--
-- 대상 형식 (샘플 확인 전 초안 — 설명 앞 '[샘플 확인 전 초안]')
--   ① 관리자 접속 기록 엑셀 : 번호 | 아이디 | 이름 | 구분(로그인·로그아웃·OTP 인증 …) | 결과 | 접속 IP | 일시
--   ② key=value 텍스트 로그 : 2026-09-03 10:02:11 [ADMIN_LOGIN] result=FAIL reason=PASSWORD_MISMATCH admin_id=… ip=…
--   ③ 한글 문장형 로그     : 관리자 로그인 실패 - 아이디: …, 접속IP: …
--   샘플을 받으면 패턴을 맞추고 설명에서 표시를 지움.
--
-- 공통 룰과의 관계 (공통 룰은 그대로 함께 적용됨)
--   반복 인증 실패(10건↑)  → 자사몰은 '관리자 로그인 실패 다발'(5건↑)로 더 일찍 탐지
--   심야 시간대 접근(00~05시) → 자사몰은 '심야(22~06시) 관리자 로그인'으로 더 넓게, 성공 건만
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'WEB', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'MALL_LOGIN_FAIL_BURST' AS RULE_CODE, '관리자 로그인 실패 다발' AS RULE_NAME,
               '[샘플 확인 전 초안] 비밀번호 불일치 등 로그인 실패가 한 파일에서 5건 이상. 특정 계정·IP 에 몰렸는지 [로그 보기]로 확인하고, 무차별 대입이면 IP 차단·비밀번호 변경 (ISMS 2.5.3, 안전성 확보조치 기준 제5조 — 일정 횟수 실패 시 접근 제한). 공통 룰 ''반복 인증 실패''(10건↑)보다 낮은 기준' AS DESCRIPTION,
               '^(?=.*(로그인|log ?in|sign ?in|인증|authenticat|비밀번호|패스워드|password)).*(실패|fail(ed|ure)?|denied|거부|불일치|mismatch|incorrect|wrong ?password|invalid ?password)' AS PATTERN,
               'HIGH' AS SEVERITY, 5 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 1160 AS SORT_ORDER
        UNION ALL SELECT 'MALL_LOGIN_UNKNOWN_ID', '없는 아이디로 로그인 시도',
               '[샘플 확인 전 초안] 등록되지 않은 아이디로 로그인 시도가 3건 이상. 아이디를 바꿔 가며 넣는 계정 추측 공격 의심 — 시도한 아이디 목록과 접속 IP 확인 (ISMS 2.5.3)',
               '존재하지 ?않는 ?(아이디|계정|회원|사용자|관리자)|(미등록|등록되지 ?않은|없는) ?(아이디|계정|사용자|관리자)|(user|account|admin|id) ?not ?(found|exist)|unknown ?(user|account)|no ?such ?user|invalid ?(user|account|id)\\b|id_?not_?(exist|found)|not_?exist(s)?_?(id|user)',
               'MEDIUM', 3, 'Y', 1161
        UNION ALL SELECT 'MALL_ACCOUNT_LOCK', '관리자 계정 잠금·잠금 해제',
               '[샘플 확인 전 초안] 로그인 실패 초과로 계정이 잠기거나, 잠긴 계정을 관리자가 풀어 준 기록. 잠금은 공격 대상이 된 계정, 해제는 본인 요청·승인이 있었는지 확인 (ISMS 2.5.3, 안전성 확보조치 기준 제5조)',
               '계정 ?잠금|잠김|잠금 ?(처리|해제)|로그인 ?(제한|잠금)|account ?(is ?)?locked|lock ?out|login_?lock|un ?lock(ed)? ?(account|user|admin)|account_?unlock|\\d+ ?회 ?(초과|이상) ?(실패|오류)',
               'MEDIUM', 1, 'Y', 1162
        UNION ALL SELECT 'MALL_LOGIN_IP_DENIED', '허용되지 않은 IP에서 관리자 접속 시도',
               '[샘플 확인 전 초안] 관리자 페이지 접속 허용 IP 가 아닌 곳에서 로그인 시도. 해외·처음 보는 IP 면 계정 탈취 시도로 보고 해당 계정 비밀번호 변경 여부 확인 (ISMS 2.6.1, 안전성 확보조치 기준 제6조 — 접속 IP 제한)',
               '(접속|허용|로그인|관리자)? ?ip ?(제한|차단|거부|불일치)|허용되지 ?않은 ?ip|미허용 ?ip|비인가 ?ip|not ?allowed ?ip|ip ?(address ?)?(not ?allowed|denied|blocked|restricted)|ip_?(deny|denied|block(ed)?|restrict(ed)?|not_?allowed)',
               'HIGH', 1, 'Y', 1163
        UNION ALL SELECT 'MALL_LOGIN_OTP_FAIL', '2차 인증(OTP·인증번호) 실패',
               '[샘플 확인 전 초안] 비밀번호는 맞았지만 2차 인증(OTP·휴대폰 인증번호)에서 3건 이상 실패. 비밀번호가 이미 유출됐을 수 있으므로 해당 계정 비밀번호 즉시 변경 검토 (ISMS 2.5.3, 안전성 확보조치 기준 제6조 — 외부 접속 시 안전한 인증)',
               '(otp|2차 ?인증|2단계 ?인증|추가 ?인증|mfa|2fa|인증 ?번호|보안 ?코드|verification ?code).{0,30}(실패|불일치|오류|만료|초과|fail(ed|ure)?|invalid|mismatch|expired|incorrect)',
               'HIGH', 3, 'Y', 1164
        UNION ALL SELECT 'MALL_SHARED_ACCOUNT_LOGIN', '공용·기본 계정(admin·master 등)으로 관리자 로그인',
               '[샘플 확인 전 초안] admin·administrator·master·root·guest 같은 공용·기본 계정으로 로그인(인증 완료). 자사몰 운영자 계정은 1인 1계정이 원칙 — 실제 사용자 확인, 공용 계정이면 개인 계정으로 바꾸고 사용 중지 (ISMS 2.5.1/2.5.2). test1 같은 이름이라도 개인에게 발급된 계정은 대상 아님',
               '^(?!.*(실패|fail|denied|거부|차단|로그아웃|log ?out|log ?off))(?=.*(로그인|log ?in|sign ?in|log ?on|인증)).*(\\t|,|(^|[^a-z0-9_])(id|user(name|_?id)?|login_?id|admin_?id|account|아이디|계정|사용자)\\s*[:=]\\s*[\\x22\\x27]?)(admin|administrator|master|root|guest)\\d*([\\x22\\x27]?(\\t|[\\s,;|)]|$))',
               'MEDIUM', 1, 'Y', 1165
        UNION ALL SELECT 'MALL_OFF_HOUR_LOGIN', '심야(22~06시) 관리자 로그인',
               '22시 ~ 06시 사이 관리자 로그인 완료(수행업무 ''인증''·''로그인''). 야간 근무·긴급 작업 기록과 대조하고, 근거가 없으면 본인 확인 (ISMS 2.9.4). 실패·로그아웃 줄은 제외. 공통 룰 ''심야 시간대 접근''(00~05시, 낮음)보다 넓은 시간대. 실제 자사몰 접속 기록(접속일시 | 접속IP | 운영자 아이디 | 수행업무) 기준',
               '^(?!.*(실패|fail|denied|거부|차단|잠금|locked|로그아웃|log ?out|log ?off))(?=.*(로그인|log ?in|sign ?in|log ?on|인증)).*(?<![\\d:])(2[23]|0[0-5]):[0-5]\\d:[0-5]\\d\\b',
               'MEDIUM', 1, 'Y', 1166
        UNION ALL SELECT 'MALL_DUPLICATE_LOGIN', '동일 계정 중복(동시) 로그인',
               '[샘플 확인 전 초안] 같은 관리자 계정이 다른 PC·IP 에서 동시에 로그인되어 앞 세션이 끊긴 기록. 계정 공유나 탈취 의심 — 두 접속 IP 가 모두 본인 것인지 확인 (ISMS 2.5.1)',
               '중복 ?로그인|동시 ?(접속|로그인)|다른 ?(기기|장소|브라우저|pc|ip|곳)에서 ?(로그인|접속)|duplicate[ _]?(login|session)|concurrent[ _]?(login|session)|session ?(kicked|terminated by (another|new) login)',
               'MEDIUM', 1, 'Y', 1167
        UNION ALL SELECT 'MALL_PW_FIND', '관리자 비밀번호 찾기·임시 비밀번호 발급',
               '[샘플 확인 전 초안] 로그인 화면의 비밀번호 찾기, 임시 비밀번호 발급·재발급. 본인 요청인지 확인하고, 발급 직후 다른 IP 에서 로그인했는지 함께 확인 (ISMS 2.5.4)',
               '비밀번호 ?찾기|임시 ?비밀번호|비밀번호 ?재발급|forgot ?password|find_?(pw|passwd|password)|temp(orary)? ?password|password ?reissue',
               'MEDIUM', 1, 'Y', 1168
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'SVC_MALL' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 확인용 (조회만)
-- SELECT r.RULE_CODE, r.RULE_NAME, r.SEVERITY, r.THRESHOLD_COUNT, r.USE_YN
--   FROM sec_log_rule r JOIN sec_log_system s ON s.SYSTEM_ID = r.SYSTEM_ID
--  WHERE s.SYSTEM_CODE = 'SVC_MALL' AND r.DELETED_YN = 'N' ORDER BY r.SORT_ORDER;
-- ------------------------------------------------------------
