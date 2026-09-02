-- ============================================================
--  08_flexible_columns.sql
--  안전작업 매뉴얼 — 서식 구분 + 사용자 정의 표 열(체크 열 포함)
--
--  실행: mysql -u platform_user --default-character-set=utf8mb4 platform_db < 08_flexible_columns.sql
--
--  배경:
--    기존에는 상세 표의 열이 safety_manual_step 의 고정 컬럼
--    (DESCRIPTION/HAZARD/SAFETY_EQUIPMENT/REMARK)으로 박혀 있었다.
--    "작업 위험성 평가서" 서식이 추가되면서 열 구성이 서식마다 달라지고
--    (작업 순서 / 발생 가능한 위험 / √ / 위험성 평가 대책 / √),
--    체크 열이 필요해졌다. 그래서 열 정의를 데이터로 뺀다.
--
--  구조:
--    safety_manual.FORM_TYPE      서식 유형
--    safety_manual_meta           매뉴얼 머리말 항목 (부서명/작업인원/목적 등 — 서식마다 다름)
--    safety_manual_column         표의 열 정의 (이름/유형/순서/폭) — 매뉴얼마다 자유롭게
--    safety_manual_step_value     행 x 열 교차 값 (텍스트 또는 체크)
--
--  기존 고정 컬럼은 ALTER DROP COLUMN 을 쓰지 않는다(운영 안전 규칙).
--  아래 4)에서 값만 새 구조로 복사하고, 이후 애플리케이션은 새 구조만 읽고 쓴다.
--
--  재실행 안전: ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS /
--               백필은 이미 열이 만들어진 매뉴얼을 건너뛴다.
-- ============================================================

SET NAMES utf8mb4;

-- ────────────────────────────────────────────
-- 1) 서식 유형
-- ────────────────────────────────────────────
ALTER TABLE safety_manual
    ADD COLUMN IF NOT EXISTS FORM_TYPE varchar(30) NOT NULL DEFAULT 'WORK_METHOD'
        COMMENT '서식 유형(WORK_METHOD=안전작업 매뉴얼, RISK_ASSESSMENT=작업 위험성 평가서)' AFTER TITLE;

ALTER TABLE safety_manual
    ADD INDEX IF NOT EXISTS IDX_SAFETY_MANUAL_FORM_TYPE (FORM_TYPE);

-- ────────────────────────────────────────────
-- 2) 매뉴얼 머리말 항목 (라벨-값)
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `safety_manual_meta` (
  `META_ID`    bigint(20)   NOT NULL AUTO_INCREMENT              COMMENT '머리말 항목 ID (PK)',
  `MANUAL_ID`  bigint(20)   NOT NULL                             COMMENT '매뉴얼 ID (FK)',
  `LABEL`      varchar(100) NOT NULL                             COMMENT '항목명 (예: 부서명, 작업인원, 목적)',
  `VALUE_TEXT` text         DEFAULT NULL                         COMMENT '항목 값',
  `SORT_ORDER` int(11)      NOT NULL DEFAULT 0                   COMMENT '표시 순서',

  `CREATED_AT` datetime     NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `CREATED_BY` varchar(50)  DEFAULT NULL                         COMMENT '생성자(로그인ID)',
  `UPDATED_AT` datetime     NOT NULL DEFAULT current_timestamp() COMMENT '수정일시',
  `UPDATED_BY` varchar(50)  DEFAULT NULL                         COMMENT '수정자(로그인ID)',
  `DELETED_YN` varchar(1)   NOT NULL DEFAULT 'N'                 COMMENT '삭제여부(Y/N)',
  `DELETED_AT` datetime     DEFAULT NULL                         COMMENT '삭제일시',
  `DELETED_BY` varchar(50)  DEFAULT NULL                         COMMENT '삭제자(로그인ID)',

  PRIMARY KEY (`META_ID`),
  KEY `IDX_SAFETY_META_MANUAL` (`MANUAL_ID`, `DELETED_YN`, `SORT_ORDER`),
  CONSTRAINT `FK_SAFETY_META_MANUAL` FOREIGN KEY (`MANUAL_ID`) REFERENCES `safety_manual` (`MANUAL_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='안전작업 매뉴얼 머리말 항목(서식마다 다른 라벨-값)';

-- ────────────────────────────────────────────
-- 3) 표 열 정의
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `safety_manual_column` (
  `COLUMN_ID`    bigint(20)   NOT NULL AUTO_INCREMENT              COMMENT '열 ID (PK)',
  `MANUAL_ID`    bigint(20)   NOT NULL                             COMMENT '매뉴얼 ID (FK)',
  `LABEL`        varchar(100) NOT NULL                             COMMENT '열 이름 (예: 작업 순서, 위험요인)',
  `COLUMN_TYPE`  varchar(20)  NOT NULL DEFAULT 'TEXT'              COMMENT '열 유형(TEXT=글, CHECK=체크버튼, PHOTO=사진)',
  `SORT_ORDER`   int(11)      NOT NULL DEFAULT 0                   COMMENT '표시 순서 (왼쪽부터)',
  `WIDTH_WEIGHT` int(11)      NOT NULL DEFAULT 100                 COMMENT '열 폭 비중 (화면에서 가용 폭을 이 비중대로 나눈다)',

  `CREATED_AT`   datetime     NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `CREATED_BY`   varchar(50)  DEFAULT NULL                         COMMENT '생성자(로그인ID)',
  `UPDATED_AT`   datetime     NOT NULL DEFAULT current_timestamp() COMMENT '수정일시',
  `UPDATED_BY`   varchar(50)  DEFAULT NULL                         COMMENT '수정자(로그인ID)',
  `DELETED_YN`   varchar(1)   NOT NULL DEFAULT 'N'                 COMMENT '삭제여부(Y/N)',
  `DELETED_AT`   datetime     DEFAULT NULL                         COMMENT '삭제일시',
  `DELETED_BY`   varchar(50)  DEFAULT NULL                         COMMENT '삭제자(로그인ID)',

  PRIMARY KEY (`COLUMN_ID`),
  KEY `IDX_SAFETY_COLUMN_MANUAL` (`MANUAL_ID`, `DELETED_YN`, `SORT_ORDER`),
  CONSTRAINT `FK_SAFETY_COLUMN_MANUAL` FOREIGN KEY (`MANUAL_ID`) REFERENCES `safety_manual` (`MANUAL_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='안전작업 매뉴얼 상세 표의 열 정의(이름/유형/순서/폭)';

-- ────────────────────────────────────────────
-- 4) 행 x 열 값
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `safety_manual_step_value` (
  `VALUE_ID`   bigint(20)  NOT NULL AUTO_INCREMENT              COMMENT '값 ID (PK)',
  `STEP_ID`    bigint(20)  NOT NULL                             COMMENT '단계(행) ID (FK)',
  `COLUMN_ID`  bigint(20)  NOT NULL                             COMMENT '열 ID (FK)',
  `TEXT_VALUE` text        DEFAULT NULL                         COMMENT 'TEXT 열의 값',
  `CHECKED_YN` varchar(1)  NOT NULL DEFAULT 'N'                 COMMENT 'CHECK 열의 체크 여부(Y/N)',

  `CREATED_AT` datetime    NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `CREATED_BY` varchar(50) DEFAULT NULL                         COMMENT '생성자(로그인ID)',
  `UPDATED_AT` datetime    NOT NULL DEFAULT current_timestamp() COMMENT '수정일시',
  `UPDATED_BY` varchar(50) DEFAULT NULL                         COMMENT '수정자(로그인ID)',
  `DELETED_YN` varchar(1)  NOT NULL DEFAULT 'N'                 COMMENT '삭제여부(Y/N)',
  `DELETED_AT` datetime    DEFAULT NULL                         COMMENT '삭제일시',
  `DELETED_BY` varchar(50) DEFAULT NULL                         COMMENT '삭제자(로그인ID)',

  PRIMARY KEY (`VALUE_ID`),
  CONSTRAINT `UK_SAFETY_STEP_VALUE` UNIQUE (`STEP_ID`, `COLUMN_ID`),
  KEY `IDX_SAFETY_STEP_VALUE_COLUMN` (`COLUMN_ID`),
  CONSTRAINT `FK_SAFETY_VALUE_STEP`   FOREIGN KEY (`STEP_ID`)   REFERENCES `safety_manual_step` (`STEP_ID`),
  CONSTRAINT `FK_SAFETY_VALUE_COLUMN` FOREIGN KEY (`COLUMN_ID`) REFERENCES `safety_manual_column` (`COLUMN_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='안전작업 매뉴얼 상세 표의 행x열 값';

-- ────────────────────────────────────────────
-- 5) 기존 매뉴얼 백필 — 고정 컬럼을 열 정의 + 값으로 옮긴다
--    (열이 이미 하나라도 있는 매뉴얼은 건너뛴다 → 재실행 안전)
-- ────────────────────────────────────────────

-- 5-1) 기존 서식(안전작업 매뉴얼)의 표준 5열을 만든다
INSERT INTO safety_manual_column (MANUAL_ID, LABEL, COLUMN_TYPE, SORT_ORDER, WIDTH_WEIGHT, CREATED_BY)
SELECT m.MANUAL_ID, t.LABEL, t.COLUMN_TYPE, t.SORT_ORDER, t.WIDTH_WEIGHT, 'system'
FROM safety_manual m
CROSS JOIN (
            SELECT '공정 순서(사진)'  AS LABEL, 'PHOTO' AS COLUMN_TYPE, 1 AS SORT_ORDER, 150 AS WIDTH_WEIGHT
  UNION ALL SELECT '공정 순서(설명)',        'TEXT',  2, 270
  UNION ALL SELECT '위험요인',               'TEXT',  3, 400
  UNION ALL SELECT '안전 보호구',            'TEXT',  4, 150
  UNION ALL SELECT '비고',                   'TEXT',  5, 120
) AS t
WHERE m.DELETED_YN = 'N'
  AND NOT EXISTS (SELECT 1 FROM safety_manual_column c WHERE c.MANUAL_ID = m.MANUAL_ID);

-- 5-2) 각 단계의 고정 컬럼 값을 새 구조로 복사
INSERT INTO safety_manual_step_value (STEP_ID, COLUMN_ID, TEXT_VALUE, CHECKED_YN, CREATED_BY)
SELECT s.STEP_ID, c.COLUMN_ID,
       CASE c.LABEL
           WHEN '공정 순서(설명)' THEN s.DESCRIPTION
           WHEN '위험요인'        THEN s.HAZARD
           WHEN '안전 보호구'     THEN s.SAFETY_EQUIPMENT
           WHEN '비고'            THEN s.REMARK
       END,
       'N', 'system'
FROM safety_manual_step s
JOIN safety_manual_column c ON c.MANUAL_ID = s.MANUAL_ID AND c.COLUMN_TYPE = 'TEXT' AND c.DELETED_YN = 'N'
WHERE s.DELETED_YN = 'N'
  AND c.LABEL IN ('공정 순서(설명)', '위험요인', '안전 보호구', '비고')
  AND NOT EXISTS (
      SELECT 1 FROM safety_manual_step_value v WHERE v.STEP_ID = s.STEP_ID AND v.COLUMN_ID = c.COLUMN_ID
  );

SELECT '--- 08_flexible_columns.sql 실행 완료 ---' AS '';
SELECT (SELECT COUNT(*) FROM safety_manual_column     WHERE DELETED_YN = 'N') AS 열정의,
       (SELECT COUNT(*) FROM safety_manual_step_value WHERE DELETED_YN = 'N') AS 셀값,
       (SELECT COUNT(*) FROM safety_manual_meta       WHERE DELETED_YN = 'N') AS 머리말항목;
