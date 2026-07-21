-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 00_create_database.sql
-- 설명: 자체 테스트용 DB 생성 + 전용 사용자 생성 + 권한 부여
-- 실행: root 계정으로 MariaDB 접속 후 실행
--       mysql -u root -p < sql/module-dailyreport/00_create_database.sql
--
-- ※ 플랫폼 통합 시에는 이 파일 실행 불필요 (기존 DB 사용)
-- ※ 자체 독립 테스트 환경 구축 시에만 실행
-- ============================================================

-- ────────────────────────────────────────────
-- 1. 데이터베이스 생성
-- ────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS dailyreport_dev
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

-- ────────────────────────────────────────────
-- 2. 전용 DB 사용자 생성 + 권한 부여
--    ※ 비밀번호는 테스트용입니다. 운영 환경에서는 반드시 변경하세요.
-- ────────────────────────────────────────────
CREATE USER IF NOT EXISTS 'dailyreport'@'localhost'
    IDENTIFIED BY 'dailyreport1234!';

CREATE USER IF NOT EXISTS 'dailyreport'@'%'
    IDENTIFIED BY 'dailyreport1234!';

GRANT ALL PRIVILEGES ON dailyreport_dev.* TO 'dailyreport'@'localhost';
GRANT ALL PRIVILEGES ON dailyreport_dev.* TO 'dailyreport'@'%';

FLUSH PRIVILEGES;

-- ────────────────────────────────────────────
-- 3. 확인
-- ────────────────────────────────────────────
SELECT '=== DB 생성 완료 ===' AS message;
SHOW DATABASES LIKE 'dailyreport_dev';
SELECT user, host FROM mysql.user WHERE user = 'dailyreport';

-- ────────────────────────────────────────────
-- 이후 실행 안내:
--   mysql -u dailyreport -p'dailyreport1234!' dailyreport_dev < sql/module-dailyreport/01_schema.sql
--   mysql -u dailyreport -p'dailyreport1234!' dailyreport_dev < sql/module-dailyreport/02_seed_data.sql
--   mysql -u dailyreport -p'dailyreport1234!' dailyreport_dev < sql/module-dailyreport/03_verify.sql
-- ────────────────────────────────────────────
