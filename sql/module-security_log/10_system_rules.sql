-- ============================================================
-- module-security_log : 10_system_rules.sql
-- 점검 대상 시스템별 전용 탐지 룰셋 (14개 시스템)
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (INSERT IGNORE + UK_SEC_LOG_RULE_CODE, UPDATE 는 대상 조건으로 1회만 반영)
--           운영자가 화면에서 수정한 룰(패턴·위험도·사용여부)은 덮어쓰지 않음
-- 주의    : DROP / TRUNCATE / DELETE 없음, core 테이블 FK 없음
--           SYSTEM_ID 는 SYSTEM_CODE 로 찾아 넣음 (ID 하드코딩 없음, 시스템이 없으면 그 시스템 룰은 건너뜀)
-- 정규식  : Java Pattern, 대소문자 무시, 한 줄 단위 매칭. 엑셀은 탭(\t) 구분 한 줄로 변환된 뒤 매칭
--           SQL 문자열 안 역슬래시는 '\\' 로 이스케이프 (NO_BACKSLASH_ESCAPES 미설정 기준)
-- 적용    : 이후 업로드·재분석부터. 이미 분석한 업로드는 상세 화면 [재분석] (검토완료 건은 잠김)
-- 선행    : 01 ~ 09 (특히 03 시스템 등록, 04 DLP 룰, 07 SYSTEM_ID 컬럼)
--
-- 설명(DESCRIPTION) 앞 '[샘플 확인 전 초안]' : 실제 로그 샘플을 받기 전 일반 형식 기준으로 만든 룰.
--   샘플을 받으면 패턴을 맞추고 설명에서 표시를 지움. 홈페이지·DLP 는 실제 로그 기준.
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 0. DLP 전용으로 이동: 04 의 DLP 룰 중 공통(SYSTEM_ID NULL)으로 남은 룰
--    (SECURITY 유형 공통 룰이라 SSL VPN·V3 EPP·WAF 로그에도 적용되던 문제 해소)
-- ------------------------------------------------------------
UPDATE sec_log_rule r
  JOIN sec_log_system s ON s.SYSTEM_CODE = 'SEC_DLP' AND s.DELETED_YN = 'N'
   SET r.SYSTEM_ID = s.SYSTEM_ID
 WHERE r.SYSTEM_ID IS NULL
   AND r.DELETED_YN = 'N'
   AND r.RULE_CODE IN ('DLP_IP_RESTRICT_DENY', 'DLP_ADMIN_PW_CHANGE', 'DLP_ADMIN_ACCOUNT_CHANGE', 'DLP_POLICY_CHANGE', 'DLP_LOG_DELETE', 'DLP_AUDIT_SETTING_CHANGE', 'DLP_DATA_EXPORT', 'DLP_USER_INFO_CHANGE');


-- ------------------------------------------------------------
-- 1. 홈페이지 (SVC_HOMEPAGE) — 실제 로그 기준
--    형식: 관리자 페이지 감사 로그 엑셀: idx | id | type(I 로그인·R 조회·C 생성·U 수정·D 삭제·O 로그아웃) | serviceName | serviceTarget | referer | action | memo | ip | reg_date
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'WEB', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'HP_ADMIN_CREATE' AS RULE_CODE, '관리자 계정 생성' AS RULE_NAME,
               '[관리자 등록/수정] 메뉴에서 관리자 계정 생성(type=C, 메모 "상태: 생성"). 계정 신청·승인 내역과 대조하고 부여 권한이 업무에 맞는지 확인 (ISMS 2.5.1 사용자 계정 관리)' AS DESCRIPTION,
               '\\tC\\t관리자 등록/수정\\t|\\[관리자\\]\\s*상태:\\s*생성' AS PATTERN,
               'HIGH' AS SEVERITY, 1 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 1000 AS SORT_ORDER
        UNION ALL SELECT 'HP_ADMIN_MODIFY', '관리자 계정 권한·상태 변경',
               '[관리자 등록/수정] 메뉴에서 기존 관리자의 권한·상태(비밀번호 초기화 포함) 변경(type=U). 권한 변경 요청·승인 내역과 대조 (ISMS 2.5.1/2.5.5 특수 권한 관리)',
               '\\tU\\t관리자 등록/수정\\t',
               'HIGH', 1, 'Y', 1010
        UNION ALL SELECT 'HP_ADMIN_DELETE', '관리자 계정 삭제',
               '관리자 계정 삭제(type=D, deleteAdmin). 퇴사·직무 변경에 따른 정상 회수인지, 증적 삭제 목적은 아닌지 확인 (ISMS 2.5.1)',
               '\\tD\\t관리자 삭제\\t|/AdminAuth/deleteAdmin',
               'HIGH', 1, 'Y', 1020
        UNION ALL SELECT 'HP_ADMIN_PW_CHANGE', '관리자 비밀번호 본인 변경',
               '관리자 본인 비밀번호 변경(ajaxChangeAdminPassword). 정기 변경 주기에 맞는지, 아이디(id)와 대상(serviceTarget)이 다르면 타인 비밀번호 변경인지 확인 (ISMS 2.5.4 비밀번호 관리)',
               '비밀번호 본인 변경|ajaxChangeAdminPassword',
               'MEDIUM', 1, 'Y', 1030
        UNION ALL SELECT 'HP_CONTENT_DELETE', '게시물·콘텐츠 삭제',
               '관리자 페이지에서 게시물·고객 문의(VOC)·콘텐츠 삭제(type=D, 관리자 삭제 제외). 업무상 삭제인지, 민원·신고 내용 임의 삭제는 아닌지 확인',
               '\\tD\\t(?!관리자 삭제\\t)[^\\t]*삭제\\t',
               'MEDIUM', 1, 'Y', 1040
        UNION ALL SELECT 'HP_OFF_HOUR_CHANGE', '심야(22~06시) 등록·수정·삭제',
               '22시~06시 사이 관리자 페이지 생성·수정·삭제(type=C/U/D). 사전 작업 승인 여부 확인. 자동 배치(id=시스템)는 제외',
               '^\\d+\\t(?!시스템\\t)[^\\t]+\\t[CUD]\\t.*\\t\\d{4}-\\d{2}-\\d{2} (2[23]|0[0-5]):\\d{2}:\\d{2}\\s*$',
               'MEDIUM', 1, 'Y', 1050
        UNION ALL SELECT 'HP_ADMIN_LIST_VIEW', '관리자 목록·상세 조회',
               '관리자 목록·상세 정보 조회(관리자 검색, 관리자 상세조회). 건수가 많아 기본 미사용 — 필요할 때 [관리 › 탐지 룰셋 편집]에서 사용으로 바꿈',
               '\\tR\\t관리자 (검색|상세조회)\\t',
               'LOW', 1, 'N', 1060
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'SVC_HOMEPAGE' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 2. 자사몰(깨끗한나라몰) (SVC_MALL) — 샘플 확인 전 초안
--    형식: 쇼핑몰 관리자 감사 로그 (형식 미확인)
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'WEB', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'MALL_MEMBER_EXPORT' AS RULE_CODE, '회원 개인정보 엑셀 다운로드' AS RULE_NAME,
               '[샘플 확인 전 초안] 회원·주문·배송 목록 엑셀 다운로드/내보내기. 개인정보 대량 반출이므로 다운로드 사유·승인 확인 (ISMS 2.10.8, 개인정보 다운로드 사유 확인)' AS DESCRIPTION,
               '(회원|고객|주문|배송|임직원|사원|직원).{0,30}(엑셀|excel|다운로드|download|내보내기|export)|(excel|download|export)\\w*.{0,20}(member|customer|order|employee)|(member|customer|order|employee)\\w*.{0,20}(excel|download|export)' AS PATTERN,
               'HIGH' AS SEVERITY, 1 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 1100 AS SORT_ORDER
        UNION ALL SELECT 'MALL_ADMIN_CHANGE', '관리자(운영자) 계정·권한 변경',
               '[샘플 확인 전 초안] 관리자·운영자 계정 생성·수정·삭제, 권한 변경. 승인 내역과 대조 (ISMS 2.5.1/2.5.5)',
               '(관리자|운영자|admin(istrator)?).{0,20}(등록|생성|추가|수정|삭제|권한)|/(insert|update|delete)Admin\\b|admin(Auth|Role).{0,20}(save|update|delete)',
               'HIGH', 1, 'Y', 1110
        UNION ALL SELECT 'MALL_MEMBER_CHANGE', '회원 정보 관리자 수정·탈퇴 처리',
               '[샘플 확인 전 초안] 관리자가 회원 정보 수정, 등급 변경, 강제 탈퇴·삭제 처리. 고객 요청·민원 근거 확인',
               '(회원|고객|임직원|사원).{0,20}(정보 ?수정|등급 ?변경|강제 ?탈퇴|탈퇴 ?처리|삭제)|/(update|delete|withdraw)(Member|Customer|Employee)\\b',
               'MEDIUM', 1, 'Y', 1120
        UNION ALL SELECT 'MALL_POINT_GRANT', '적립금·쿠폰 수동 지급',
               '[샘플 확인 전 초안] 관리자가 적립금·포인트·쿠폰·예치금을 수동 지급·조정. 이벤트·보상 근거와 금액 확인 (부정 지급 방지)',
               '(적립금|포인트|쿠폰|예치금).{0,20}(지급|발급|수동|조정|차감)|/(give|issue|adjust)(Point|Coupon|Reserve)\\b',
               'MEDIUM', 1, 'Y', 1130
        UNION ALL SELECT 'MALL_ORDER_REFUND', '관리자 주문 취소·환불 처리',
               '[샘플 확인 전 초안] 관리자가 직접 처리한 주문 취소·환불·반품 승인. 고객 요청 없는 처리나 반복 처리 계정 확인',
               '(주문 ?취소|환불|반품).{0,10}(처리|승인|완료)|/(cancel|refund)(Order|Payment)\\b',
               'LOW', 1, 'Y', 1140
        UNION ALL SELECT 'MALL_ADMIN_PW_CHANGE', '관리자 비밀번호 변경·초기화',
               '[샘플 확인 전 초안] 관리자 비밀번호 변경·초기화. 본인 변경인지, 정기 변경 주기에 맞는지 확인 (ISMS 2.5.4)',
               '(관리자|운영자|admin).{0,20}(비밀번호|패스워드|password).{0,10}(변경|초기화|change|reset)|ChangeAdminPassword|비밀번호 본인 변경',
               'MEDIUM', 1, 'Y', 1150
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'SVC_MALL' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 3. 임직원몰 (SVC_EMP_MALL) — 샘플 확인 전 초안
--    형식: 임직원몰 관리자 감사 로그 (형식 미확인)
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'WEB', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'EMALL_MEMBER_EXPORT' AS RULE_CODE, '임직원(회원) 개인정보 엑셀 다운로드' AS RULE_NAME,
               '[샘플 확인 전 초안] 임직원(회원)·주문·배송 목록 엑셀 다운로드/내보내기. 개인정보 대량 반출이므로 다운로드 사유·승인 확인 (ISMS 2.10.8, 개인정보 다운로드 사유 확인)' AS DESCRIPTION,
               '(회원|고객|주문|배송|임직원|사원|직원).{0,30}(엑셀|excel|다운로드|download|내보내기|export)|(excel|download|export)\\w*.{0,20}(member|customer|order|employee)|(member|customer|order|employee)\\w*.{0,20}(excel|download|export)' AS PATTERN,
               'HIGH' AS SEVERITY, 1 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 1200 AS SORT_ORDER
        UNION ALL SELECT 'EMALL_ADMIN_CHANGE', '관리자(운영자) 계정·권한 변경',
               '[샘플 확인 전 초안] 관리자·운영자 계정 생성·수정·삭제, 권한 변경. 승인 내역과 대조 (ISMS 2.5.1/2.5.5)',
               '(관리자|운영자|admin(istrator)?).{0,20}(등록|생성|추가|수정|삭제|권한)|/(insert|update|delete)Admin\\b|admin(Auth|Role).{0,20}(save|update|delete)',
               'HIGH', 1, 'Y', 1210
        UNION ALL SELECT 'EMALL_MEMBER_CHANGE', '임직원(회원) 정보 관리자 수정·탈퇴 처리',
               '[샘플 확인 전 초안] 관리자가 임직원(회원) 정보 수정, 등급 변경, 강제 탈퇴·삭제 처리. 고객 요청·민원 근거 확인',
               '(회원|고객|임직원|사원).{0,20}(정보 ?수정|등급 ?변경|강제 ?탈퇴|탈퇴 ?처리|삭제)|/(update|delete|withdraw)(Member|Customer|Employee)\\b',
               'MEDIUM', 1, 'Y', 1220
        UNION ALL SELECT 'EMALL_POINT_GRANT', '복지포인트·쿠폰 수동 지급',
               '[샘플 확인 전 초안] 관리자가 임직원 복지포인트·쿠폰을 수동 지급·조정. 인사·복지 기준과 금액 확인 (부정 지급 방지)',
               '(복지 ?포인트|포인트|쿠폰|적립금).{0,20}(지급|발급|수동|조정|차감)|/(give|issue|adjust)(Point|Coupon)\\b',
               'MEDIUM', 1, 'Y', 1230
        UNION ALL SELECT 'EMALL_ORDER_REFUND', '관리자 주문 취소·환불 처리',
               '[샘플 확인 전 초안] 관리자가 직접 처리한 주문 취소·환불·반품 승인. 고객 요청 없는 처리나 반복 처리 계정 확인',
               '(주문 ?취소|환불|반품).{0,10}(처리|승인|완료)|/(cancel|refund)(Order|Payment)\\b',
               'LOW', 1, 'Y', 1240
        UNION ALL SELECT 'EMALL_ADMIN_PW_CHANGE', '관리자 비밀번호 변경·초기화',
               '[샘플 확인 전 초안] 관리자 비밀번호 변경·초기화. 본인 변경인지, 정기 변경 주기에 맞는지 확인 (ISMS 2.5.4)',
               '(관리자|운영자|admin).{0,20}(비밀번호|패스워드|password).{0,10}(변경|초기화|change|reset)|ChangeAdminPassword|비밀번호 본인 변경',
               'MEDIUM', 1, 'Y', 1250
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'SVC_EMP_MALL' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 4. KN-OMS (SVC_KN_OMS) — 샘플 확인 전 초안
--    형식: 주문관리시스템 사용자 행위 로그 (형식 미확인)
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'APP', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'OMS_USER_ROLE_CHANGE' AS RULE_CODE, '사용자 계정·권한 변경' AS RULE_NAME,
               '[샘플 확인 전 초안] OMS 사용자 계정 생성·삭제, 권한·메뉴 그룹·역할 변경. 신청·승인 내역과 대조 (ISMS 2.5.1/2.5.5)' AS DESCRIPTION,
               '(사용자|계정|권한|메뉴 ?그룹|역할).{0,20}(등록|생성|추가|변경|수정|삭제|부여|회수)|\\b(create|update|delete|grant|revoke)[ _]?(user|role|permission|auth)\\b' AS PATTERN,
               'HIGH' AS SEVERITY, 1 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 1300 AS SORT_ORDER
        UNION ALL SELECT 'OMS_DATA_EXPORT', '주문·고객 데이터 엑셀 반출',
               '[샘플 확인 전 초안] 주문·고객·배송·매출 데이터 엑셀 다운로드/내보내기. 고객 개인정보 포함 여부와 업무 목적 확인 (ISMS 2.10.8)',
               '(주문|고객|출고|배송|매출|거래처).{0,30}(엑셀|excel|다운로드|download|내보내기|export)|\\b(download|export)\\w*\\.(xlsx?|csv)\\b',
               'MEDIUM', 1, 'Y', 1310
        UNION ALL SELECT 'OMS_ORDER_FORCE_CHANGE', '주문 강제·일괄 변경/삭제',
               '[샘플 확인 전 초안] 주문 상태 강제 변경, 일괄 수정·삭제·취소. 정상 업무 처리인지 확인 (매출·재고 정합성)',
               '주문.{0,20}(강제|일괄|삭제)|\\b(force|bulk|batch)[ _]?(update|delete|cancel|change)',
               'MEDIUM', 1, 'Y', 1320
        UNION ALL SELECT 'OMS_MASTER_CHANGE', '단가·거래처·상품 기준정보 변경',
               '[샘플 확인 전 초안] 단가·가격, 거래처, 상품(품목) 기준정보 변경·삭제·일괄 반영. 변경 승인 근거 확인 (부정 처리 방지)',
               '(단가|가격|거래처|상품 ?마스터|품목|기준 ?정보).{0,20}(변경|수정|삭제|일괄)',
               'MEDIUM', 1, 'Y', 1330
        UNION ALL SELECT 'OMS_PW_RESET', '사용자 비밀번호 초기화',
               '[샘플 확인 전 초안] 관리자가 다른 사용자의 비밀번호를 초기화·변경. 본인 확인 절차를 거쳤는지 확인 (ISMS 2.5.4)',
               '(비밀번호|패스워드|password).{0,20}(초기화|reset)',
               'MEDIUM', 1, 'Y', 1340
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'SVC_KN_OMS' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 5. AWS방화벽 (SEC_AWS_FW) — 샘플 확인 전 초안
--    형식: AWS Network Firewall / 보안그룹 변경(CloudTrail) / key=value 형식
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'NETWORK', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'AFW_ANY_OPEN' AS RULE_CODE, '전체 개방(0.0.0.0/0) 규칙 추가' AS RULE_NAME,
               '[샘플 확인 전 초안] 보안그룹·방화벽 규칙에 0.0.0.0/0(::/0) 전체 허용 추가. 외부 노출 서비스인지 즉시 확인 (ISMS 2.6.1 네트워크 접근)' AS DESCRIPTION,
               '(AuthorizeSecurityGroupIngress|CreateRuleGroup|UpdateRuleGroup|action=CONFIG|rule (add|added|modify)).*(0\\.0\\.0\\.0/0|::/0)' AS PATTERN,
               'CRITICAL' AS SEVERITY, 1 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 1400 AS SORT_ORDER
        UNION ALL SELECT 'AFW_RULE_DELETE', '방화벽 정책·규칙 삭제',
               '[샘플 확인 전 초안] 방화벽 정책·규칙 그룹·보안그룹 삭제 또는 규칙 회수. 변경 승인 여부와 통제 공백 확인 (ISMS 2.6.1)',
               '"eventName"\\s*:\\s*"(DeleteFirewall(Policy)?|DeleteRuleGroup|DeleteSecurityGroup|RevokeSecurityGroup(Ingress|Egress))"|policy=DELETE|(방화벽|보안) ?(정책|규칙).{0,10}삭제',
               'HIGH', 1, 'Y', 1410
        UNION ALL SELECT 'AFW_POLICY_CHANGE', '방화벽 정책·규칙 변경',
               '[샘플 확인 전 초안] 방화벽 정책·규칙 그룹 생성·수정, 보안그룹 규칙 추가. 변경 요청·승인 내역과 대조 (ISMS 2.6.1, 2.10.1)',
               '"eventName"\\s*:\\s*"(UpdateFirewallPolicy|UpdateRuleGroup|CreateRuleGroup|CreateFirewallPolicy|AuthorizeSecurityGroup(Ingress|Egress)|ModifySecurityGroupRules|AssociateFirewallPolicy)"|action=CONFIG\\b',
               'MEDIUM', 1, 'Y', 1420
        UNION ALL SELECT 'AFW_ADMIN_PORT_EXTERNAL', '외부 IP → 관리 포트 허용 접속',
               '[샘플 확인 전 초안] 사설 대역이 아닌 IP에서 SSH(22)·RDP(3389)·DB(3306/1433/1521/5432) 포트로 허용된 접속(3건 이상). 관리 포트 외부 노출 여부 확인 (ISMS 2.6.6 원격접근 통제)',
               'action=(ACCEPT|ALLOW|PASS)\\s+src=(?!10\\.|172\\.(1[6-9]|2\\d|3[01])\\.|192\\.168\\.|127\\.)\\S+.*dport=(22|3389|3306|1433|1521|5432)\\b|"action"\\s*:\\s*"(allowed|pass)".*"src_ip"\\s*:\\s*"(?!10\\.|172\\.(1[6-9]|2\\d|3[01])\\.|192\\.168\\.)[\\d.]+".*"dest_port"\\s*:\\s*(22|3389|3306|1433|1521|5432)\\b',
               'HIGH', 3, 'Y', 1430
        UNION ALL SELECT 'AFW_IPS_ALERT', '침입 탐지(IPS) 경보',
               '[샘플 확인 전 초안] AWS Network Firewall 상태 저장 규칙(Suricata) 경보. 시그니처와 출발지 확인, 반복 출발지는 차단 검토 (ISMS 2.10.1)',
               '"event_type"\\s*:\\s*"alert"|\\bsignature(_id)?\\s*[=:]',
               'HIGH', 1, 'Y', 1440
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'SEC_AWS_FW' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 6. UTM (SEC_UTM) — 샘플 확인 전 초안
--    형식: UTM(통합 보안 장비) 시스템·관리자 이벤트 로그 (FortiGate 계열 key="value" 및 한글 장비 로그)
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'NETWORK', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'UTM_ADMIN_LOGIN_FAIL' AS RULE_CODE, '장비 관리자 로그인 실패' AS RULE_NAME,
               '[샘플 확인 전 초안] UTM 관리 화면·SSH 관리자 로그인 실패 5건 이상. 무차별 대입 여부, 관리 접속 IP 제한 설정 확인 (ISMS 2.5.3, 2.6.6)' AS DESCRIPTION,
               'logdesc="?admin login failed|관리자 ?로그인 ?실패|action="?login"?\\s.*status="?failed' AS PATTERN,
               'HIGH' AS SEVERITY, 5 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 1500 AS SORT_ORDER
        UNION ALL SELECT 'UTM_POLICY_CHANGE', '방화벽 정책 추가·수정·삭제',
               '[샘플 확인 전 초안] 방화벽 정책(firewall.policy) 추가·수정·삭제. 변경 요청·승인 내역과 대조 (ISMS 2.6.1, 2.10.1)',
               'cfgpath="?firewall\\.policy|(방화벽|보안) ?정책.{0,20}(추가|수정|삭제|변경)|firewall policy.{0,20}(add|edit|delet|chang)',
               'MEDIUM', 1, 'Y', 1510
        UNION ALL SELECT 'UTM_ANY_ALLOW', '전체 허용(any→any) 정책',
               '[샘플 확인 전 초안] 출발지·목적지가 모두 all/any 인 허용 정책 설정. 사실상 통제 해제이므로 즉시 확인 (ISMS 2.6.1)',
               'srcaddr="?all"?.{0,200}dstaddr="?all"?.{0,200}action="?accept|\\bany\\b.{0,10}\\bany\\b.{0,30}(allow|accept|허용)',
               'CRITICAL', 1, 'Y', 1520
        UNION ALL SELECT 'UTM_ADMIN_ACCOUNT_CHANGE', '장비 관리자 계정 변경',
               '[샘플 확인 전 초안] UTM 관리자 계정 추가·삭제·권한(프로필) 변경. 승인 내역과 대조 (ISMS 2.5.1/2.5.5)',
               'cfgpath="?system\\.admin\\b|관리자 ?계정.{0,10}(추가|생성|삭제|변경)|admin(istrator)? (account )?(added|deleted|changed)',
               'HIGH', 1, 'Y', 1530
        UNION ALL SELECT 'UTM_FIRMWARE_CONFIG', '펌웨어 변경·설정 복원·초기화',
               '[샘플 확인 전 초안] 펌웨어 업그레이드, 설정 파일 복원, 공장 초기화. 작업 승인·백업 여부 확인 (ISMS 2.10.1)',
               '(firmware|펌웨어).{0,20}(upgrade|업그레이드|변경|downgrade)|config(uration)? (file )?restor|설정 ?(복원|초기화)|factory ?reset',
               'HIGH', 1, 'Y', 1540
        UNION ALL SELECT 'UTM_LOG_SETTING_CHANGE', '로그 설정 변경·삭제',
               '[샘플 확인 전 초안] 로그 저장·전송(syslog) 설정 변경, 로그 비활성화·삭제. 감사 증적 훼손 여부 즉시 확인 (ISMS 2.9.4)',
               'cfgpath="?log\\.|log (setting|disk|syslogd?).{0,30}(disable|delete|chang|edit)|로그 ?(설정 ?변경|삭제|비활성|전송 ?중지)',
               'CRITICAL', 1, 'Y', 1550
        UNION ALL SELECT 'UTM_IPS_CRITICAL', 'IPS 심각·높음 공격 탐지',
               '[샘플 확인 전 초안] IPS 엔진이 심각(critical)·높음(high) 등급 공격을 탐지. 대상 서버 영향과 차단 여부 확인 (ISMS 2.10.1)',
               'subtype="?ips"?.{0,300}severity="?(critical|high)|crlevel="?(critical|high)|IPS.{0,20}(차단|탐지).{0,20}(심각|높음)',
               'HIGH', 1, 'Y', 1560
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'SEC_UTM' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 7. SSL VPN (SEC_SSL_VPN) — 샘플 확인 전 초안
--    형식: SSL VPN 인증·터널·관리자 로그 (장비 형식 미확인)
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'SECURITY', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'VPN_LOGIN_FAIL_MANY' AS RULE_CODE, 'VPN 인증 실패 다발' AS RULE_NAME,
               '[샘플 확인 전 초안] SSL VPN 로그인·인증 실패 10건 이상(장비 전용 표기). 무차별 대입·계정 도용 시도 확인 (ISMS 2.6.6 원격접근 통제)' AS DESCRIPTION,
               'ssl-login-fail|sslvpn_login_(permission_denied|unknown_user|cert_check_failed)|tunnel.{0,20}(fail|denied)|VPN.{0,20}(인증|로그인) ?실패' AS PATTERN,
               'HIGH' AS SEVERITY, 10 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 1600 AS SORT_ORDER
        UNION ALL SELECT 'VPN_UNKNOWN_USER', '없는 계정으로 VPN 접속 시도',
               '[샘플 확인 전 초안] 존재하지 않는 계정으로 VPN 접속 시도 3건 이상. 계정 추측 공격 여부 확인',
               'unknown user|invalid user|존재하지 않는 (사용자|계정)|sslvpn_login_unknown_user',
               'HIGH', 3, 'Y', 1610
        UNION ALL SELECT 'VPN_ACCOUNT_LOCK', 'VPN 계정 잠김',
               '[샘플 확인 전 초안] 인증 실패 누적으로 VPN 계정 잠김. 본인 실수인지, 외부 공격인지 사용자 확인 후 해제 (ISMS 2.5.3)',
               'account (is |was )?locked|계정 ?잠김|잠금 ?처리|lockout',
               'MEDIUM', 1, 'Y', 1620
        UNION ALL SELECT 'VPN_DUP_LOGIN', '동일 계정 중복(동시) 접속',
               '[샘플 확인 전 초안] 같은 계정으로 다른 IP에서 동시에 접속. 계정 공유·도용 여부 확인 (ISMS 2.5.2 사용자 식별)',
               'duplicate login|동시 ?접속|중복 ?로그인|already logged in|concurrent (login|session)',
               'MEDIUM', 1, 'Y', 1630
        UNION ALL SELECT 'VPN_ADMIN_CONFIG_CHANGE', 'VPN 사용자·접근 정책 변경',
               '[샘플 확인 전 초안] VPN 사용자·그룹 추가/삭제, 접근 정책·포털 설정 변경. 신청·승인 내역과 대조 (ISMS 2.5.1, 2.6.6)',
               '(VPN ?)?(사용자|그룹|접근 ?정책|포털|허용 ?IP).{0,20}(추가|등록|수정|삭제|변경)|vpn\\.(user|group|policy|portal).{0,30}(add|edit|delet)|cfgpath="?(user\\.local|user\\.group|vpn\\.ssl)',
               'HIGH', 1, 'Y', 1640
        UNION ALL SELECT 'VPN_OFF_HOUR_LOGIN', '심야(00~06시) VPN 접속',
               '[샘플 확인 전 초안] 00시~06시 VPN 접속 성공(한글·터널 표기). 야간 근무·장애 대응 등 사유 확인',
               '\\b0[0-5]:\\d{2}:\\d{2}\\b.*(로그인 ?성공|접속 ?성공|tunnel-up|tunnel established)',
               'LOW', 1, 'Y', 1650
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'SEC_SSL_VPN' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 8. GIT (SEC_GIT) — 샘플 확인 전 초안
--    형식: GitLab/GitHub 감사 로그 (JSON) 및 한글 감사 로그
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'APP', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'GIT_REPO_DELETE' AS RULE_CODE, '저장소(프로젝트) 삭제' AS RULE_NAME,
               '[샘플 확인 전 초안] 저장소·프로젝트 삭제. 승인 여부와 백업 확인, 소스 증적 훼손 여부 확인' AS DESCRIPTION,
               'repo\\.destroy|project_(destroyed|deleted)|(저장소|리포지토리|프로젝트) ?삭제|(removed|deleted) (the )?(project|repository)' AS PATTERN,
               'HIGH' AS SEVERITY, 1 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 1700 AS SORT_ORDER
        UNION ALL SELECT 'GIT_REPO_PUBLIC', '저장소 공개(public) 전환',
               '[샘플 확인 전 초안] 저장소·프로젝트 공개 범위를 public(공개)으로 변경. 소스코드·비밀정보 외부 노출 위험이므로 즉시 확인 (ISMS 2.8.6 소스 프로그램 관리)',
               'visibility.{0,40}(public|공개)|repo\\.access.{0,60}public|made (the )?(repository|project) public|공개 ?(저장소|프로젝트)로 변경',
               'HIGH', 1, 'Y', 1710
        UNION ALL SELECT 'GIT_PERMISSION_CHANGE', '저장소 권한·멤버 변경',
               '[샘플 확인 전 초안] 멤버 추가·삭제, 접근 권한(Owner/Maintainer/Admin) 변경. 신청·승인 내역과 대조 (ISMS 2.5.1, 2.8.6)',
               '(access ?level|member_role|\\brole\\b).{0,30}(changed|updated|변경)|\\bmember_(updated|added|removed|created|destroyed)\\b|\\b(add|remove)(ed)?_member\\b|org\\.(add|remove)_member|(Owner|Maintainer|Admin) (access )?granted|권한.{0,10}(부여|변경|회수)',
               'HIGH', 1, 'Y', 1720
        UNION ALL SELECT 'GIT_BRANCH_PROTECT_OFF', '보호 브랜치 해제',
               '[샘플 확인 전 초안] main/release 등 보호 브랜치 해제·규칙 삭제. 검토 없는 배포 경로가 생기는지 확인 (ISMS 2.8.6)',
               'protected_branch\\.(destroy|policy_override)|unprotect(ed)? branch|branch protection.{0,20}(removed|disabled|deleted)|보호 ?브랜치.{0,10}(해제|삭제)',
               'HIGH', 1, 'Y', 1730
        UNION ALL SELECT 'GIT_FORCE_PUSH', '강제 푸시(force push)',
               '[샘플 확인 전 초안] 이력을 덮어쓰는 강제 푸시. 코드 이력 변조 여부 확인',
               'force[ -_]?push|forced update|강제 ?푸시',
               'MEDIUM', 1, 'Y', 1740
        UNION ALL SELECT 'GIT_TOKEN_CREATE', '접근 토큰·배포 키 생성',
               '[샘플 확인 전 초안] 개인 접근 토큰(PAT)·배포 키·SSH 키 생성. 만료 기한과 사용 목적 확인 (비밀정보 관리)',
               '(personal_access_token|access[ _]token|deploy[ _]key|ssh[ _]key).{0,30}(creat|add|생성|추가)|(토큰|배포 ?키|SSH ?키).{0,10}(생성|발급|추가)',
               'MEDIUM', 1, 'Y', 1750
        UNION ALL SELECT 'GIT_ARCHIVE_DOWNLOAD', '저장소 압축 다운로드·내보내기',
               '[샘플 확인 전 초안] 저장소 전체 압축(zip/tar) 다운로드, 프로젝트 내보내기. 소스코드 반출 목적 확인 (ISMS 2.10.8)',
               'repo\\.download_zip|archive.{0,20}download|download.{0,20}archive|export(ed)? project|project_export|프로젝트 ?내보내기|소스 ?다운로드',
               'MEDIUM', 1, 'Y', 1760
        UNION ALL SELECT 'GIT_MFA_DISABLE', '2단계 인증(2FA) 해제',
               '[샘플 확인 전 초안] 사용자 또는 조직의 2단계 인증 해제. 계정 탈취 위험 증가 여부 확인 (ISMS 2.5.3 사용자 인증)',
               '(two[- ]factor|2fa|mfa|otp).{0,30}(disabled|removed|off|해제|비활성)|disable_two_factor',
               'HIGH', 1, 'Y', 1770
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'SEC_GIT' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 9. V3 EPP (SEC_V3_EPP) — 샘플 확인 전 초안
--    형식: AhnLab V3/EPP 관리 콘솔 탐지·정책·에이전트 로그 (한글)
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'SECURITY', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'V3_MALWARE_DETECT' AS RULE_CODE, '악성코드 탐지' AS RULE_NAME,
               '[샘플 확인 전 초안] PC·서버에서 악성코드·바이러스 탐지. 치료 결과, 유입 경로(메일·USB·웹), 같은 PC 반복 탐지 확인 (ISMS 2.10.9 악성코드 통제)' AS DESCRIPTION,
               '(악성 ?코드|바이러스|트로이 ?목마|웜|애드웨어|malware|virus|trojan|worm).{0,40}(탐지|발견|진단|detected|found)' AS PATTERN,
               'HIGH' AS SEVERITY, 1 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 1800 AS SORT_ORDER
        UNION ALL SELECT 'V3_CLEAN_FAIL', '악성코드 치료·격리 실패',
               '[샘플 확인 전 초안] 탐지된 악성코드 치료·삭제·격리 실패. 감염 상태가 남아 있으므로 당일 격리·수동 조치 (ISMS 2.10.9, 2.11 사고 대응)',
               '(치료|삭제|격리).{0,10}(실패|불가)|(clean|quarantine|delet|remov)\\w*\\s+fail',
               'CRITICAL', 1, 'Y', 1810
        UNION ALL SELECT 'V3_RANSOMWARE', '랜섬웨어 탐지·차단',
               '[샘플 확인 전 초안] 랜섬웨어(행위 기반 포함) 탐지·차단. 네트워크 격리, 공유 폴더 암호화 여부 즉시 확인 (침해사고 대응)',
               '랜섬웨어|ransom',
               'CRITICAL', 1, 'Y', 1820
        UNION ALL SELECT 'V3_PROTECTION_OFF', '실시간 검사·자기 보호 해제',
               '[샘플 확인 전 초안] 실시간 검사·행위 기반 진단·자기 보호 기능 해제. 사용자 임의 해제인지 확인하고 정책으로 강제 (ISMS 2.10.9)',
               '(실시간 ?(검사|감시)|행위 ?기반 ?진단|자기 ?보호|real[- ]?time (scan|protection)|self[- ]?protection).{0,20}(해제|중지|사용 ?안 ?함|끔|비활성|disabled|off|stopped)',
               'HIGH', 1, 'Y', 1830
        UNION ALL SELECT 'V3_AGENT_REMOVE', '에이전트(V3) 삭제',
               '[샘플 확인 전 초안] PC·서버에서 V3/EPP 에이전트 삭제·제거. 승인된 PC 교체·폐기인지 확인, 미보호 단말 발생 여부 확인 (ISMS 2.10.9)',
               '(에이전트|agent|V3).{0,20}(삭제|제거|uninstall|removed)',
               'HIGH', 1, 'Y', 1840
        UNION ALL SELECT 'V3_UPDATE_FAIL', '엔진 업데이트 실패 반복',
               '[샘플 확인 전 초안] 엔진·패턴 업데이트 실패 5건 이상. 오래된 엔진으로 신규 악성코드를 놓칠 수 있음, 네트워크·라이선스 확인',
               '(엔진|패턴|engine|pattern).{0,20}(업데이트|update).{0,20}(실패|fail)',
               'LOW', 5, 'Y', 1850
        UNION ALL SELECT 'V3_POLICY_CHANGE', '보안 정책 변경',
               '[샘플 확인 전 초안] 관리 콘솔에서 검사·차단 정책 추가·수정·삭제·적용 대상 변경. 변경 요청·승인 내역과 대조 (ISMS 2.10.1)',
               '(정책|policy).{0,30}(변경|수정|삭제|추가|적용 ?대상|changed|modified|deleted)',
               'MEDIUM', 1, 'Y', 1860
        UNION ALL SELECT 'V3_EXCLUSION_ADD', '검사 예외 추가',
               '[샘플 확인 전 초안] 검사 예외(파일·폴더·프로세스) 추가. 악성코드 은닉 경로로 악용될 수 있어 사유 확인 (ISMS 2.10.9)',
               '(검사 ?)?예외.{0,20}(추가|등록|설정)|(exclusion|exception).{0,20}(add|register)',
               'HIGH', 1, 'Y', 1870
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'SEC_V3_EPP' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 10. AWS console (SEC_AWS_CONSOLE) — 샘플 확인 전 초안
--    형식: AWS CloudTrail 이벤트 (JSON 한 줄 = 이벤트 1건)
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'CLOUD', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'AWS_ROOT_LOGIN' AS RULE_CODE, '루트 계정 콘솔 로그인' AS RULE_NAME,
               '[샘플 확인 전 초안] AWS 루트(root) 계정으로 콘솔 로그인. 루트 계정은 일상 사용 금지(CIS AWS 1.7), 사용 사유 확인 (ISMS 2.5.5 특수 권한)' AS DESCRIPTION,
               '"type"\\s*:\\s*"Root".*"eventName"\\s*:\\s*"ConsoleLogin"|"eventName"\\s*:\\s*"ConsoleLogin".*"type"\\s*:\\s*"Root"' AS PATTERN,
               'HIGH' AS SEVERITY, 1 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 1900 AS SORT_ORDER
        UNION ALL SELECT 'AWS_CONSOLE_NO_MFA', 'MFA 없이 콘솔 로그인',
               '[샘플 확인 전 초안] MFA(2단계 인증) 없이 콘솔 로그인. 해당 IAM 사용자 MFA 설정 강제 (CIS AWS 1.10, ISMS 2.5.3)',
               '"MFAUsed"\\s*:\\s*"No"',
               'MEDIUM', 1, 'Y', 1910
        UNION ALL SELECT 'AWS_LOGIN_FAIL', '콘솔 로그인 실패 다발',
               '[샘플 확인 전 초안] 콘솔 로그인 실패 5건 이상. 무차별 대입·계정 도용 시도 확인 (ISMS 2.5.3)',
               '"ConsoleLogin"\\s*:\\s*"Failure"',
               'HIGH', 5, 'Y', 1920
        UNION ALL SELECT 'AWS_IAM_CHANGE', 'IAM 사용자·권한·액세스 키 변경',
               '[샘플 확인 전 초안] IAM 사용자·역할 생성/삭제, 정책 연결, 액세스 키 생성, MFA 장치 해제. 승인 내역과 대조 (ISMS 2.5.1/2.5.5)',
               '"eventName"\\s*:\\s*"(CreateUser|DeleteUser|CreateAccessKey|UpdateAccessKey|AttachUserPolicy|AttachRolePolicy|AttachGroupPolicy|PutUserPolicy|PutRolePolicy|PutGroupPolicy|CreateLoginProfile|UpdateLoginProfile|AddUserToGroup|CreateRole|UpdateAssumeRolePolicy|DeactivateMFADevice|DeleteVirtualMFADevice)"',
               'HIGH', 1, 'Y', 1930
        UNION ALL SELECT 'AWS_SG_OPEN_ANY', '보안그룹 전체 개방(0.0.0.0/0)',
               '[샘플 확인 전 초안] 보안그룹 인바운드에 0.0.0.0/0(::/0) 허용 추가. 외부 노출 서비스인지 즉시 확인 (CIS AWS 5.2, ISMS 2.6.1)',
               '"eventName"\\s*:\\s*"AuthorizeSecurityGroupIngress".*"cidrIpv?6?"\\s*:\\s*"(0\\.0\\.0\\.0/0|::/0)"',
               'CRITICAL', 1, 'Y', 1940
        UNION ALL SELECT 'AWS_LOG_TAMPER', '감사 로그(CloudTrail 등) 중지·삭제',
               '[샘플 확인 전 초안] CloudTrail 중지·삭제·변경, VPC 흐름 로그·GuardDuty·Config·Security Hub 중지. 감사 증적 훼손 여부 즉시 확인 (CIS AWS 4.5, ISMS 2.9.4)',
               '"eventName"\\s*:\\s*"(StopLogging|DeleteTrail|UpdateTrail|PutEventSelectors|DeleteFlowLogs|DeleteDetector|DisableSecurityHub|DeleteConfigurationRecorder|StopConfigurationRecorder|DeleteDeliveryChannel)"',
               'CRITICAL', 1, 'Y', 1950
        UNION ALL SELECT 'AWS_S3_POLICY_CHANGE', 'S3 버킷 정책·공개 설정 변경',
               '[샘플 확인 전 초안] S3 버킷 ACL·정책 변경, 퍼블릭 액세스 차단 해제. 데이터 외부 공개 여부 확인 (CIS AWS 2.1, ISMS 2.10.8)',
               '"eventName"\\s*:\\s*"(PutBucketAcl|PutBucketPolicy|DeleteBucketPolicy|PutBucketPublicAccessBlock|DeleteBucketPublicAccessBlock|DeletePublicAccessBlock|PutObjectAcl)"',
               'HIGH', 1, 'Y', 1960
        UNION ALL SELECT 'AWS_KMS_DELETE', '암호화 키(KMS) 삭제 예약·비활성화',
               '[샘플 확인 전 초안] KMS 고객 관리형 키 삭제 예약·비활성화. 암호화 데이터 복구 불가 위험 확인 (CIS AWS 4.7, ISMS 2.7.2 암호키 관리)',
               '"eventName"\\s*:\\s*"(ScheduleKeyDeletion|DisableKey|DisableKeyRotation)"',
               'HIGH', 1, 'Y', 1970
        UNION ALL SELECT 'AWS_ACCESS_DENIED', '권한 없는 API 호출 다발',
               '[샘플 확인 전 초안] AccessDenied·UnauthorizedOperation 오류 20건 이상. 탈취된 키로 권한 탐색(정찰) 중인지 확인 (CIS AWS 4.1)',
               '"errorCode"\\s*:\\s*"(AccessDenied|Client\\.UnauthorizedOperation|UnauthorizedOperation)"',
               'MEDIUM', 20, 'Y', 1980
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'SEC_AWS_CONSOLE' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 11. WAF (SEC_WAF) — 샘플 확인 전 초안
--    형식: 웹 방화벽 관리자 감사·공격 탐지 로그 (장비 형식 미확인)
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'SECURITY', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'WAF_ADMIN_LOGIN_FAIL' AS RULE_CODE, 'WAF 관리자 로그인 실패' AS RULE_NAME,
               '[샘플 확인 전 초안] WAF 관리 콘솔 관리자 로그인 실패 5건 이상. 무차별 대입 여부, 관리 접속 IP 제한 확인 (ISMS 2.5.3, 2.6.6)' AS DESCRIPTION,
               '(관리자|admin).{0,20}(로그인|login).{0,20}(실패|fail)' AS PATTERN,
               'HIGH' AS SEVERITY, 5 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 2000 AS SORT_ORDER
        UNION ALL SELECT 'WAF_PROTECTION_OFF', '차단 해제·탐지 모드 전환',
               '[샘플 확인 전 초안] 보호 정책을 사용 안 함·바이패스·탐지 전용(모니터링) 모드로 전환. 차단이 멈춘 기간과 사유 확인 (ISMS 2.10.1)',
               '(차단|보호|정책|룰|규칙|policy|rule|protection).{0,20}(해제|비활성|사용 ?안 ?함|disabled|bypass|탐지 ?(전용 ?)?모드|detect(ion)?[ -]only|monitor(ing)? mode)|bypass mode',
               'HIGH', 1, 'Y', 2010
        UNION ALL SELECT 'WAF_POLICY_CHANGE', '탐지 정책·시그니처 변경',
               '[샘플 확인 전 초안] 탐지 정책·룰·시그니처 추가·수정·삭제. 변경 요청·승인 내역과 대조 (ISMS 2.10.1)',
               '(정책|룰|규칙|시그니처|policy|rule|signature).{0,20}(추가|수정|삭제|변경|added|modified|deleted|changed)',
               'MEDIUM', 1, 'Y', 2020
        UNION ALL SELECT 'WAF_ALLOWLIST_ADD', '예외(허용 IP·URL) 추가',
               '[샘플 확인 전 초안] 허용 IP·URL 예외, 화이트리스트 추가. 공격 우회 경로로 악용될 수 있어 사유와 기한 확인',
               '(예외|화이트 ?리스트|허용 ?(IP|URL)|white ?list|allow ?list|exception).{0,20}(추가|등록|added|registered)',
               'HIGH', 1, 'Y', 2030
        UNION ALL SELECT 'WAF_SQLI_ATTACK', 'SQL 인젝션 공격 다발',
               '[샘플 확인 전 초안] SQL 인젝션 공격 탐지 10건 이상. 반복 출발지 차단, 대상 URL 취약점 점검 (ISMS 2.10.1)',
               'sql ?injection|\\bsqli\\b|SQL ?(인젝션|삽입)',
               'HIGH', 10, 'Y', 2040
        UNION ALL SELECT 'WAF_WEBSHELL', '웹셸·악성 파일 업로드 시도',
               '[샘플 확인 전 초안] 웹셸 업로드·실행, 악성 파일 업로드 시도. 차단 여부와 서버에 파일이 남았는지 즉시 확인 (침해사고 대응)',
               'web ?shell|웹 ?쉘|웹셸|악성 ?파일 ?업로드|malicious file upload',
               'CRITICAL', 1, 'Y', 2050
        UNION ALL SELECT 'WAF_ADMIN_ACCOUNT_CHANGE', 'WAF 관리자 계정 변경',
               '[샘플 확인 전 초안] WAF 관리자 계정 추가·삭제·권한 변경. 승인 내역과 대조 (ISMS 2.5.1/2.5.5)',
               '(관리자|운영자|admin(istrator)?) ?(계정)?.{0,10}(추가|생성|삭제|권한 ?변경|added|deleted|created)',
               'HIGH', 1, 'Y', 2060
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'SEC_WAF' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 12. AP/스위치 (NET_AP_SWITCH) — 샘플 확인 전 초안
--    형식: 네트워크 장비 syslog (Cisco IOS 계열 %FACILITY-SEVERITY-MNEMONIC, 한글 장비 로그)
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'NETWORK', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'NETSW_LOGIN_FAIL' AS RULE_CODE, '장비 로그인 실패 다발' AS RULE_NAME,
               '[샘플 확인 전 초안] 스위치·AP 관리 접속(SSH/콘솔) 로그인 실패 5건 이상(장비 전용 표기). 무차별 대입 여부, 관리 접속 ACL 확인 (ISMS 2.6.6)' AS DESCRIPTION,
               '%SEC_LOGIN-\\d-LOGIN_FAILED|%AAA-\\d-\\w*FAIL|%SSH-\\d-\\w*(FAIL|AUTH)' AS PATTERN,
               'HIGH' AS SEVERITY, 5 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 2200 AS SORT_ORDER
        UNION ALL SELECT 'NETSW_CONFIG_CHANGE', '장비 설정 변경·저장',
               '[샘플 확인 전 초안] 설정 모드 진입·변경, 설정 저장(write memory). 변경 요청·승인 내역과 대조 (ISMS 2.10.1)',
               '%SYS-5-CONFIG_I|Configured from|%PARSER-5-CFGLOG_LOGGEDCMD|write memory|copy running-config startup-config|설정 ?(변경|저장)',
               'MEDIUM', 1, 'Y', 2210
        UNION ALL SELECT 'NETSW_ACL_CHANGE', '접근 제어 목록(ACL) 변경',
               '[샘플 확인 전 초안] access-list·access-group 추가·삭제. 망 분리·관리 접속 제한이 풀리는지 확인 (ISMS 2.6.1)',
               'CFGLOG_LOGGEDCMD.*\\b(no )?(ip )?access-(list|group)\\b|ACL.{0,10}(추가|삭제|변경)',
               'HIGH', 1, 'Y', 2220
        UNION ALL SELECT 'NETSW_USER_CHANGE', '장비 계정·비밀번호·SNMP 변경',
               '[샘플 확인 전 초안] 로컬 계정(username)·enable 비밀번호·SNMP 커뮤니티 변경. 승인 여부, 기본 커뮤니티(public/private) 사용 금지 확인 (ISMS 2.5.1, 2.5.4)',
               'CFGLOG_LOGGEDCMD.*\\b(username|enable (secret|password)|snmp-server community)\\b|관리자 ?계정.{0,10}(추가|삭제|변경)',
               'HIGH', 1, 'Y', 2230
        UNION ALL SELECT 'NETSW_RELOAD', '장비 재부팅',
               '[샘플 확인 전 초안] 장비 재부팅·재시작. 계획된 작업인지, 장애·임의 재기동인지 확인',
               '%SYS-5-(RELOAD|RESTART)|reload requested|system (reboot|restart)|장비 ?(재부팅|재시작)',
               'HIGH', 1, 'Y', 2240
        UNION ALL SELECT 'NETSW_LOG_CLEAR', '장비 로그 삭제·전송 중지',
               '[샘플 확인 전 초안] 로그 버퍼 삭제(clear logging), 로그 서버 전송 중지(no logging). 감사 증적 훼손 여부 즉시 확인 (ISMS 2.9.4)',
               '%SYS-6-LOGGINGCLEARED|clear logging|CFGLOG_LOGGEDCMD.*\\bno logging\\b|로그 ?(삭제|전송 ?중지)',
               'CRITICAL', 1, 'Y', 2250
        UNION ALL SELECT 'NETSW_ROGUE_AP', '비인가 AP 탐지',
               '[샘플 확인 전 초안] 무선 컨트롤러가 비인가(Rogue) AP를 탐지. 사내 임의 설치 AP인지 확인 후 차단 (ISMS 2.6.7 무선 네트워크 접근)',
               'rogue ?ap|비인가 ?AP|unauthorized (ap|access point)',
               'HIGH', 1, 'Y', 2260
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'NET_AP_SWITCH' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 13. DBMS(OMS, 홈페이지) (DB_OMS_HOME) — 샘플 확인 전 초안
--    형식: DB 접속·쿼리 감사 로그 (MariaDB/MySQL server_audit, Oracle/MSSQL 감사 표기)
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_rule
    (SYSTEM_ID, RULE_CODE, RULE_NAME, DESCRIPTION, LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN, SORT_ORDER, CREATED_BY)
SELECT s.SYSTEM_ID, v.RULE_CODE, v.RULE_NAME, v.DESCRIPTION, 'DB', 'REGEX', v.PATTERN, v.SEVERITY, v.THRESHOLD_COUNT, v.USE_YN, v.SORT_ORDER, NULL
  FROM sec_log_system s
  JOIN (
        SELECT 'DBA_ACCOUNT_CHANGE' AS RULE_CODE, 'DB 계정 생성·삭제·비밀번호 변경' AS RULE_NAME,
               '[샘플 확인 전 초안] CREATE/DROP/ALTER/RENAME USER, SET PASSWORD. DB 계정 신청·승인 내역과 대조 (ISMS 2.5.1)' AS DESCRIPTION,
               '\\b(create|drop|alter|rename)\\s+user\\b|\\bset\\s+password\\b|\\bidentified\\s+by\\b' AS PATTERN,
               'HIGH' AS SEVERITY, 1 AS THRESHOLD_COUNT, 'Y' AS USE_YN, 2300 AS SORT_ORDER
        UNION ALL SELECT 'DBA_PRIV_GRANT', 'DB 고위험 권한 부여',
               '[샘플 확인 전 초안] ALL·SUPER·FILE·PROCESS·DBA 등 고위험 권한 또는 WITH GRANT OPTION 부여. 최소 권한 원칙 위반 여부 확인 (ISMS 2.5.5)',
               '\\bgrant\\s+(all|super|file|process|shutdown|reload|dba|sysdba|create user)\\b|\\bwith\\s+grant\\s+option\\b',
               'HIGH', 1, 'Y', 2310
        UNION ALL SELECT 'DBA_LOGIN_FAIL', 'DB 접속 실패 다발',
               '[샘플 확인 전 초안] DB 접속 인증 실패 10건 이상(MariaDB FAILED_CONNECT, Oracle ORA-01017/28000). 무차별 대입·계정 정보 유출 확인 (ISMS 2.5.3)',
               '\\bFAILED_CONNECT\\b|access denied for user|ORA-(01017|28000)\\b',
               'HIGH', 10, 'Y', 2320
        UNION ALL SELECT 'DBA_SENSITIVE_QUERY', '주민·카드·계좌번호 컬럼 조회',
               '[샘플 확인 전 초안] 주민등록번호·카드·계좌·여권번호 컬럼 SELECT. 업무 목적·조회 범위 확인 (개인정보 접속기록 점검). 컬럼명은 실제 스키마에 맞게 조정 필요',
               '\\bselect\\b.{0,300}\\b(jumin\\w*|ssn|resident_?no|rrn|card_?no|account_?no|bank_?account|passport_?no)\\b',
               'MEDIUM', 1, 'Y', 2330
        UNION ALL SELECT 'DBA_DELETE_NO_WHERE', '조건 없는 DELETE(전체 삭제)',
               '[샘플 확인 전 초안] WHERE 조건 없는 DELETE FROM 테이블. 대량 데이터 삭제 사고·악의적 삭제 여부 확인, 백업 복구 필요성 검토',
               '\\bdelete\\s+from\\s+[\\w.`"\\[\\]]+\\s*(;|\\''|"|,|$)',
               'HIGH', 1, 'Y', 2340
        UNION ALL SELECT 'DBA_AUDIT_OFF', 'DB 감사 로그 중지',
               '[샘플 확인 전 초안] 감사 플러그인 중지·제거, NOAUDIT, 감사 대상 해제. 접속기록 증적 훼손 여부 즉시 확인 (ISMS 2.9.4, 개인정보 접속기록 보관)',
               'server_audit_logging\\s*=\\s*(off|0)\\b|\\bnoaudit\\b|uninstall\\s+plugin\\s+server_audit|audit_trail\\s*=\\s*none|alter\\s+server\\s+audit\\b.{0,100}state\\s*=\\s*off|general_log\\s*=\\s*(off|0)\\b',
               'CRITICAL', 1, 'Y', 2350
       ) v ON 1 = 1
 WHERE s.SYSTEM_CODE = 'DB_OMS_HOME' AND s.DELETED_YN = 'N';

-- ------------------------------------------------------------
-- 확인용 (조회만)
-- SELECT s.SYSTEM_NAME, COUNT(r.RULE_ID) AS 전용룰, SUM(r.USE_YN = 'Y') AS 사용
--   FROM sec_log_system s LEFT JOIN sec_log_rule r ON r.SYSTEM_ID = s.SYSTEM_ID AND r.DELETED_YN = 'N'
--  WHERE s.DELETED_YN = 'N' GROUP BY s.SYSTEM_ID, s.SYSTEM_NAME ORDER BY s.SYSTEM_ID;
-- ------------------------------------------------------------
