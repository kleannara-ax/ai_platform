-- ============================================================
--  module-kims : "KIMS 서울" 권한을 공통코드로 관리
--
--  KIMS_PERM(KIMS 관리자)과 같은 방식으로, 공통코드 그룹 'KIMS_PERM_SEOUL'
--  에 등록된 로그인 ID는 PC 관리(IP 관리)에서 서울 사업장 데이터만
--  조회/수정할 수 있다(청주는 완전히 차단됨). 다른 KIMS 기능/데이터는 노출되지 않는다.
--  (KIMS는 자체 사용자 테이블을 두지 않으며, 플랫폼 core_user 계정만 사용한다.)
--
--  KIMS_PERM(관리자)/플랫폼 ROLE_MANAGER 는 이 제한과 무관하게 항상
--  전체(청주+서울) 조회·수정이 가능하다 — 변경 없음.
--
--  운영 방법: 공통코드 관리 화면 → KIMS 서울 그룹 → 코드 추가
--             CODE = 플랫폼 로그인 ID, 코드명 = 사람 이름
--  KIMS_PERM 과 달리 초기 멤버를 자동으로 채우지 않는다(관리자가 화면에서 직접 등록).
--  재실행 안전.
-- ============================================================

SET NAMES utf8mb4;

-- 코드 그룹만 생성한다. 초기 멤버는 없음 — 관리자가 공통코드 관리 화면에서 등록한다.
INSERT INTO code_group (GROUP_CODE, GROUP_NAME, DESCRIPTION, IS_ACTIVE, SORT_ORDER)
VALUES ('KIMS_PERM_SEOUL', 'KIMS 서울', 'PC 관리(IP 관리)에서 서울 사업장 데이터만 조회·수정 가능한 로그인 ID 목록 (청주는 차단)', 1, 121)
ON DUPLICATE KEY UPDATE GROUP_NAME = VALUES(GROUP_NAME), DESCRIPTION = VALUES(DESCRIPTION), IS_ACTIVE = VALUES(IS_ACTIVE);

-- 확인용
-- SELECT d.CODE, d.CODE_NAME, d.IS_ACTIVE
--   FROM code_detail d JOIN code_group g ON g.GROUP_ID = d.GROUP_ID
--  WHERE g.GROUP_CODE = 'KIMS_PERM_SEOUL' ORDER BY d.SORT_ORDER;
