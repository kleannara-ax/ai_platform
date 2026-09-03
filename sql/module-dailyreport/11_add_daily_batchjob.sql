-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 11_add_daily_batchjob.sql
-- 설명: ★ 게시판(공장일보/세부공장일보) 재업로드 요청 테이블 신규 추가 (2026-08)
--
--       [배경]
--       공장일보/세부공장일보는 이 AI 플랫폼 화면에서 입력한 값을 기반으로
--       별도의 사내 게시판에도 게시된다. 매일 오전 8:05 이전 저장은 게시판
--       재업로드가 필요 없지만(게시판 배치가 이미 그 값을 반영해 게시하기
--       전이므로), 오전 8:05 이후에 값을 고치면 이미 게시된 게시글이 최신
--       값과 달라지므로 별도의 "게시판 재업로드 배치"가 그 게시글을 다시
--       고쳐 써야 한다.
--
--       이 테이블은 그 재업로드가 필요함을 다른 PC에서 동작하는 배치
--       시스템에 알리는 "요청 큐"이다. 배치 시스템이 이 테이블을 5초에
--       한 번씩 훑으며(이 세션과는 다른 PC/프로세스), 처리가 필요한 요청을
--       찾아 게시판을 갱신하고 CREATE_YN/RESULT_VALUE 등을 채워 넣는다.
--       이 세션(AI 플랫폼 웹앱)은 이 테이블에 요청 행을 INSERT하는 역할만
--       담당하며, 실제 게시판 갱신 로직은 이 세션의 책임이 아니다.
--
--       [BATCH_TYPE 값]
--       사용자가 오전 8:05 이후 "수정" 버튼을 누르면 아래 3가지 선택지 중
--       하나를 반드시 골라야 한다(라디오 버튼, 단일 선택):
--         '1' = 공장일보 재업로드   (특이사항 / 표2 제지 재공품 항목 수정)
--         '2' = 세부공장일보 재업로드 (표 1, 2, 3, 4 값 수정)
--         '3' = 모두 재업로드       (공장일보 + 세부공장일보 둘 다)
--       ※ '3'을 선택해도 별도로 행을 2개 나눠 만들지 않고 이 값 그대로
--         1건만 INSERT한다 — 배치 시스템이 BATCH_TYPE='3'을 "두 게시판 모두
--         재업로드가 필요하다"는 의미로 해석해 처리한다.
--
-- 실행 순서:
--   1) "0. 사전 점검" — 기존 테이블 존재 여부 확인
--   2) "1. CREATE TABLE" — daily_batchjob 테이블 생성
--   3) "2. 사후 검증" — 테이블/컬럼 생성 확인
--
-- 주의: 순수 신규 테이블 생성이며 기존 테이블에는 어떤 영향도 주지 않는다.
--       재실행해도 안전(idempotent) — CREATE TABLE IF NOT EXISTS 사용.
--       운영 DB 반영은 담당자가 직접 검토 후 수동으로 실행해야 함.
--
--       ★ 2026-09 수정: 이 파일은 더 이상 USE dailyreport_dev;로 대상 스키마를
--       강제 전환하지 않는다(자체 테스트용 개발 DB 이름이 하드코딩되어 있어,
--       운영 DB(예: platform_db)에 그대로 실행하면 세션이 dailyreport_dev로
--       전환되어 실패하거나 잘못된 스키마에 테이블이 생성되는 문제가 있었음).
--       대신 12_alter_batchjob_date_to_date_type.sql과 동일한 방식으로
--       DATABASE()(현재 mysql 접속 시 지정한 DB)를 그대로 사용한다.
--       → 실행 전, mysql 커맨드라인/클라이언트에서 대상 DB를 반드시 먼저
--       지정해야 한다:
--         자체 테스트: mysql -u factory_admin -p dailyreport_dev < 11_add_daily_batchjob.sql
--         운영/플랫폼: mysql -u {user} -p platform_db < 11_add_daily_batchjob.sql
-- ============================================================

-- ═══════════════════════════════════════════════
-- 0. 사전 점검 — 기존 테이블 존재 여부 확인
-- ═══════════════════════════════════════════════
SELECT '=== 0. 사전 점검: daily_batchjob 존재 여부 ===' AS section;

SELECT TABLE_NAME, TABLE_COMMENT
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_batchjob';


-- ═══════════════════════════════════════════════
-- 1. CREATE TABLE — daily_batchjob
-- ═══════════════════════════════════════════════
SELECT '=== 1. CREATE TABLE 실행: daily_batchjob ===' AS section;

CREATE TABLE IF NOT EXISTS daily_batchjob (
    SEQ_NO       BIGINT        NOT NULL AUTO_INCREMENT                COMMENT '순번',
    BATCH_DATE   DATE          NOT NULL                                COMMENT '일자 (일보 대상 날짜, daily_report.REPORT_DATE와 동일 규칙)',
    BATCH_TYPE   VARCHAR(1)    NOT NULL                                COMMENT '구분 (1:공장일보, 2:세부공장일보, 3:모두)',
    CREATE_YN    VARCHAR(1)    NOT NULL DEFAULT 'N'                    COMMENT '생성여부 (배치가 처리 완료 시 Y로 갱신)',
    CREATED_AT   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '요청일시',
    CREATED_BY   BIGINT        NOT NULL                                COMMENT '요청자 (core_user FK)',
    UPDATED_AT   DATETIME      NULL     ON UPDATE CURRENT_TIMESTAMP    COMMENT '수정일시 (배치가 처리 시 갱신)',
    UPDATED_BY   BIGINT        NULL                                    COMMENT '수정자 (배치 처리 주체 식별용, 필요 시 사용)',
    RESULT_VALUE VARCHAR(1)    NULL                                    COMMENT '성공여부 (배치가 처리 후 Y/N 등으로 기록)',
    REMARKS1     VARCHAR(100)  NULL                                    COMMENT '비고1',
    REMARKS2     VARCHAR(100)  NULL                                    COMMENT '비고2',
    REMARKS3     VARCHAR(100)  NULL                                    COMMENT '비고3',
    PRIMARY KEY (SEQ_NO),
    INDEX IDX_BATCHJOB_DATE (BATCH_DATE),
    INDEX IDX_BATCHJOB_CREATE_YN (CREATE_YN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='게시판(공장일보/세부공장일보) 재업로드 요청 큐 — 별도 PC 배치 시스템이 5초 주기로 폴링';


-- ═══════════════════════════════════════════════
-- 2. 사후 검증
-- ═══════════════════════════════════════════════
SELECT '=== 2. 사후 검증: daily_batchjob 생성 확인 ===' AS section;

SELECT TABLE_NAME, TABLE_COMMENT
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_batchjob';

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'daily_batchjob'
 ORDER BY ORDINAL_POSITION;

SELECT '=== 11_add_daily_batchjob.sql 실행 완료 ===' AS message;
