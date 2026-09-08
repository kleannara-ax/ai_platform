-- ============================================================
--  10_step_photo_column.sql
--  사진이 "표의 어느 칸에 들어가는지"를 기록한다
--
--  실행: mysql -u platform_user --default-character-set=utf8mb4 platform_db < 10_step_photo_column.sql
--
--  배경:
--    지금까지 사진은 단계(행)에만 붙어 있어서, 매뉴얼마다 하나뿐인 "사진" 열에 전부 몰려 나왔다.
--    실제 엑셀에는 비고(개선사항) 칸에 글과 사진이 함께 들어 있는 시트가 있고,
--    화면에서 사진을 직접 올릴 때도 어느 칸에 넣을지 고를 수 있어야 한다.
--
--  하는 일:
--    safety_manual_step_photo 에 COLUMN_ID 를 추가한다.
--      NULL      = 예전처럼 매뉴얼의 '사진' 열에 표시 (기존 데이터는 전부 이 값)
--      값이 있음 = 그 열의 칸 안에 글과 함께 표시
--
--  주의:
--    외래키 제약은 걸지 않는다. 이 리포지토리의 다른 ALTER 마이그레이션과 같은 방식이며,
--    열이 지워져도 사진 자체는 남아 기본 사진 열로 돌아가게 하려는 의도다.
--    (SafetyPhotoService 가 조회 시 살아 있는 열인지 확인한다)
--
--  재실행 안전: ADD COLUMN IF NOT EXISTS / ADD INDEX IF NOT EXISTS 만 쓴다.
--               기존 행의 값을 바꾸지 않으므로 여러 번 실행해도 결과가 같다.
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE `safety_manual_step_photo`
    ADD COLUMN IF NOT EXISTS `COLUMN_ID` bigint(20) DEFAULT NULL
        COMMENT '사진이 들어갈 열 ID (NULL=매뉴얼의 기본 사진 열)' AFTER `STEP_ID`;

ALTER TABLE `safety_manual_step_photo`
    ADD INDEX IF NOT EXISTS `IDX_SAFETY_PHOTO_COLUMN` (`COLUMN_ID`);

SELECT '--- 적용 결과 ---' AS '';
SELECT COUNT(*) AS 전체사진수,
       SUM(COLUMN_ID IS NULL) AS 기본사진열,
       SUM(COLUMN_ID IS NOT NULL) AS 특정열지정
  FROM `safety_manual_step_photo`
 WHERE DELETED_YN = 'N';
