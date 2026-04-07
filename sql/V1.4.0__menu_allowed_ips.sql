-- ============================================================
-- V1.4.0 : 메뉴별 IP 접근 제한 기능
-- ALLOWED_IPS : 쉼표(,)로 구분된 허용 IP 목록
--               NULL이면 제한 없음 (모든 IP 허용)
-- ============================================================

ALTER TABLE CORE_MENU
  ADD COLUMN ALLOWED_IPS VARCHAR(1000) DEFAULT NULL
  COMMENT '허용 IP 목록 (쉼표 구분, NULL=제한없음)';
