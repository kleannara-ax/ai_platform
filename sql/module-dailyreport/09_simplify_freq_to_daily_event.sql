-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 09_simplify_freq_to_daily_event.sql
-- 설명: ★ 입력 주기(FREQ_CODE) 단순화 (2026-08) — monthly(매월)/yearly(매년)
--       두 값을 완전히 폐기하고, daily(매일)/event(발생 시) 두 가지만 남긴다.
--
--       [배경]
--       기존에는 매월 1일에만 입력 가능한 monthly, 매년 1월 1일에만 입력
--       가능한 yearly 주기가 있었으나, daily/event는 이미 원래부터 "항상
--       활성화"로 동일하게 동작했다. 운영상 실익이 없고 오히려 "그 날이
--       아니면 입력 자체가 막힌다"는 혼란만 주므로, monthly/yearly를 완전히
--       없애고 daily/event 두 가지로 단순화한다. 이제부터는 컬럼관리
--       대시보드(cell-auth-admin.html)에서도 주기 선택 옵션이 daily/event
--       두 개뿐이며, 실제 활성화 로직(CellService.canEditByFrequency)도
--       주기 값과 무관하게(freqCode가 null이 아니면) 항상 활성화로 동작하도록
--       이미 앱 코드가 변경되었다 (별도 배포).
--
--       [수정 내용]
--       1) DB: daily_report_cell_auth.FREQ_CODE/FREQ_LABEL 중 monthly/yearly인
--          행을 daily/매일로 일괄 변경 — 앱의 실제 권한 판단(단일 소스)이
--          이 테이블을 참조하므로 가장 중요한 대상이다.
--       2) DB: daily_report_cell.FREQ_CODE/FREQ_LABEL 중 monthly/yearly인
--          행도 daily/매일로 일괄 변경 — 이 값은 표시용 캐시일 뿐 실제 권한
--          판단에는 쓰이지 않지만(CellService.isCellEditableForUser는 항상
--          CellAuth를 단일 소스로 판단), 관리자 화면 등에서 혼란을 주지 않도록
--          함께 정리한다.
--       3) App(Java, 별도 배포): DefaultCellTemplate.java의 신규 셀 생성 시
--          monthly/yearly 하드코딩을 daily로 변경, CellService.canEditByFrequency
--          단순화, CellAuthService에 daily/event 외 값 등록 차단 검증 추가,
--          cell-auth-admin.html 주기 선택 <select>에서 monthly/yearly
--          <option> 제거.
--
-- 실행 순서:
--   1) "0. 사전 점검" — 현재 monthly/yearly로 등록된 행 개수 확인
--   2) "1. UPDATE 실행" — daily_report_cell_auth, daily_report_cell 순으로 일괄 변경
--   3) "2. 사후 검증" — monthly/yearly가 완전히 사라졌는지 재확인
--
-- 주의: 이 스크립트는 앱 코드 배포와 반드시 함께(또는 배포 직후) 실행해야
--       한다 — 앱이 이미 daily/event만 허용하도록 배포된 상태에서, DB에
--       monthly/yearly 잔여 데이터가 있어도 CellService.canEditByFrequency가
--       "freqCode가 null이 아니면 항상 활성화"로 방어적으로 동작하므로 즉시
--       기능 장애가 발생하지는 않지만, 관리자 화면에 옛 라벨("매월"/"매년")이
--       계속 보이는 등 데이터 정합성 문제가 남으므로 가능한 빨리 실행할 것.
--       운영 DB 반영은 담당자가 직접 검토 후 수동으로 실행해야 함
--       (이 스크립트는 sandbox/dev DB 적용을 우선 검증하는 목적으로 작성됨).
--       재실행해도 안전(idempotent) — 이미 daily로 바뀐 행은 WHERE 조건에
--       걸리지 않으므로 두 번 실행해도 결과가 달라지지 않는다.
-- ============================================================


-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — 현재 monthly/yearly로 등록된 행 개수 확인
-- ═══════════════════════════════════════════════
SELECT '=== 0-1. 사전 점검: daily_report_cell_auth FREQ_CODE 분포 ===' AS section;
SELECT FREQ_CODE, COUNT(*) AS cnt
  FROM daily_report_cell_auth
 GROUP BY FREQ_CODE;

SELECT '=== 0-2. 사전 점검: daily_report_cell FREQ_CODE 분포 ===' AS section;
SELECT FREQ_CODE, COUNT(*) AS cnt
  FROM daily_report_cell
 GROUP BY FREQ_CODE;


-- ═══════════════════════════════════════════════
-- 1. UPDATE 실행 — monthly/yearly → daily/매일 일괄 변경
-- ═══════════════════════════════════════════════
SELECT '=== 1-1. UPDATE 실행: daily_report_cell_auth ===' AS section;

UPDATE daily_report_cell_auth
   SET FREQ_CODE = 'daily',
       FREQ_LABEL = '매일'
 WHERE FREQ_CODE IN ('monthly', 'yearly');

SELECT '=== 1-2. UPDATE 실행: daily_report_cell (표시용 캐시) ===' AS section;

UPDATE daily_report_cell
   SET FREQ_CODE = 'daily',
       FREQ_LABEL = '매일'
 WHERE FREQ_CODE IN ('monthly', 'yearly');


-- ═══════════════════════════════════════════════
-- 2. 사후 검증 — monthly/yearly가 완전히 사라졌는지 확인 (0건이어야 정상)
-- ═══════════════════════════════════════════════
SELECT '=== 2-1. 사후 검증: daily_report_cell_auth FREQ_CODE 분포 ===' AS section;
SELECT FREQ_CODE, COUNT(*) AS cnt
  FROM daily_report_cell_auth
 GROUP BY FREQ_CODE;

SELECT '=== 2-2. 사후 검증: daily_report_cell FREQ_CODE 분포 ===' AS section;
SELECT FREQ_CODE, COUNT(*) AS cnt
  FROM daily_report_cell
 GROUP BY FREQ_CODE;

-- 다음 단계 안내
SELECT '=== 다음 단계 ===' AS section;
SELECT '위 2번 결과에 monthly/yearly가 없으면(daily/event만 남으면) 성공.' AS step1,
       '앱(app.jar)이 이미 daily/event만 허용하도록 배포되어 있는지 확인할 것.' AS step2,
       '컬럼관리 대시보드에서 주기 선택 옵션이 매일/발생 시 두 개뿐인지 화면으로 확인할 것.' AS step3;
