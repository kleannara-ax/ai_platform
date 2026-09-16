-- ============================================================
--  11_column_width_defaults.sql
--  표 열 기본 폭 조정 — 비고는 좁게, 위험요인/작업 내용은 넓게
--
--  실행: mysql -u platform_user --default-character-set=utf8mb4 platform_db < 11_column_width_defaults.sql
--
--  배경:
--    엑셀에서 읽어 만든 글 칸은 이름과 상관없이 폭이 모두 같았다(260).
--    비고에는 한두 줄짜리 메모가, 위험요인/작업 내용에는 여러 문장이 들어가는데
--    폭이 같다 보니 위험요인 칸만 잘게 접혀 읽기 어려웠다. 종이로 뽑으면 더 두드러진다.
--
--  하는 일:
--    아직 기본값(260)인 글 칸만 바꾼다. 합계는 그대로라 나머지 칸 폭은 변하지 않는다.
--      비고     260 -> 190
--      위험요인 260 -> 330
--
--  주의:
--    관리자가 화면에서 폭을 손댄 칸(260 이 아닌 값)은 건드리지 않는다.
--    사용자가 머리글을 끌어 조절한 폭은 그 브라우저에만 저장되므로 이 SQL 과 무관하다.
--    ("열 너비 초기화" 를 누르면 여기서 정한 기본값으로 돌아온다)
--    되돌리려면 같은 조건으로 190 -> 260, 330 -> 260 으로 UPDATE 하면 된다.
-- ============================================================

UPDATE SAFETY_MANUAL_COLUMN
   SET WIDTH_WEIGHT = 190,
       UPDATED_AT   = NOW(),
       UPDATED_BY   = 'migration-11'
 WHERE DELETED_YN   = 'N'
   AND COLUMN_TYPE  = 'TEXT'
   AND WIDTH_WEIGHT = 260
   AND LABEL LIKE '%비고%';

UPDATE SAFETY_MANUAL_COLUMN
   SET WIDTH_WEIGHT = 330,
       UPDATED_AT   = NOW(),
       UPDATED_BY   = 'migration-11'
 WHERE DELETED_YN   = 'N'
   AND COLUMN_TYPE  = 'TEXT'
   AND WIDTH_WEIGHT = 260
   AND LABEL LIKE '%위험요인%';

-- 적용 결과 확인
SELECT LABEL, WIDTH_WEIGHT, COUNT(*) AS 열수
  FROM SAFETY_MANUAL_COLUMN
 WHERE DELETED_YN = 'N'
   AND (LABEL LIKE '%비고%' OR LABEL LIKE '%위험요인%')
 GROUP BY LABEL, WIDTH_WEIGHT
 ORDER BY 열수 DESC;
