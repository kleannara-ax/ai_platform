-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 04_migrate_owner_ids_to_cell_auth.sql
-- 설명: ★ 하드코딩 제거 마이그레이션
--       DefaultCellTemplate.java에 자바 문자열로 박혀 있던 담당자
--       (jung/yoo/kim/park/choi/jang/lee/energy)가 daily_report_cell.OWNER_IDS/
--       OWNER_NAMES 컬럼에 저장되어 있던 것을, 관리자가 '컬럼관리' 화면에서
--       배정하는 daily_report_cell_auth 테이블로 1회성 이전한다.
--
--       ※ 이 스크립트는 애플리케이션 코드가 아니라 "기존에 이미 살아있던
--         운영 데이터"를 그대로 새 테이블로 옮기는 데이터 마이그레이션이다.
--         하드코딩된 이름을 다시 SQL에 적어 넣는 것이 아니라, 현재 DB에
--         실제로 들어있는 OWNER_IDS 값을 파싱해서 이전하므로 운영 DB가
--         개발 시드와 다르더라도(자체 구축 DB) 그대로 동작한다.
--
-- 실행 전제:
--   - daily_report_cell.OWNER_IDS에 남아있는 로그인ID가 core_user.LOGIN_ID에
--     실제로 존재해야 한다 (없으면 해당 담당자는 이전되지 않고 verify 단계에서
--     "미매칭 owner_token"으로 보고됨 → 운영 담당자 확인 후 재실행 필요).
--   - MariaDB 10.5+ (재귀 CTE, JSON_ARRAYAGG 지원 필요) — 본 프로젝트는 10.11 사용.
--
-- 실행 순서:
--   1) (선택) 아래 "0. 사전 점검" 섹션만 먼저 실행해 매칭 여부 확인
--   2) "1. 마이그레이션 INSERT" 실행 (daily_report_cell_auth로 이전)
--   3) 애플리케이션에서 관리자 계정으로
--        POST /dailyreport-api/cell-auths/resync-all
--      호출 → CellOwnershipSyncService가 daily_report_cell.OWNER_IDS/OWNER_NAMES를
--      새로 등록된 cell_auth 기준으로 재계산 (기존 하드코딩 값을 대체)
--   4) "2. 사후 검증" 섹션으로 결과 확인
--
-- 주의: 운영 반영 전 반드시 dev/staging DB에서 먼저 실행 후 검증할 것.
-- ============================================================


-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — OWNER_IDS 토큰이 core_user.LOGIN_ID와 매칭되는지 확인
--    (공백 구분 다중 담당자, 예: "jang lee" → "jang", "lee")
-- ═══════════════════════════════════════════════
SELECT '=== 0. 사전 점검: OWNER_IDS 토큰 매칭 확인 ===' AS section;

WITH RECURSIVE owner_split AS (
    SELECT
        CELL_ID,
        TABLE_ID,
        TRIM(SUBSTRING_INDEX(OWNER_IDS, ' ', 1))                                   AS owner_token,
        IF(LOCATE(' ', OWNER_IDS) > 0,
           TRIM(SUBSTRING(OWNER_IDS, LOCATE(' ', OWNER_IDS) + 1)),
           '')                                                                     AS remainder
    FROM daily_report_cell
    WHERE OWNER_IDS IS NOT NULL AND TRIM(OWNER_IDS) <> ''
    UNION ALL
    SELECT
        CELL_ID,
        TABLE_ID,
        TRIM(SUBSTRING_INDEX(remainder, ' ', 1))                                   AS owner_token,
        IF(LOCATE(' ', remainder) > 0,
           TRIM(SUBSTRING(remainder, LOCATE(' ', remainder) + 1)),
           '')                                                                     AS remainder
    FROM owner_split
    WHERE remainder <> ''
)
SELECT os.owner_token,
       COUNT(*)                              AS cell_count,
       CASE WHEN u.USER_ID IS NULL THEN '❌ core_user에 없음 (미매칭)'
            ELSE CONCAT('✅ user_id=', u.USER_ID, ' (', u.USER_NAME, ')')
       END                                    AS match_status
FROM owner_split os
LEFT JOIN core_user u ON u.LOGIN_ID = os.owner_token
WHERE os.owner_token <> ''
GROUP BY os.owner_token, u.USER_ID, u.USER_NAME
ORDER BY match_status DESC, os.owner_token;

-- ↑ 위 결과에서 '❌ 미매칭'이 있으면, 아래 INSERT 실행 전에
--   core_user에 해당 LOGIN_ID를 추가하거나 담당자를 재확인해야 한다.
--   (미매칭 토큰은 자동으로 건너뛰어지며 에러 없이 진행됨 — INNER JOIN 사용)


-- ═══════════════════════════════════════════════
-- 1. 마이그레이션 INSERT — daily_report_cell_auth로 이전
--    (USER_ID, TABLE_CODE) 단위로 묶어서 좌표 목록을 JSON 배열로 저장
--    이미 존재하는 (USER_ID, TABLE_CODE) 조합은 건너뛴다 (INSERT IGNORE +
--    UNIQUE KEY UK_CELL_AUTH_USER_TABLE 활용 — 재실행해도 안전).
-- ═══════════════════════════════════════════════
SELECT '=== 1. 마이그레이션 INSERT 실행 ===' AS section;

-- ★ MariaDB는 "WITH RECURSIVE ... INSERT INTO ... SELECT ..." 순서를 허용하지 않는다.
--   CTE 체인은 반드시 SELECT 바로 앞에 와야 하므로, INSERT INTO (컬럼목록)을
--   WITH RECURSIVE보다 먼저 써야 한다 (INSERT INTO tbl (...) WITH RECURSIVE ... SELECT ...).
INSERT IGNORE INTO daily_report_cell_auth
    (USER_ID, TABLE_CODE, CELL_COORDS, FREQ_CODE, FREQ_LABEL, IS_ACTIVE, GRANTED_BY, DESCRIPTION)
WITH RECURSIVE owner_split AS (
    SELECT
        c.CELL_ID,
        c.TABLE_ID,
        c.EXCEL_COORD,
        c.FREQ_CODE,
        c.FREQ_LABEL,
        TRIM(SUBSTRING_INDEX(c.OWNER_IDS, ' ', 1))                                 AS owner_token,
        IF(LOCATE(' ', c.OWNER_IDS) > 0,
           TRIM(SUBSTRING(c.OWNER_IDS, LOCATE(' ', c.OWNER_IDS) + 1)),
           '')                                                                     AS remainder
    FROM daily_report_cell c
    WHERE c.OWNER_IDS IS NOT NULL AND TRIM(c.OWNER_IDS) <> ''
    UNION ALL
    SELECT
        os.CELL_ID,
        os.TABLE_ID,
        os.EXCEL_COORD,
        os.FREQ_CODE,
        os.FREQ_LABEL,
        TRIM(SUBSTRING_INDEX(os.remainder, ' ', 1))                                AS owner_token,
        IF(LOCATE(' ', os.remainder) > 0,
           TRIM(SUBSTRING(os.remainder, LOCATE(' ', os.remainder) + 1)),
           '')                                                                     AS remainder
    FROM owner_split os
    WHERE os.remainder <> ''
),
resolved AS (
    SELECT
        u.USER_ID,
        t.TABLE_CODE,
        os.EXCEL_COORD,
        os.FREQ_CODE,
        os.FREQ_LABEL
    FROM owner_split os
    JOIN daily_report_table t ON t.TABLE_ID = os.TABLE_ID
    JOIN core_user u ON u.LOGIN_ID = os.owner_token          -- 미매칭 토큰은 자동 제외
    WHERE os.owner_token <> ''
),
-- 같은 (USER_ID, TABLE_CODE)에 여러 FREQ_CODE가 섞여 있으면
-- 대표값으로 가장 빈도 높은 FREQ_CODE 하나를 선택 (실제 편집 가능 여부 판단은
-- CellOwnershipSyncService가 채우는 daily_report_cell.FREQ_CODE 기준이라
-- 이 대표값은 표시/설명용일 뿐 정확도에 영향 없음).
freq_rank AS (
    SELECT USER_ID, TABLE_CODE, FREQ_CODE, FREQ_LABEL,
           COUNT(*) AS freq_count,
           ROW_NUMBER() OVER (
               PARTITION BY USER_ID, TABLE_CODE
               ORDER BY COUNT(*) DESC, FREQ_CODE ASC
           ) AS rn
    FROM resolved
    GROUP BY USER_ID, TABLE_CODE, FREQ_CODE, FREQ_LABEL
)
SELECT
    r.USER_ID,
    r.TABLE_CODE,
    JSON_ARRAYAGG(DISTINCT r.EXCEL_COORD),
    fr.FREQ_CODE,
    fr.FREQ_LABEL,
    1,
    NULL,
    '★ 마이그레이션: 기존 DefaultCellTemplate 하드코딩 담당자 이전 (04_migrate_owner_ids_to_cell_auth.sql)'
FROM resolved r
JOIN freq_rank fr
     ON fr.USER_ID = r.USER_ID AND fr.TABLE_CODE = r.TABLE_CODE AND fr.rn = 1
GROUP BY r.USER_ID, r.TABLE_CODE, fr.FREQ_CODE, fr.FREQ_LABEL;


-- ═══════════════════════════════════════════════
-- 2. 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== 2. 마이그레이션 결과 확인 ===' AS section;

SELECT ca.AUTH_ID, u.LOGIN_ID, u.USER_NAME,
       ca.TABLE_CODE, ca.CELL_COORDS,
       ca.FREQ_CODE, ca.FREQ_LABEL, ca.IS_ACTIVE,
       JSON_LENGTH(ca.CELL_COORDS) AS coord_count
  FROM daily_report_cell_auth ca
  JOIN core_user u ON u.USER_ID = ca.USER_ID
 WHERE ca.DESCRIPTION LIKE '★ 마이그레이션%'
 ORDER BY ca.TABLE_CODE, u.LOGIN_ID;

-- 다음 단계 안내
SELECT '=== 다음 단계 ===' AS section;
SELECT '위 결과를 확인한 뒤, 애플리케이션에 관리자로 로그인하여' AS step1,
       'POST /dailyreport-api/cell-auths/resync-all 을 1회 호출하세요.' AS step2,
       '호출 즉시 daily_report_cell.OWNER_IDS/OWNER_NAMES가 방금 이전한' AS step3,
       'cell_auth 데이터 기준으로 재계산되어 하드코딩 값을 대체합니다.' AS step4;
