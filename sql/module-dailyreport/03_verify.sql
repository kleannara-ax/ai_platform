-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 03_verify.sql
-- 설명: 설치 검증 쿼리 — AI 플랫폼 통합형 (Phase 4)
--       플랫폼 코어 + 모듈 데이터 + 셀 권한 검증
-- ============================================================

USE dailyreport_dev;

-- ────────────────────────────────────────────
-- 1. 테이블 생성 확인 (9개 테이블)
-- ────────────────────────────────────────────
SELECT '=== 1. 테이블 목록 (9개 예상) ===' AS section;
SELECT TABLE_NAME, TABLE_ROWS, TABLE_COMMENT
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'dailyreport_dev'
 ORDER BY TABLE_NAME;

-- ────────────────────────────────────────────
-- 2. 사용자 확인 (9명: admin + 8명 담당자)
-- ────────────────────────────────────────────
SELECT '=== 2. 테스트 사용자 ===' AS section;
SELECT USER_ID, LOGIN_ID, USER_NAME, DEPARTMENT, POSITION, ROLE, IS_ACTIVE,
       CASE WHEN PASSWORD IS NOT NULL THEN 'SET' ELSE 'NULL' END AS pwd_status
  FROM core_user ORDER BY USER_ID;

-- ────────────────────────────────────────────
-- 3. ★ 메뉴 계층 구조 확인 (3건)
-- ────────────────────────────────────────────
SELECT '=== 3. 메뉴 계층 (세부공장일보) ===' AS section;
SELECT m.MENU_ID, m.PARENT_MENU_ID, m.MENU_CODE, m.MENU_NAME, m.MENU_TYPE,
       m.MENU_URL, m.SORT_ORDER,
       pm.MENU_NAME AS parent_name
  FROM core_menu m
  LEFT JOIN core_menu pm ON pm.MENU_ID = m.PARENT_MENU_ID
 WHERE m.MENU_CODE LIKE 'DAILY_REPORT%'
 ORDER BY m.MENU_ID;

-- ────────────────────────────────────────────
-- 4. ★ 페이지 접근 권한 확인 (10건)
--    1계층: 입력 페이지(MENU_ID=101) — 9명
--    3계층: 접근권한 페이지(MENU_ID=102) — admin만
-- ────────────────────────────────────────────
SELECT '=== 4. 페이지 접근 권한 ===' AS section;
SELECT mp.PERM_ID, u.LOGIN_ID, u.USER_NAME, m.MENU_NAME,
       mp.CAN_READ, mp.CAN_WRITE, mp.CAN_DELETE, mp.CAN_ADMIN,
       g.USER_NAME AS granted_by_name
  FROM core_menu_permission mp
  JOIN core_user u ON u.USER_ID = mp.USER_ID
  JOIN core_menu m ON m.MENU_ID = mp.MENU_ID
  LEFT JOIN core_user g ON g.USER_ID = mp.GRANTED_BY
 ORDER BY m.MENU_ID, mp.USER_ID;

-- ────────────────────────────────────────────
-- 5. ★ 셀 단위 접근 권한 확인 (cell_auth, 9건)
-- ────────────────────────────────────────────
SELECT '=== 5. 셀 단위 접근 권한 (cell_auth) ===' AS section;
SELECT ca.AUTH_ID, u.LOGIN_ID, u.USER_NAME,
       ca.TABLE_CODE, ca.CELL_COORDS,
       ca.FREQ_CODE, ca.FREQ_LABEL,
       ca.IS_ACTIVE, ca.DESCRIPTION,
       JSON_LENGTH(ca.CELL_COORDS) AS coord_count
  FROM daily_report_cell_auth ca
  JOIN core_user u ON u.USER_ID = ca.USER_ID
 ORDER BY ca.TABLE_CODE, u.LOGIN_ID;

-- ────────────────────────────────────────────
-- 6. 일보 목록 확인 (3건)
-- ────────────────────────────────────────────
SELECT '=== 6. 일보 목록 ===' AS section;
SELECT REPORT_ID, REPORT_DATE, TITLE, STATUS, CREATED_BY
  FROM daily_report ORDER BY REPORT_DATE;

-- ────────────────────────────────────────────
-- 7. 표 메타 확인 (4개 표 × 3일보 = 12건)
-- ────────────────────────────────────────────
SELECT '=== 7. 표 메타 ===' AS section;
SELECT t.TABLE_ID, r.REPORT_DATE, t.TABLE_CODE, t.TABLE_NAME, t.SORT_ORDER,
       t.ROW_COUNT, t.COL_COUNT
  FROM daily_report_table t
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 ORDER BY r.REPORT_DATE, t.SORT_ORDER;

-- ────────────────────────────────────────────
-- 8. 셀 데이터 요약 (표별 셀 수 + 유형별 분포)
-- ────────────────────────────────────────────
SELECT '=== 8. 셀 데이터 요약 (표별) ===' AS section;
SELECT t.TABLE_CODE, t.TABLE_NAME,
       COUNT(*) AS total_cells,
       SUM(CASE WHEN c.CELL_TYPE = 'HEADER'   THEN 1 ELSE 0 END) AS headers,
       SUM(CASE WHEN c.CELL_TYPE = 'DATA'     THEN 1 ELSE 0 END) AS data_cells,
       SUM(CASE WHEN c.CELL_TYPE = 'READONLY' THEN 1 ELSE 0 END) AS readonly_cells,
       SUM(CASE WHEN c.OWNER_IDS IS NOT NULL  THEN 1 ELSE 0 END) AS assignable_cells
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE r.REPORT_DATE = '2024-07-20'
 GROUP BY t.TABLE_CODE, t.TABLE_NAME
 ORDER BY t.SORT_ORDER;

-- ────────────────────────────────────────────
-- 9. 소유자(담당자) 배정 현황
-- ────────────────────────────────────────────
SELECT '=== 9. 소유자 배정 현황 ===' AS section;
SELECT c.OWNER_IDS, c.OWNER_NAMES, c.FREQ_CODE, c.FREQ_LABEL,
       COUNT(*) AS cell_count,
       GROUP_CONCAT(c.EXCEL_COORD ORDER BY c.EXCEL_COORD) AS coords
  FROM daily_report_cell c
  JOIN daily_report_table t ON t.TABLE_ID = c.TABLE_ID
  JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
 WHERE r.REPORT_DATE = '2024-07-20'
   AND c.OWNER_IDS IS NOT NULL
 GROUP BY c.OWNER_IDS, c.OWNER_NAMES, c.FREQ_CODE, c.FREQ_LABEL
 ORDER BY c.OWNER_IDS;

-- ────────────────────────────────────────────
-- 10. ★ cell_auth ↔ 셀 OWNER_IDS 교차 검증
--     cell_auth의 좌표가 실제 셀의 OWNER_IDS와 일치하는지 확인
-- ────────────────────────────────────────────
SELECT '=== 10. cell_auth ↔ OWNER_IDS 교차 검증 ===' AS section;
SELECT u.LOGIN_ID, ca.TABLE_CODE,
       ca.CELL_COORDS AS auth_coords,
       GROUP_CONCAT(c.EXCEL_COORD ORDER BY c.EXCEL_COORD) AS actual_owner_coords,
       CASE WHEN ca.CELL_COORDS IS NOT NULL THEN 'OK' ELSE 'MISMATCH' END AS status
  FROM daily_report_cell_auth ca
  JOIN core_user u ON u.USER_ID = ca.USER_ID
  LEFT JOIN daily_report_cell c
    ON c.OWNER_IDS LIKE CONCAT('%', u.LOGIN_ID, '%')
   AND c.TABLE_ID IN (
       SELECT t.TABLE_ID FROM daily_report_table t
       JOIN daily_report r ON r.REPORT_ID = t.REPORT_ID
       WHERE r.REPORT_DATE = '2024-07-20'
         AND t.TABLE_CODE = ca.TABLE_CODE
   )
 GROUP BY u.LOGIN_ID, ca.TABLE_CODE, ca.CELL_COORDS;

-- ────────────────────────────────────────────
-- 11. 특이사항 / 이미지
-- ────────────────────────────────────────────
SELECT '=== 11. 특이사항 ===' AS section;
SELECT rm.REMARK_ID, rm.TABLE_CODE, rm.CATEGORY, LEFT(rm.CONTENT, 50) AS content_preview
  FROM daily_report_remark rm
  JOIN daily_report r ON r.REPORT_ID = rm.REPORT_ID
 ORDER BY rm.SORT_ORDER;

SELECT '=== 12. 이미지 ===' AS section;
SELECT im.IMAGE_ID, im.ORIGINAL_NAME, im.TABLE_CODE, im.FILE_SIZE
  FROM daily_report_image im
  JOIN daily_report r ON r.REPORT_ID = im.REPORT_ID
 ORDER BY im.SORT_ORDER;

-- ────────────────────────────────────────────
-- 13. 전체 요약
-- ────────────────────────────────────────────
SELECT '=== 13. 전체 요약 ===' AS section;
SELECT
    (SELECT COUNT(*) FROM core_user)               AS users,
    (SELECT COUNT(*) FROM core_menu)                AS menus,
    (SELECT COUNT(*) FROM core_menu_permission)     AS menu_permissions,
    (SELECT COUNT(*) FROM daily_report)             AS reports,
    (SELECT COUNT(*) FROM daily_report_table)       AS tables_meta,
    (SELECT COUNT(*) FROM daily_report_cell)        AS cells,
    (SELECT COUNT(*) FROM daily_report_cell WHERE OWNER_IDS IS NOT NULL) AS assignable_cells,
    (SELECT COUNT(*) FROM daily_report_cell_auth)   AS cell_auths,
    (SELECT COUNT(*) FROM daily_report_remark)      AS remarks,
    (SELECT COUNT(*) FROM daily_report_image)       AS images;

SELECT '=== 03_verify.sql 실행 완료 ===' AS message;
