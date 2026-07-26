-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 06_allow_multi_freq_cell_auth.sql
-- 설명: ★ 한 사용자가 같은 표(TABLE_CODE)에서 서로 다른 주기(FREQ_CODE)의
--       셀들을 나눠서 담당할 수 있도록, daily_report_cell_auth 테이블의
--       UNIQUE KEY UK_CELL_AUTH_USER_TABLE (USER_ID, TABLE_CODE) 제약을 제거한다.
--
--       [버그 배경]
--       기존에는 (USER_ID, TABLE_CODE) 조합에 UNIQUE 제약이 걸려 있어, 한 사용자는
--       하나의 표에 대해 CellAuth 레코드를 단 1건만 가질 수 있었다. 즉 그 표 안의
--       모든 담당 셀이 반드시 동일한 FREQ_CODE(주기)를 가져야만 했다.
--       하지만 실제로는 "같은 표 안에서 일부 셀은 매일 입력, 다른 셀은 매년 입력"
--       처럼 한 사람이 서로 다른 주기의 셀 그룹을 동시에 담당하는 경우가 존재하여,
--       관리자가 같은 사용자에게 같은 표의 다른 셀 + 다른 주기로 권한을 추가
--       등록하려 하면 "이미 권한이 존재합니다" 오류로 저장이 실패했다.
--       (CellOwnershipSyncService.java 클래스 Javadoc에 이미 이 한계가 명시되어 있었음)
--
--       [수정 내용]
--       1) DB: UK_CELL_AUTH_USER_TABLE 유니크 인덱스 제거 → 한 사용자가 같은 표에
--          여러 CellAuth 행(좌표 그룹별로 서로 다른 FREQ_CODE)을 가질 수 있도록 허용.
--       2) App(Java, 별도 배포): CellAuth.java 엔티티의 @UniqueConstraint 제거,
--          CellAuthService.createAuth()/updateAuth()의 "이미 권한이 존재합니다"
--          무조건 차단 로직을 "좌표가 겹치는 경우에만 차단"으로 완화,
--          CellService.java의 단일 CellAuth 조회를 List<CellAuth> 조회로 변경하여
--          여러 주기 그룹 중 해당 좌표를 커버하는 항목의 FREQ_CODE로 편집 가능
--          여부를 판단하도록 수정.
--
-- 실행 순서:
--   1) "0. 사전 점검" — 현재 제약 존재 여부 및 (USER_ID, TABLE_CODE) 중복 후보 확인
--   2) "1. ALTER 실행" — UNIQUE 인덱스 제거
--   3) "2. 사후 검증" — 인덱스가 제거되었는지 재확인
--
-- 주의: 운영 반영 전 반드시 dev/staging DB에서 먼저 실행 후 검증할 것.
--       (★ 운영 DB 반영은 담당자가 직접 검토 후 수동으로 실행해야 함 — 이 스크립트는
--        샌드박스/개발 DB 적용 목적으로 우선 사용)
--       재실행해도 안전(idempotent) — 인덱스가 이미 없으면 DROP 시 오류가 나므로
--       0번 사전 점검에서 존재 여부를 먼저 확인하고 실행할 것.
-- ============================================================


-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — 현재 UNIQUE 인덱스 존재 여부 확인
-- ═══════════════════════════════════════════════
SELECT '=== 0. 사전 점검: UK_CELL_AUTH_USER_TABLE 인덱스 존재 여부 ===' AS section;

SELECT INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX, NON_UNIQUE
  FROM INFORMATION_SCHEMA.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_report_cell_auth'
   AND INDEX_NAME = 'UK_CELL_AUTH_USER_TABLE'
 ORDER BY SEQ_IN_INDEX;

-- 참고: 제약이 있었다면 이미 (USER_ID, TABLE_CODE) 조합은 항상 1건만 존재했을 것이므로
-- 아래 쿼리는 통상 0건이 나온다 (참고용).
SELECT '=== 참고: 현재 (USER_ID, TABLE_CODE) 중복 건수 확인 ===' AS section;
SELECT USER_ID, TABLE_CODE, COUNT(*) AS cnt
  FROM daily_report_cell_auth
 GROUP BY USER_ID, TABLE_CODE
HAVING COUNT(*) > 1;


-- ═══════════════════════════════════════════════
-- 1. ALTER 실행 — UNIQUE 인덱스 제거
-- ═══════════════════════════════════════════════
SELECT '=== 1. ALTER 실행: UK_CELL_AUTH_USER_TABLE 인덱스 제거 ===' AS section;

ALTER TABLE daily_report_cell_auth
  DROP INDEX UK_CELL_AUTH_USER_TABLE;


-- ═══════════════════════════════════════════════
-- 2. 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== 2. 사후 검증: 인덱스 제거 확인 (0건이어야 정상) ===' AS section;

SELECT INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX, NON_UNIQUE
  FROM INFORMATION_SCHEMA.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_report_cell_auth'
   AND INDEX_NAME = 'UK_CELL_AUTH_USER_TABLE';

SELECT '=== 남아있는 인덱스 목록 (참고) ===' AS section;
SELECT INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX, NON_UNIQUE
  FROM INFORMATION_SCHEMA.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_report_cell_auth'
 ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 다음 단계 안내
SELECT '=== 다음 단계 ===' AS section;
SELECT '위 2번 결과가 0건이면 UNIQUE 제약 제거 성공.' AS step1,
       '이제 애플리케이션(app.jar)을 재빌드/재기동한 뒤,' AS step2,
       '같은 사용자+같은 표에 대해 서로 다른 셀+주기로 CellAuth를' AS step3,
       '2건 이상 등록해도 저장이 성공하는지 API로 확인할 것.' AS step4;
