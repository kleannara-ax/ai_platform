-- ============================================================
--  PS 지분 검사 모듈 - 메뉴 구조 정리 마이그레이션
--  1) 기존: PS 커버리지 검사 → 변경: PS 지분 검사
--  2) PS_INSP_HISTORY 메뉴 제거
--  3) PS_INSP_PAGE 메뉴 제거 (하위 메뉴 전부 삭제)
--  4) PS_INSP_MGMT를 단독 메뉴로 변경 (URL 직접 할당)
--  이미 배포된 환경에서 실행 (03_menu_data.sql 이후)
-- ============================================================

-- 1. PS_INSP_HISTORY 메뉴 제거 (역할 매핑 → 메뉴 삭제)
DELETE FROM core_role_menu
WHERE menu_id IN (SELECT menu_id FROM core_menu WHERE menu_code = 'PS_INSP_HISTORY');

DELETE FROM core_menu
WHERE menu_code = 'PS_INSP_HISTORY';

-- 2. PS_INSP_PAGE 메뉴 제거 (역할 매핑 → 메뉴 삭제)
DELETE FROM core_role_menu
WHERE menu_id IN (SELECT menu_id FROM core_menu WHERE menu_code = 'PS_INSP_PAGE');

DELETE FROM core_menu
WHERE menu_code = 'PS_INSP_PAGE';

-- 3. PS_INSP_MGMT를 단독 메뉴로 변경 (URL 할당, 명칭 변경)
UPDATE core_menu
SET menu_name   = 'PS 지분 검사',
    menu_url    = '/ps-insp-api/page',
    description = 'PS 지분 검사 (점보롤 지분 검사)',
    updated_at  = NOW()
WHERE menu_code = 'PS_INSP_MGMT';
