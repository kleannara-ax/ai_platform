-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 13_reorder_menu_add_safety_stats.sql
-- 설명: ★ 세부공장일보 하위 메뉴 순서 변경 + 신규 "사고 통계" 메뉴 추가 (2026-08)
--
--       [배경]
--       기존 세부공장일보 하위 메뉴는 다음 2개였다.
--         101 세부공장일보 입력       SORT_ORDER=1
--         102 세부공장일보 컬럼관리   SORT_ORDER=2
--
--       여기에 신규 "세부공장일보 사고 통계" 페이지(표 5~8: 안전사고
--       발생건수/손실금액/연도별 추이/월별 추이)를 추가하면서, 메뉴
--       순서를 다음과 같이 재배치한다.
--         101 세부공장일보 입력       SORT_ORDER=1 (변경 없음)
--         103 세부공장일보 사고 통계  SORT_ORDER=2 (신규)
--         102 세부공장일보 컬럼관리   SORT_ORDER=3 (2 → 3 변경, 최하단으로 이동)
--
--       [권한 정책]
--       세부공장일보 사고 통계(MENU_ID=103)는 표 5~8에 담당자별 DATA
--       셀(당월 실적 입력)이 존재하는 "입력 페이지"이므로, 기존 입력
--       페이지(MENU_ID=101)와 동일하게 9명 전원에게 READ+WRITE를
--       부여한다. 셀 단위 세부 권한은 기존과 동일하게
--       daily_report_cell_auth가 담당한다(별도 시드 불필요 — 운영
--       배포 초기에는 admin이 '세부공장일보 컬럼관리' 페이지에서
--       직접 배정).
--
-- 실행 순서:
--   1) "0. 사전 점검" — 현재 core_menu 상태 확인
--   2) "1. 메뉴 순서 변경" — MENU_ID=102 SORT_ORDER 2 → 3
--   3) "2. 신규 메뉴 추가" — MENU_ID=103 세부공장일보 사고 통계 (SORT_ORDER=2)
--   4) "3. 신규 메뉴 접근 권한" — core_menu_permission 9건 (101과 동일 패턴)
--   5) "4. 사후 검증" — 변경된 core_menu / core_menu_permission 확인
--
-- 주의: 이미 실행된 환경에서 재실행해도 안전하도록 INSERT는 IGNORE,
--       UPDATE는 결과가 같으면 재실행해도 무해하게 작성한다.
-- ============================================================

USE dailyreport_dev;

-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — 현재 core_menu 상태
-- ═══════════════════════════════════════════════
SELECT '=== 0. 사전 점검: 현재 세부공장일보 하위 메뉴 ===' AS section;

SELECT MENU_ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, MENU_URL, SORT_ORDER, IS_ACTIVE
  FROM core_menu
 WHERE MENU_ID = 100 OR PARENT_ID = 100
 ORDER BY SORT_ORDER, MENU_ID;


-- ═══════════════════════════════════════════════
-- 1. 메뉴 순서 변경 — 세부공장일보 컬럼관리(102)를 최하단으로
-- ═══════════════════════════════════════════════
SELECT '=== 1. 메뉴 순서 변경: 컬럼관리(102) SORT_ORDER 2 → 3 ===' AS section;

UPDATE core_menu
   SET SORT_ORDER = 3
 WHERE MENU_ID = 102
   AND MENU_CODE = 'DAILY_REPORT_AUTH';


-- ═══════════════════════════════════════════════
-- 2. 신규 메뉴 추가 — 세부공장일보 사고 통계 (MENU_ID=103, SORT_ORDER=2)
-- ═══════════════════════════════════════════════
SELECT '=== 2. 신규 메뉴 추가: 세부공장일보 사고 통계(103) ===' AS section;

INSERT IGNORE INTO core_menu
    (MENU_ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, MENU_URL, ICON, SORT_ORDER, DESCRIPTION, IS_ACTIVE)
VALUES
    (103, 100, 'DAILY_REPORT_SAFETY_STATS', '세부공장일보 사고 통계', 'PAGE', '/dailyreport/page/safety-stats', 'fa-solid fa-triangle-exclamation', 2, '안전사고 발생건수/손실금액/연도별·월별 추이 통계 페이지', 1);


-- ═══════════════════════════════════════════════
-- 3. 신규 메뉴 접근 권한 — core_menu_permission (101과 동일 패턴)
--    admin: READ+WRITE+DELETE+ADMIN, 8명 현업+environment: READ+WRITE
-- ═══════════════════════════════════════════════
SELECT '=== 3. 신규 메뉴(103) 접근 권한 부여 ===' AS section;

INSERT IGNORE INTO core_menu_permission
    (USER_ID, MENU_ID, CAN_READ, CAN_WRITE, CAN_DELETE, CAN_ADMIN, GRANTED_BY)
VALUES
    (1, 103, 1, 1, 1, 1, 1),   -- admin:  전체 권한
    (2, 103, 1, 1, 0, 0, 1),   -- kim:    읽기+쓰기
    (3, 103, 1, 1, 0, 0, 1),   -- park:   읽기+쓰기
    (4, 103, 1, 1, 0, 0, 1),   -- yoo:    읽기+쓰기
    (5, 103, 1, 1, 0, 0, 1),   -- jung:   읽기+쓰기
    (6, 103, 1, 1, 0, 0, 1),   -- jang:   읽기+쓰기
    (7, 103, 1, 1, 0, 0, 1),   -- lee:    읽기+쓰기
    (8, 103, 1, 1, 0, 0, 1),   -- choi:   읽기+쓰기
    (9, 103, 1, 1, 0, 0, 1);   -- energy: 읽기+쓰기


-- ═══════════════════════════════════════════════
-- 4. 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== 4. 사후 검증: 변경된 세부공장일보 하위 메뉴 순서 ===' AS section;

SELECT MENU_ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, MENU_URL, SORT_ORDER, IS_ACTIVE
  FROM core_menu
 WHERE MENU_ID = 100 OR PARENT_ID = 100
 ORDER BY SORT_ORDER, MENU_ID;

SELECT '=== 4-2. 사후 검증: 신규 메뉴(103) 접근 권한 ===' AS section;

SELECT mp.USER_ID, u.USER_NAME, mp.MENU_ID, m.MENU_NAME, mp.CAN_READ, mp.CAN_WRITE, mp.CAN_DELETE, mp.CAN_ADMIN
  FROM core_menu_permission mp
  JOIN core_menu m ON m.MENU_ID = mp.MENU_ID
  JOIN core_user u ON u.USER_ID = mp.USER_ID
 WHERE mp.MENU_ID = 103
 ORDER BY mp.USER_ID;

SELECT '=== 13_reorder_menu_add_safety_stats.sql 실행 완료 ===' AS message;
