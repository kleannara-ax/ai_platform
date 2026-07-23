-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 02_seed_data.sql
-- 설명: AI 플랫폼 통합형 시드 데이터
--       - 플랫폼 코어: core_user, core_menu, core_menu_permission
--       - 모듈 데이터: 일보 3건, 표 12건, 셀 320건
--       - ★ 신규: daily_report_cell_auth (셀 단위 접근 권한)
-- 실행 순서: 2번째 (01_schema.sql 실행 후)
-- ============================================================
-- 변경이력:
--   v1.0: 최초 작성 (Phase 1-2)
--   v2.0: HTML 원본 기준 4개 표 + 셀 소유권 (Phase 3)
--   v3.0 (현재): AI 플랫폼 통합 — 메뉴/권한/셀권한 (Phase 4)
--     - core_menu: 세부공장일보 카테고리 + 2개 하위 페이지
--     - core_menu_permission: 사용자별 페이지 접근 권한
--     - daily_report_cell_auth: 사용자별 셀 단위 접근 권한 (레거시 cell_permission 대체)
-- ============================================================

USE dailyreport_dev;

-- ═══════════════════════════════════════════════
-- 0. 테스트 사용자 (core_user — 플랫폼 기존 데이터 재사용)
--    PASSWORD: BCrypt('password123') — 테스트용
-- ═══════════════════════════════════════════════
-- ★ 컬럼 수정: DEPARTMENT/POSITION/IS_ACTIVE는 01_schema.sql의 실제 core_user
--   스키마(V2.0.0 운영 기준)에 존재하지 않는다 (부서/직급은 user_profile로 분리됨,
--   IS_ACTIVE는 enabled로 대체됨). role 값도 실제 컬럼 기본값(ROLE_USER/ROLE_ADMIN)
--   포맷에 맞춘다.
INSERT IGNORE INTO core_user (USER_ID, LOGIN_ID, USER_NAME, PASSWORD, EMAIL, ROLE, ENABLED) VALUES
    (1, 'admin', '관리자',           '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'admin@factory.co.kr',   'ROLE_ADMIN', 1),
    (2, 'kim',   '김완중 팀장',      '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'kim@factory.co.kr',     'ROLE_USER',  1),
    (3, 'park',  '박지권 책임',      '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'park@factory.co.kr',    'ROLE_USER',  1),
    (4, 'yoo',   '유동현 책임',      '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'yoo@factory.co.kr',     'ROLE_USER',  1),
    (5, 'jung',  '정상엽 책임',      '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'jung@factory.co.kr',    'ROLE_USER',  1),
    (6, 'jang',  '장석환 선임',      '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'jang@factory.co.kr',    'ROLE_USER',  1),
    (7, 'lee',   '이도형 사원',      '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'lee@factory.co.kr',     'ROLE_USER',  1),
    (8, 'choi',  '최민우 사원',      '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'choi@factory.co.kr',    'ROLE_USER',  1),
    (9, 'energy','환경에너지팀 반장', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'energy@factory.co.kr',  'ROLE_USER',  1);


-- ═══════════════════════════════════════════════
-- 1. ★★ core_menu — AI 플랫폼 메뉴 계층
--    세부공장일보(CATEGORY) → 세부공장일보 입력(PAGE) + 세부공장일보 컬럼관리(PAGE)
-- ═══════════════════════════════════════════════
INSERT IGNORE INTO core_menu (MENU_ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, MENU_URL, ICON, SORT_ORDER, DESCRIPTION, IS_ACTIVE) VALUES
    (100, NULL, 'DAILY_REPORT',       '세부공장일보',         'CATEGORY', NULL,                                  'fa-solid fa-clipboard-list', 10, '세부공장일보 카테고리', 1),
    (101, 100,  'DAILY_REPORT_INPUT',  '세부공장일보 입력',    'PAGE',     '/dailyreport/page',             'fa-solid fa-edit',           1,  '세부공장일보 데이터 입력 페이지', 1),
    (102, 100,  'DAILY_REPORT_AUTH',   '세부공장일보 컬럼관리', 'PAGE',     '/dailyreport/page/column-mgmt',   'fa-solid fa-user-shield',    2,  '셀 단위 접근 권한 관리 페이지 (관리자용)', 1);


-- ═══════════════════════════════════════════════
-- 2. ★★ core_menu_permission — 사용자별 페이지 접근 권한
--
--    [1계층] 세부공장일보 입력 (MENU_ID=101)
--      → admin: READ + WRITE + ADMIN
--      → 8명 현업: READ + WRITE (자기 셀만 입력, 셀 레벨 권한은 cell_auth에서 제어)
--
--    [3계층] 세부공장일보 컬럼관리 (MENU_ID=102)
--      → admin: READ + WRITE + ADMIN (관리자만 접근 가능)
-- ═══════════════════════════════════════════════

-- 1계층: 세부공장일보 입력 페이지 접근 권한
INSERT IGNORE INTO core_menu_permission (USER_ID, MENU_ID, CAN_READ, CAN_WRITE, CAN_DELETE, CAN_ADMIN, GRANTED_BY) VALUES
    (1, 101, 1, 1, 1, 1, 1),   -- admin:  전체 권한
    (2, 101, 1, 1, 0, 0, 1),   -- kim:    읽기+쓰기
    (3, 101, 1, 1, 0, 0, 1),   -- park:   읽기+쓰기
    (4, 101, 1, 1, 0, 0, 1),   -- yoo:    읽기+쓰기
    (5, 101, 1, 1, 0, 0, 1),   -- jung:   읽기+쓰기
    (6, 101, 1, 1, 0, 0, 1),   -- jang:   읽기+쓰기
    (7, 101, 1, 1, 0, 0, 1),   -- lee:    읽기+쓰기
    (8, 101, 1, 1, 0, 0, 1),   -- choi:   읽기+쓰기
    (9, 101, 1, 1, 0, 0, 1);   -- energy: 읽기+쓰기

-- 3계층: 세부공장일보 컬럼관리 관리 페이지 접근 권한 (관리자만)
INSERT IGNORE INTO core_menu_permission (USER_ID, MENU_ID, CAN_READ, CAN_WRITE, CAN_DELETE, CAN_ADMIN, GRANTED_BY) VALUES
    (1, 102, 1, 1, 1, 1, 1);   -- admin: 접근권한 관리 페이지 전체 권한


-- ═══════════════════════════════════════════════
-- 3. ★★ daily_report_cell_auth — 셀 단위 접근 권한
--    (레거시 daily_report_cell_permission 대체)
--
--    관리자가 '세부공장일보 컬럼관리' 페이지에서 설정
--    각 사용자별로 담당 표의 셀 좌표(JSON 배열)와 입력 주기를 지정
--
--    ※ CELL_COORDS는 해당 사용자가 입력 담당하는 셀의 엑셀 좌표 목록
--    ※ 하나의 사용자가 같은 표에 여러 주기의 셀을 담당할 수 있음
--       → 현재는 (USER_ID, TABLE_CODE) UNIQUE이므로 주 주기(빈도 높은)를 FREQ_CODE에 기록
--       → 셀별 개별 주기는 daily_report_cell.FREQ_CODE에 저장됨
-- ═══════════════════════════════════════════════

-- ※ 운영 배포 초기: cell_auth 데이터 없음 (빈 상태)
-- ※ admin이 '세부공장일보 컬럼관리' 페이지에서 담당자를 직접 배정
-- ※ 배정 전까지 admin만 세부공장일보 접근 가능


-- ═══════════════════════════════════════════════
-- A. 일보 생성 (기존 데이터 유지)
-- ═══════════════════════════════════════════════
INSERT INTO daily_report (REPORT_DATE, TITLE, STATUS, CREATED_BY) VALUES
    ('2024-07-18', '2024-07-18 세부공장일보', 'CONFIRMED', 1),
    ('2024-07-19', '2024-07-19 세부공장일보', 'SUBMITTED', 1),
    ('2024-07-20', '2024-07-20 세부공장일보', 'DRAFT',     1);

SET @rpt_0718 := (SELECT REPORT_ID FROM daily_report WHERE REPORT_DATE = '2024-07-18');
SET @rpt_0719 := (SELECT REPORT_ID FROM daily_report WHERE REPORT_DATE = '2024-07-19');
SET @rpt_0720 := (SELECT REPORT_ID FROM daily_report WHERE REPORT_DATE = '2024-07-20');

-- ═══════════════════════════════════════════════
-- B. 7/20 일보 — 4개 표 메타 생성
-- ═══════════════════════════════════════════════
INSERT INTO daily_report_table (REPORT_ID, TABLE_CODE, TABLE_NAME, SORT_ORDER, ROW_COUNT, COL_COUNT) VALUES
    (@rpt_0720, 'TBL_PRODUCTION_INDEX', '주요 생산 지표 현황', 1, 10, 15),
    (@rpt_0720, 'TBL_INVENTORY', '제지 재공품 및 야적현황', 2, 10, 13),
    (@rpt_0720, 'TBL_ENERGY', '에너지 원단위', 3, 8, 6),
    (@rpt_0720, 'TBL_BOILER', '보일러 운영 현황', 4, 7, 8);

SET @tbl_1 := (SELECT TABLE_ID FROM daily_report_table WHERE REPORT_ID = @rpt_0720 AND TABLE_CODE = 'TBL_PRODUCTION_INDEX');
SET @tbl_2 := (SELECT TABLE_ID FROM daily_report_table WHERE REPORT_ID = @rpt_0720 AND TABLE_CODE = 'TBL_INVENTORY');
SET @tbl_3 := (SELECT TABLE_ID FROM daily_report_table WHERE REPORT_ID = @rpt_0720 AND TABLE_CODE = 'TBL_ENERGY');
SET @tbl_4 := (SELECT TABLE_ID FROM daily_report_table WHERE REPORT_ID = @rpt_0720 AND TABLE_CODE = 'TBL_BOILER');

-- ═══════════════════════════════════════════════
-- C. 주요 생산 지표 현황 (TBL_PRODUCTION_INDEX) — 셀 데이터 (기존 유지)
-- ═══════════════════════════════════════════════
INSERT INTO daily_report_cell (TABLE_ID, ROW_INDEX, COL_INDEX, EXCEL_COORD, CELL_VALUE, CELL_TYPE, CELL_LABEL, DATA_FORMAT, INPUT_CYCLE, FREQ_CODE, FREQ_LABEL, OWNER_IDS, OWNER_NAMES, IS_LOCKED, ROW_SPAN, COL_SPAN) VALUES
    (@tbl_1, 0, 0, 'B5', '생산지표', 'HEADER', '생산지표', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 3),
    (@tbl_1, 0, 3, 'E5', '최종', 'HEADER', '최종', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 0, 4, 'F5', '\'24년', 'HEADER', '\'24년', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 0, 5, 'G5', '\'25년', 'HEADER', '\'25년', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 0, 6, 'H5', '\'25년', 'HEADER', '\'25년', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 0, 7, 'I5', '\'26년', 'HEADER', '\'26년', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 7),
    (@tbl_1, 0, 14, 'P5', '비고 사항', 'HEADER', '비고 사항', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_1, 1, 3, 'E6', '목표', 'HEADER', '목표', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 1, 4, 'F6', '월평균', 'HEADER', '월평균', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 1, 5, 'G6', '월평균', 'HEADER', '월평균', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 1, 6, 'H6', '12월', 'HEADER', '12월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 1, 7, 'I6', '1월', 'HEADER', '1월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 1, 8, 'J6', '2월', 'HEADER', '2월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 1, 9, 'K6', '3월', 'HEADER', '3월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 1, 10, 'L6', '4월', 'HEADER', '4월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 1, 11, 'M6', '5월', 'HEADER', '5월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 1, 12, 'N6', '6월', 'HEADER', '6월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 1, 13, 'O6', '7월', 'HEADER', '7월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 2, 0, 'B7', '제지3 평균선속(m/분)', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 3),
    (@tbl_1, 2, 3, 'E7', '640', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 2, 4, 'F7', '583.5', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 2, 5, 'G7', '587', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 2, 6, 'H7', '588', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 2, 7, 'I7', '597', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 2, 8, 'J7', '584', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 2, 9, 'K7', '577', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 2, 10, 'L7', '597', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 2, 11, 'M7', '597', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 2, 12, 'N7', '594', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 2, 13, 'O7', 'DRS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 2, 14, 'P7', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 3, 0, 'B8', '초지5 생산량(톤/日)', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 3),
    (@tbl_1, 3, 3, 'E8', '85', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 3, 4, 'F8', '83.8', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 3, 5, 'G8', '76', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 3, 6, 'H8', '83.5', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 3, 7, 'I8', '80.4', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 3, 8, 'J8', '85.6', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 3, 9, 'K8', '79.9', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 3, 10, 'L8', '83.6', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 3, 11, 'M8', '83', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 3, 12, 'N8', '79.5', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 3, 13, 'O8', 'SAP', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 3, 14, 'P8', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 4, 0, 'B9', '수율(%)', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 3, 1),
    (@tbl_1, 4, 1, 'C9', 'PS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_1, 4, 2, 'D9', '완제품', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 4, 3, 'E9', '91', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 4, 4, 'F9', '97.7', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 4, 5, 'G9', '99.2', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 4, 6, 'H9', '98.6', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 4, 7, 'I9', '98.7', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 4, 8, 'J9', '97.2', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 4, 9, 'K9', '101.5', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 4, 10, 'L9', '101.8', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 4, 11, 'M9', '99.8', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 4, 12, 'N9', '98.7', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 4, 13, 'O9', '유동현 책임', 'DATA', NULL, NULL, 'DAILY', 'event', '발생 시', NULL, NULL, 0, 1, 1),
    (@tbl_1, 4, 14, 'P9', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 5, 2, 'D10', '코팅제외', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 5, 3, 'E10', '78', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 5, 4, 'F10', '83.8', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 5, 5, 'G10', '85.2', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 5, 6, 'H10', '84.1', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 5, 7, 'I10', '84.6', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 5, 8, 'J10', '83.5', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 5, 9, 'K10', '87.6', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 5, 10, 'L10', '88.2', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 5, 11, 'M10', '86.3', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 5, 12, 'N10', '84.7', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 5, 13, 'O10', '유동현 책임', 'DATA', NULL, NULL, 'DAILY', 'event', '발생 시', NULL, NULL, 0, 1, 1),
    (@tbl_1, 5, 14, 'P10', '- 완제품내 코팅 비율 14.0%', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 6, 1, 'C11', '화장지', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 2),
    (@tbl_1, 6, 3, 'E11', '63.5', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 6, 4, 'F11', '63.5', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 6, 5, 'G11', '64.6', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 6, 6, 'H11', '61.1', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 6, 7, 'I11', '63.3', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 6, 8, 'J11', '63.6', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 6, 9, 'K11', '63.6', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 6, 10, 'L11', '69.6', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 6, 11, 'M11', '74.6', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 6, 12, 'N11', '74.4', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 6, 13, 'O11', '유동현 책임', 'DATA', NULL, NULL, 'DAILY', 'event', '발생 시', NULL, NULL, 0, 1, 1),
    (@tbl_1, 6, 14, 'P11', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 7, 0, 'B12', '고지감량율(%)', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 3),
    (@tbl_1, 7, 3, 'E12', '-', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 7, 4, 'F12', '15.8', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 7, 5, 'G12', '14.8', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 7, 6, 'H12', '12.7', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 7, 7, 'I12', '11.2', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 7, 8, 'J12', '11.8', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 7, 9, 'K12', '13', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 7, 10, 'L12', '14.2', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 7, 11, 'M12', '15.9', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 7, 12, 'N12', '16', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 7, 13, 'O12', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 7, 14, 'P12', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 8, 0, 'B13', '슬러지원단위', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 2),
    (@tbl_1, 8, 2, 'D13', '제   지', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 8, 3, 'E13', '정상엽 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_1, 8, 4, 'F13', '89', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 8, 5, 'G13', '91', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 8, 6, 'H13', '94', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 8, 7, 'I13', '99', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 8, 8, 'J13', '104', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 8, 9, 'K13', '96', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 8, 10, 'L13', '84', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 8, 11, 'M13', '82', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 8, 12, 'N13', '84', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 8, 13, 'O13', '정상엽 책임', 'DATA', NULL, NULL, 'DAILY', 'event', '발생 시', NULL, NULL, 0, 1, 1),
    (@tbl_1, 8, 14, 'P13', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 9, 0, 'B14', '(Kg/톤)', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 2),
    (@tbl_1, 9, 2, 'D14', '화장지', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 9, 3, 'E14', '정상엽 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_1, 9, 4, 'F14', '76', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 9, 5, 'G14', '64', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 9, 6, 'H14', '81', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 9, 7, 'I14', '58', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 9, 8, 'J14', '68', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 9, 9, 'K14', '50', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 9, 10, 'L14', '46', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 9, 11, 'M14', '53', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 9, 12, 'N14', '62', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_1, 9, 13, 'O14', '정상엽 책임', 'DATA', NULL, NULL, 'DAILY', 'event', '발생 시', NULL, NULL, 0, 1, 1),
    (@tbl_1, 9, 14, 'P14', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1);

-- ═══════════════════════════════════════════════
-- D. 제지 재공품 및 야적현황 (TBL_INVENTORY) — 셀 데이터 (기존 유지)
-- ═══════════════════════════════════════════════
INSERT INTO daily_report_cell (TABLE_ID, ROW_INDEX, COL_INDEX, EXCEL_COORD, CELL_VALUE, CELL_TYPE, CELL_LABEL, DATA_FORMAT, INPUT_CYCLE, FREQ_CODE, FREQ_LABEL, OWNER_IDS, OWNER_NAMES, IS_LOCKED, ROW_SPAN, COL_SPAN) VALUES
    (@tbl_2, 0, 0, 'B19', '구 분', 'HEADER', '구 분', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 2),
    (@tbl_2, 0, 2, 'D19', '기준', 'HEADER', '기준', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_2, 0, 3, 'E19', '적정재고', 'HEADER', '적정재고', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_2, 0, 4, 'F19', '\'25년', 'HEADER', '\'25년', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 0, 5, 'G19', '\'26년', 'HEADER', '\'26년', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 7),
    (@tbl_2, 0, 12, 'N19', '비 고', 'HEADER', '비 고', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 1, 4, 'F20', '12월', 'HEADER', '12월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 1, 5, 'G20', '1월', 'HEADER', '1월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 1, 6, 'H20', '2월', 'HEADER', '2월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 1, 7, 'I20', '3월', 'HEADER', '3월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 1, 8, 'J20', '4월', 'HEADER', '4월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 1, 9, 'K20', '5월', 'HEADER', '5월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 1, 10, 'L20', '6월', 'HEADER', '6월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 1, 11, 'M20', '7월', 'HEADER', '7월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 1, 12, 'N20', NULL, 'HEADER', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 2, 0, 'B21', '제지 재공품', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 4, 1),
    (@tbl_2, 2, 1, 'C21', '밀롤창고', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 2, 2, 'D21', '톤', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 4, 1),
    (@tbl_2, 2, 3, 'E21', '김완중 팀장', 'DATA', NULL, NULL, 'DAILY', 'event', '발생 시', NULL, NULL, 0, 1, 1),
    (@tbl_2, 2, 4, 'F21', '3826', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 2, 5, 'G21', '3043', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 2, 6, 'H21', '3296', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 2, 7, 'I21', '2196', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 2, 8, 'J21', '3037', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 2, 9, 'K21', '3711', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 2, 10, 'L21', '3006', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 2, 11, 'M21', 'MES', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 2, 12, 'N21', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 3, 1, 'C22', '카타대기', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 3, 3, 'E22', '김완중 팀장', 'DATA', NULL, NULL, 'DAILY', 'event', '발생 시', NULL, NULL, 0, 1, 1),
    (@tbl_2, 3, 4, 'F22', '320', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 3, 5, 'G22', '315', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 3, 6, 'H22', '549', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 3, 7, 'I22', '648', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 3, 8, 'J22', '1360', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 3, 9, 'K22', '1121', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 3, 10, 'L22', '1110', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 3, 11, 'M22', 'MES', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 3, 12, 'N22', '- 제지 카타 동시 가동/운휴에 따른 재공 증가', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 4, 1, 'C23', '미포장', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 4, 3, 'E23', '김완중 팀장', 'DATA', NULL, NULL, 'DAILY', 'event', '발생 시', NULL, NULL, 0, 1, 1),
    (@tbl_2, 4, 4, 'F23', '212', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 4, 5, 'G23', '764', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 4, 6, 'H23', '702', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 4, 7, 'I23', '149', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 4, 8, 'J23', '86', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 4, 9, 'K23', '173', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 4, 10, 'L23', '266', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 4, 11, 'M23', 'MES', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 4, 12, 'N23', '- 시트 808톤, 원지 134톤(슬리터104톤,생산품30톤)', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 5, 1, 'C24', '포장후 물류입고전', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 5, 3, 'E24', '김완중 팀장', 'DATA', NULL, NULL, 'DAILY', 'event', '발생 시', NULL, NULL, 0, 1, 1),
    (@tbl_2, 5, 4, 'F24', '83', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 5, 5, 'G24', '139', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 5, 6, 'H24', '151', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 5, 7, 'I24', '88', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 5, 8, 'J24', '58', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 5, 9, 'K24', '288', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 5, 10, 'L24', '423', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 5, 11, 'M24', 'MES', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 5, 12, 'N24', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 6, 0, 'B25', '장기재고', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_2, 6, 1, 'C25', '3개월 초과', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 6, 2, 'D25', '톤', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 6, 3, 'E25', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 6, 4, 'F25', '4354', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 6, 5, 'G25', '4372', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 6, 6, 'H25', '4005', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 6, 7, 'I25', '4236', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 6, 8, 'J25', '3761', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 6, 9, 'K25', '3404', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 6, 10, 'L25', '3120', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 6, 11, 'M25', '장석환 선임/\n이도형 사원', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_2, 6, 12, 'N25', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 7, 1, 'C26', '6개월 초과', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 7, 2, 'D26', '톤', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 7, 3, 'E26', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 7, 4, 'F26', '917', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 7, 5, 'G26', '980', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 7, 6, 'H26', '786', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 7, 7, 'I26', '915', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 7, 8, 'J26', '957', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 7, 9, 'K26', '1543', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 7, 10, 'L26', '1130', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 7, 11, 'M26', '장석환 선임/\n이도형 사원', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_2, 7, 12, 'N26', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 8, 0, 'B27', '야적현황', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_2, 8, 1, 'C27', '제지', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 8, 2, 'D27', '톤', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 8, 3, 'E27', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 8, 4, 'F27', '489', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 8, 5, 'G27', '239', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 8, 6, 'H27', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 8, 7, 'I27', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 8, 8, 'J27', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 8, 9, 'K27', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 8, 10, 'L27', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 8, 11, 'M27', 'WMS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 8, 12, 'N27', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 9, 1, 'C28', '생활', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 9, 2, 'D28', '팔레트', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 9, 3, 'E28', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 9, 4, 'F28', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 9, 5, 'G28', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 9, 6, 'H28', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 9, 7, 'I28', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 9, 8, 'J28', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 9, 9, 'K28', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 9, 10, 'L28', '0', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 9, 11, 'M28', 'WMS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_2, 9, 12, 'N28', NULL, 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1);

-- ═══════════════════════════════════════════════
-- E. 에너지 원단위 (TBL_ENERGY) — 셀 데이터 (기존 유지)
-- ═══════════════════════════════════════════════
INSERT INTO daily_report_cell (TABLE_ID, ROW_INDEX, COL_INDEX, EXCEL_COORD, CELL_VALUE, CELL_TYPE, CELL_LABEL, DATA_FORMAT, INPUT_CYCLE, FREQ_CODE, FREQ_LABEL, OWNER_IDS, OWNER_NAMES, IS_LOCKED, ROW_SPAN, COL_SPAN) VALUES
    (@tbl_3, 0, 0, 'B34', '구분', 'HEADER', '구분', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 2),
    (@tbl_3, 0, 2, 'D34', '목표', 'HEADER', '목표', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_3, 0, 3, 'E34', '6월 실적', 'HEADER', '6월 실적', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_3, 0, 4, 'F34', '7월 현재', 'HEADER', '7월 현재', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 2),
    (@tbl_3, 1, 4, 'F35', '계 획', 'HEADER', '계 획', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 1, 5, 'G35', '실 적', 'HEADER', '실 적', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 2, 0, 'B36', '전력', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 3, 1),
    (@tbl_3, 2, 1, 'C36', '제   지', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 2, 2, 'D36', '최민우 사원', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_3, 2, 3, 'E36', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 2, 4, 'F36', '최민우 사원', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_3, 2, 5, 'G36', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 3, 1, 'C37', '화장지', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 3, 2, 'D37', '최민우 사원', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_3, 3, 3, 'E37', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 3, 4, 'F37', '최민우 사원', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_3, 3, 5, 'G37', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 4, 1, 'C38', '화)초지5', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 4, 2, 'D38', '최민우 사원', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_3, 4, 3, 'E38', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 4, 4, 'F38', '최민우 사원', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_3, 4, 5, 'G38', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 5, 0, 'B39', '연료', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 3, 1),
    (@tbl_3, 5, 1, 'C39', '제   지', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 5, 2, 'D39', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_3, 5, 3, 'E39', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 5, 4, 'F39', '박지권 책임', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_3, 5, 5, 'G39', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 6, 1, 'C40', '화장지', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 6, 2, 'D40', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_3, 6, 3, 'E40', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 6, 4, 'F40', '박지권 책임', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_3, 6, 5, 'G40', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 7, 1, 'C41', '화)초지5', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 7, 2, 'D41', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_3, 7, 3, 'E41', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_3, 7, 4, 'F41', '박지권 책임', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_3, 7, 5, 'G41', 'EIS', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1);

-- ═══════════════════════════════════════════════
-- F. 보일러 운영 현황 (TBL_BOILER) — 셀 데이터 (기존 유지)
-- ═══════════════════════════════════════════════
INSERT INTO daily_report_cell (TABLE_ID, ROW_INDEX, COL_INDEX, EXCEL_COORD, CELL_VALUE, CELL_TYPE, CELL_LABEL, DATA_FORMAT, INPUT_CYCLE, FREQ_CODE, FREQ_LABEL, OWNER_IDS, OWNER_NAMES, IS_LOCKED, ROW_SPAN, COL_SPAN) VALUES
    (@tbl_4, 0, 0, 'J34', '구분', 'HEADER', '구분', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_4, 0, 1, 'K34', '목표', 'HEADER', '목표', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_4, 0, 2, 'L34', '7월단가\n(천원/톤)', 'HEADER', '7월단가\n(천원/톤)', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_4, 0, 3, 'M34', '5월 실적', 'HEADER', '5월 실적', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_4, 0, 4, 'N34', '6월 실적', 'HEADER', '6월 실적', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_4, 0, 5, 'O34', '7월', 'HEADER', '7월', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 2),
    (@tbl_4, 0, 7, 'Q34', '비 고', 'HEADER', '비 고', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 2, 1),
    (@tbl_4, 1, 5, 'O35', '계 획', 'HEADER', '계 획', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 1, 6, 'P35', '실 적', 'HEADER', '실 적', NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 2, 0, 'J36', 'LNG보일러', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 2, 1, 'K36', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_4, 2, 2, 'L36', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_4, 2, 3, 'M36', '2.4', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 2, 4, 'N36', '0.4', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 2, 5, 'O36', '박지권 책임', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_4, 2, 6, 'P36', '환경에너지팀 반장', 'DATA', NULL, NULL, 'DAILY', 'daily', '매일', NULL, NULL, 0, 1, 1),
    (@tbl_4, 2, 7, 'Q36', '복합보일러 운휴  : \n  (1호기) 5/19~20, 4/28~29  (2호기) 5/6~6, 5/26~27\n - #2폐합성소각로 운휴 : \n   5/5~6, 5/19~20, 5/26~27\n\n - 5/10 유동상소각로 방출로 인한 손실금액 : 0.2백만원\n  ( 5월 누계 : 14.0 백만원) \n  ( 4월 누계 : 55.3 백만원)\n  ( 3월 누계 : 42.5 백만원)\n  ( 2월 누계 : 35.5 백만원)\n  ( 1월 누계 : 56.1 백만원)', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 5, 1),
    (@tbl_4, 3, 0, 'J37', '유동상소각로', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 3, 1, 'K37', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_4, 3, 2, 'L37', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_4, 3, 3, 'M37', '15.3', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 3, 4, 'N37', '14.7', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 3, 5, 'O37', '박지권 책임', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_4, 3, 6, 'P37', '환경에너지팀 반장', 'DATA', NULL, NULL, 'DAILY', 'daily', '매일', NULL, NULL, 0, 1, 1),
    (@tbl_4, 4, 0, 'J38', '복합보일러', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 4, 1, 'K38', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_4, 4, 2, 'L38', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_4, 4, 3, 'M38', '56.8', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 4, 4, 'N38', '52.5', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 4, 5, 'O38', '박지권 책임', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_4, 4, 6, 'P38', '환경에너지팀 반장', 'DATA', NULL, NULL, 'DAILY', 'daily', '매일', NULL, NULL, 0, 1, 1),
    (@tbl_4, 5, 0, 'J39', '폐합성소각로', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 5, 1, 'K39', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_4, 5, 2, 'L39', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_4, 5, 3, 'M39', '10.2', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 5, 4, 'N39', '11.6', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 5, 5, 'O39', '박지권 책임', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_4, 5, 6, 'P39', '환경에너지팀 반장', 'DATA', NULL, NULL, 'DAILY', 'daily', '매일', NULL, NULL, 0, 1, 1),
    (@tbl_4, 6, 0, 'J40', '합  계', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 6, 1, 'K40', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_4, 6, 2, 'L40', '박지권 책임', 'DATA', NULL, NULL, 'YEARLY', 'yearly', '매년', NULL, NULL, 0, 1, 1),
    (@tbl_4, 6, 3, 'M40', '84.7', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 6, 4, 'N40', '79.2', 'READONLY', NULL, NULL, 'NONE', NULL, NULL, NULL, NULL, 1, 1, 1),
    (@tbl_4, 6, 5, 'O40', '박지권 책임', 'DATA', NULL, NULL, 'MONTHLY', 'monthly', '매월', NULL, NULL, 0, 1, 1),
    (@tbl_4, 6, 6, 'P40', '환경에너지팀 반장', 'DATA', NULL, NULL, 'DAILY', 'daily', '매일', NULL, NULL, 0, 1, 1);

-- ═══════════════════════════════════════════════
-- G. 7/18, 7/19 일보 — 최소 표 구조 (기존 유지)
-- ═══════════════════════════════════════════════
INSERT INTO daily_report_table (REPORT_ID, TABLE_CODE, TABLE_NAME, SORT_ORDER, ROW_COUNT, COL_COUNT) VALUES
    (@rpt_0718, 'TBL_PRODUCTION_INDEX', '주요 생산 지표 현황', 1, 10, 15),
    (@rpt_0718, 'TBL_INVENTORY', '제지 재공품 및 야적현황', 2, 10, 13),
    (@rpt_0718, 'TBL_ENERGY', '에너지 원단위', 3, 8, 6),
    (@rpt_0718, 'TBL_BOILER', '보일러 운영 현황', 4, 7, 8);

INSERT INTO daily_report_table (REPORT_ID, TABLE_CODE, TABLE_NAME, SORT_ORDER, ROW_COUNT, COL_COUNT) VALUES
    (@rpt_0719, 'TBL_PRODUCTION_INDEX', '주요 생산 지표 현황', 1, 10, 15),
    (@rpt_0719, 'TBL_INVENTORY', '제지 재공품 및 야적현황', 2, 10, 13),
    (@rpt_0719, 'TBL_ENERGY', '에너지 원단위', 3, 8, 6),
    (@rpt_0719, 'TBL_BOILER', '보일러 운영 현황', 4, 7, 8);

-- ═══════════════════════════════════════════════
-- H. 특이사항 (7/20 일보) — 기존 유지
-- ═══════════════════════════════════════════════
INSERT INTO daily_report_remark (REPORT_ID, TABLE_CODE, CATEGORY, CONTENT, SORT_ORDER, CREATED_BY) VALUES
    (@rpt_0720, NULL, 'GENERAL', '공용 보기 화면 테스트 데이터입니다.', 1, 1);

-- ═══════════════════════════════════════════════
-- I. 이미지 첨부 (7/20 일보) — 기존 유지
-- ═══════════════════════════════════════════════
INSERT INTO daily_report_image (REPORT_ID, ORIGINAL_NAME, STORED_PATH, FILE_SIZE, CONTENT_TYPE, DESCRIPTION, TABLE_CODE, SORT_ORDER, UPLOADED_BY) VALUES
    (@rpt_0720, '보일러_운영현황.jpg', '/uploads/dailyreport/2024/07/20/boiler_status.jpg', 245760, 'image/jpeg', '보일러 운영 현황 사진', 'TBL_BOILER', 1, 3),
    (@rpt_0720, '에너지_원단위.png', '/uploads/dailyreport/2024/07/20/energy_report.png', 189440, 'image/png', '에너지 원단위 보고서 캡처', 'TBL_ENERGY', 2, 8);


SELECT '=== 02_seed_data.sql 실행 완료 ===' AS message;
SELECT '  ★ core_menu: 3건 (세부공장일보 카테고리 + 2 하위 페이지)' AS info;
SELECT '  ★ core_menu_permission: 10건 (입력 페이지 9건 + 접근권한 페이지 1건)' AS info;
SELECT '  ★ daily_report_cell_auth: 0건 (운영 초기 빈 상태 — admin이 접근권한 페이지에서 배정)' AS info;
