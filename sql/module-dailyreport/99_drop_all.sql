-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 99_drop_all.sql
-- 설명: 전체 리셋 — 모든 테이블 삭제 (역순)
--       AI 플랫폼 통합형 (Phase 4) — 9개 테이블
-- 주의: 운영 환경에서는 절대 사용 금지!
--       플랫폼 코어 테이블(core_*)은 IF EXISTS로 안전하게 삭제
-- ============================================================

USE dailyreport_dev;

SET FOREIGN_KEY_CHECKS = 0;

-- ★ 모듈 전용 테이블 (역순 삭제)
DROP TABLE IF EXISTS daily_report_image;
DROP TABLE IF EXISTS daily_report_remark;
DROP TABLE IF EXISTS daily_report_cell_auth;       -- ★ 신규 (Phase 4)
DROP TABLE IF EXISTS daily_report_cell;
DROP TABLE IF EXISTS daily_report_table;
DROP TABLE IF EXISTS daily_report;

-- ★ 플랫폼 코어 스텁 테이블 (역순 삭제)
DROP TABLE IF EXISTS core_menu_permission;          -- ★ 신규 (Phase 4)
DROP TABLE IF EXISTS core_menu;                     -- ★ 신규 (Phase 4)
DROP TABLE IF EXISTS core_user;

-- ★ 레거시 테이블 (이전 버전 호환)
DROP TABLE IF EXISTS daily_report_cell_permission;  -- 레거시 (Phase 3 이전)

SET FOREIGN_KEY_CHECKS = 1;

SELECT '=== 99_drop_all.sql 실행 완료: 모든 테이블 삭제됨 (10개) ===' AS message;
