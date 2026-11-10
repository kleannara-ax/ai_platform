-- ============================================================
--  module-cckb : CCKB 후면 색상검사 테이블 스키마
--
--  ddl-auto=none 이라 JPA 가 표를 만들지 않으므로 반드시 먼저 실행할 것.
--  신규 설치는 이 파일로 표를 만든 뒤 02_menu.sql, 03_perm_code.sql 을 실행한다.
--
--  출처: CCKB_후면색상검사_AI플랫폼_데이터테이블설계_수정본_V2.xlsx (5개 시트)
--  테이블:
--   - inspection_standard   판정 기준값 이력 (기준 L*/a*/b* + 정상/불량 임계값)
--   - judgment_code         판정 코드 공통코드 (NORMAL/BOUNDARY/DEFECT)
--   - image                 검사/기준 이미지 메타데이터 (실 파일은 디스크, dailyreport 방식)
--   - inspection_record     검사 1건 이력 (PK: 날짜+일련번호 형식 CHAR(10))
--   - standard_audit_log    기준값 변경 감사 로그
--
--  ★ 스키마 보정 사항 (엑셀 원안 대비):
--    - `image` 테이블: 엑셀 원안에는 DELETED_YN 컬럼이 누락되어 있었음(다른 4개 테이블은 보유).
--      다른 테이블과의 소프트 삭제 표준 일관성을 위해 DELETED_YN 컬럼을 추가함.
--    - `image.FILE_URL`: 엑셀 원안 설명은 "R2/오브젝트 스토리지 경로"였으나, 사용자 지시에 따라
--      dailyreport 모듈과 동일하게 "다운로드 API 경로"(예: /cckb-api/images/files/{filename})를
--      저장하는 용도로 사용한다 (실제 파일은 로컬 디스크 uploads/module_cckb/images 에 저장).
-- ============================================================

SET NAMES utf8mb4;

SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

-- ────────────────────────────────────────────────────────────
-- 1) inspection_standard : 판정 기준값 이력
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `inspection_standard` (
  `STANDARD_ID`       bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '기준값 레코드 고유ID (PK)',
  `STANDARD_VERSION`  varchar(10)   NOT NULL DEFAULT 'v1.0' COMMENT '기준값 개정 버전 (예: v1.0, v1.1)',
  `REF_L`             decimal(5,2)  NOT NULL COMMENT '기준 명도값 (예: 59.2)',
  `REF_A`             decimal(5,2)  NOT NULL COMMENT '기준 적록값 (예: 9.0)',
  `REF_B`             decimal(5,2)  NOT NULL COMMENT '기준 청황값 (예: 21.3)',
  `NORMAL_DE00_MAX`   decimal(4,2)  NOT NULL DEFAULT 2.00 COMMENT '이 값 미만이면 ΔE00 조건 정상',
  `NORMAL_DL_MAX`     decimal(4,2)  NOT NULL DEFAULT 2.00 COMMENT '이 값 이하면 ΔL* 조건 정상',
  `NORMAL_DA_MAX`     decimal(4,2)  NOT NULL DEFAULT 2.00 COMMENT '이 값 이하면 Δa* 조건 정상',
  `NORMAL_DB_MAX`     decimal(4,2)  NOT NULL DEFAULT 2.00 COMMENT '이 값 이하면 Δb* 조건 정상',
  `DEFECT_DE00_MIN`   decimal(4,2)  NOT NULL DEFAULT 4.00 COMMENT '이 값 초과시 ΔE00 조건 불량',
  `DEFECT_DL_MAX`     decimal(4,2)  NOT NULL DEFAULT 2.00 COMMENT '이 값 초과시 ΔL* 조건 불량',
  `DEFECT_DA_MAX`     decimal(4,2)  NOT NULL DEFAULT 2.00 COMMENT '이 값 초과시 Δa* 조건 불량',
  `DEFECT_DB_MAX`     decimal(4,2)  NOT NULL DEFAULT 2.00 COMMENT '이 값 초과시 Δb* 조건 불량',
  `EFFECTIVE_FROM`    date          NOT NULL COMMENT '해당 기준값 적용 시작일',
  `EFFECTIVE_TO`      date          DEFAULT NULL COMMENT 'NULL이면 현재까지 유효',
  `IS_ACTIVE`         char(1)       NOT NULL DEFAULT 'Y' COMMENT '현재 활성 기준인지 여부(Y/N)',
  `REMARKS`           varchar(200)  DEFAULT NULL COMMENT '변경 사유, 특이사항 등',
  `CREATED_AT`        datetime      NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `CREATED_BY`        bigint(20)    DEFAULT NULL COMMENT '생성자(core 사용자 ID, FK 아님)',
  `UPDATED_AT`        datetime      DEFAULT NULL COMMENT '수정일시',
  `UPDATED_BY`        bigint(20)    DEFAULT NULL COMMENT '수정자(core 사용자 ID, FK 아님)',
  `DELETED_YN`        char(1)       NOT NULL DEFAULT 'N' COMMENT '삭제여부(Y/N)',
  `DELETED_AT`        datetime      DEFAULT NULL COMMENT '삭제일시',
  `DELETED_BY`        bigint(20)    DEFAULT NULL COMMENT '삭제자(core 사용자 ID, FK 아님)',
  PRIMARY KEY (`STANDARD_ID`),
  KEY `IDX_CCKB_STD_ACTIVE` (`IS_ACTIVE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='CCKB 판정 기준값 이력';

-- ────────────────────────────────────────────────────────────
-- 2) judgment_code : 판정 코드 공통코드 (NORMAL/BOUNDARY/DEFECT)
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `judgment_code` (
  `JUDGMENT_CODE`     varchar(10)   NOT NULL COMMENT '판정코드: NORMAL/BOUNDARY/DEFECT (PK)',
  `JUDGMENT_NAME_KR`  varchar(10)   NOT NULL COMMENT '정상/경계/불량',
  `COLOR_HEX`         varchar(10)   DEFAULT NULL COMMENT 'UI 표시 색상 (예: #63f0aa)',
  `SORT_ORDER`        int(11)       DEFAULT NULL COMMENT '화면 표시 순서',
  `DESCRIPTION`       varchar(150)  DEFAULT NULL COMMENT '판정 기준 요약 설명',
  `CREATED_AT`        datetime      NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `CREATED_BY`        bigint(20)    DEFAULT NULL COMMENT '생성자(core 사용자 ID, FK 아님)',
  `UPDATED_AT`        datetime      DEFAULT NULL COMMENT '수정일시',
  `UPDATED_BY`        bigint(20)    DEFAULT NULL COMMENT '수정자(core 사용자 ID, FK 아님)',
  `DELETED_YN`        char(1)       NOT NULL DEFAULT 'N' COMMENT '삭제여부(Y/N)',
  `DELETED_AT`        datetime      DEFAULT NULL COMMENT '삭제일시',
  `DELETED_BY`        bigint(20)    DEFAULT NULL COMMENT '삭제자(core 사용자 ID, FK 아님)',
  PRIMARY KEY (`JUDGMENT_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='CCKB 판정 코드 공통코드';

-- ────────────────────────────────────────────────────────────
-- 3) inspection_record : 검사 1건 이력 (PK: 날짜+일련번호, 예: 2611060001)
--    judgment_code/inspection_standard 보다 먼저 참조되므로 image 보다 앞서 생성.
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `inspection_record` (
  `INSPECTION_ID`            char(10)      NOT NULL COMMENT '검사 1건 고유ID (PK, YYMMDD+4자리 순번)',
  `INSPECTION_DATETIME`      datetime      NOT NULL DEFAULT current_timestamp() COMMENT '판정 실행 시각',
  `STANDARD_ID`              bigint(20)    NOT NULL COMMENT 'inspection_standard 참조 (FK)',
  `MEASURED_L`               decimal(6,2)  NOT NULL COMMENT '비교 ROI 평균 명도',
  `MEASURED_A`               decimal(6,2)  NOT NULL COMMENT '비교 ROI 평균 적록값',
  `MEASURED_B`               decimal(6,2)  NOT NULL COMMENT '비교 ROI 평균 청황값',
  `DELTA_L`                  decimal(6,2)  NOT NULL COMMENT '측정L - 기준L',
  `DELTA_A`                  decimal(6,2)  NOT NULL COMMENT '측정a - 기준a',
  `DELTA_B`                  decimal(6,2)  NOT NULL COMMENT '측정b - 기준b',
  `DELTA_E00`                decimal(6,2)  NOT NULL COMMENT 'CIEDE2000 색차 (주 판정값)',
  `JUDGMENT_CODE`            varchar(10)   NOT NULL COMMENT 'judgment_code 참조 (FK)',
  `JUDGMENT_REASON`          varchar(200)  DEFAULT NULL COMMENT '예: Δa* 기준 초과로 경계',
  `IS_REINSPECTED`           char(1)       NOT NULL DEFAULT 'N' COMMENT '동일 제품 재검사 건인지(Y/N)',
  `ORIGINAL_INSPECTION_ID`   char(10)      DEFAULT NULL COMMENT '재검사 시 최초 검사 참조 (self, FK 제약 없이 ID만 보관)',
  `CREATED_AT`               datetime      NOT NULL DEFAULT current_timestamp() COMMENT '레코드 생성 시각',
  `CREATED_BY`               bigint(20)    DEFAULT NULL COMMENT '생성자(core 사용자 ID, FK 아님)',
  `UPDATED_AT`               datetime      DEFAULT NULL COMMENT '수정일시',
  `UPDATED_BY`               bigint(20)    DEFAULT NULL COMMENT '수정자(core 사용자 ID, FK 아님)',
  `DELETED_YN`               char(1)       NOT NULL DEFAULT 'N' COMMENT '해당 검사 이력이 삭제 처리한 건인지(Y/N)',
  `DELETED_AT`               datetime      DEFAULT NULL COMMENT '삭제일시',
  `DELETED_BY`               bigint(20)    DEFAULT NULL COMMENT '삭제자(core 사용자 ID, FK 아님)',
  PRIMARY KEY (`INSPECTION_ID`),
  KEY `IDX_CCKB_RECORD_STANDARD` (`STANDARD_ID`),
  KEY `IDX_CCKB_RECORD_JUDGMENT` (`JUDGMENT_CODE`),
  KEY `IDX_CCKB_RECORD_DATETIME` (`INSPECTION_DATETIME`),
  CONSTRAINT `FK_CCKB_RECORD_STANDARD` FOREIGN KEY (`STANDARD_ID`) REFERENCES `inspection_standard` (`STANDARD_ID`),
  CONSTRAINT `FK_CCKB_RECORD_JUDGMENT` FOREIGN KEY (`JUDGMENT_CODE`) REFERENCES `judgment_code` (`JUDGMENT_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='CCKB 검사 이력';

-- ────────────────────────────────────────────────────────────
-- 4) image : 검사/기준 이미지 메타데이터
--    ★ DELETED_YN 컬럼은 엑셀 원안에 없었으나 보정으로 추가함.
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `image` (
  `IMAGE_ID`       bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '이미지 고유ID (PK)',
  `INSPECTION_ID`  char(10)      DEFAULT NULL COMMENT 'inspection_record 참조 (FK, 기준사진은 NULL 가능)',
  `IMAGE_TYPE`     varchar(10)   NOT NULL COMMENT 'REF(기준) / CMP(비교)',
  `FILE_NAME`      varchar(200)  NOT NULL COMMENT '업로드 원본 파일명',
  `FILE_URL`       varchar(300)  NOT NULL COMMENT '다운로드 API 경로 (물리 경로 아님, 예: /cckb-api/images/files/{uuid}.jpg)',
  `WIDTH_PX`       int(11)       DEFAULT NULL COMMENT '이미지 원본 가로 크기',
  `HEIGHT_PX`      int(11)       DEFAULT NULL COMMENT '이미지 원본 세로 크기',
  `FILE_SIZE_KB`   int(11)       DEFAULT NULL COMMENT '업로드 파일 용량',
  `UPLOADED_BY`    bigint(20)    DEFAULT NULL COMMENT '업로드한 사용자 ID 값(core 사용자 ID, FK 아님)',
  `UPLOADED_AT`    datetime      NOT NULL DEFAULT current_timestamp() COMMENT '업로드 시각',
  `CREATED_AT`     datetime      NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `CREATED_BY`     bigint(20)    DEFAULT NULL COMMENT '생성자(core 사용자 ID, FK 아님)',
  `UPDATED_AT`     datetime      DEFAULT NULL COMMENT '수정일시',
  `UPDATED_BY`     bigint(20)    DEFAULT NULL COMMENT '수정자(core 사용자 ID, FK 아님)',
  `DELETED_YN`     char(1)       NOT NULL DEFAULT 'N' COMMENT '삭제여부(Y/N) — ★ 스키마 보정으로 추가',
  `DELETED_AT`     datetime      DEFAULT NULL COMMENT '삭제일시',
  `DELETED_BY`     bigint(20)    DEFAULT NULL COMMENT '삭제자(core 사용자 ID, FK 아님)',
  PRIMARY KEY (`IMAGE_ID`),
  KEY `IDX_CCKB_IMAGE_INSPECTION` (`INSPECTION_ID`),
  CONSTRAINT `FK_IMAGE_INSPECTION` FOREIGN KEY (`INSPECTION_ID`) REFERENCES `inspection_record` (`INSPECTION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='CCKB 검사/기준 이미지 메타데이터';

-- ────────────────────────────────────────────────────────────
-- 5) standard_audit_log : 기준값 변경 감사 로그
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `standard_audit_log` (
  `AUDIT_ID`        bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '변경 이력 고유ID (PK)',
  `STANDARD_ID`     bigint(20)    NOT NULL COMMENT 'inspection_standard 참조 (FK)',
  `CHANGED_FIELD`   varchar(50)   NOT NULL COMMENT '예: ref_a, normal_de00_max',
  `OLD_VALUE`       varchar(50)   DEFAULT NULL COMMENT '변경 전 데이터',
  `NEW_VALUE`       varchar(50)   NOT NULL COMMENT '변경 후 데이터',
  `CHANGED_BY`      bigint(20)    NOT NULL COMMENT '변경을 수행한 사용자 ID 값(core 사용자 ID, FK 아님)',
  `CHANGE_REASON`   varchar(200)  DEFAULT NULL COMMENT '변경 사유/근거',
  `CHANGED_AT`      datetime      NOT NULL DEFAULT current_timestamp() COMMENT '변경 발생 시각',
  `CREATED_AT`      datetime      NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `CREATED_BY`      bigint(20)    DEFAULT NULL COMMENT '생성자(core 사용자 ID, FK 아님)',
  `UPDATED_AT`      datetime      DEFAULT NULL COMMENT '수정일시',
  `UPDATED_BY`      bigint(20)    DEFAULT NULL COMMENT '수정자(core 사용자 ID, FK 아님)',
  `DELETED_AT`      datetime      DEFAULT NULL COMMENT '삭제일시',
  `DELETED_BY`      bigint(20)    DEFAULT NULL COMMENT '삭제자(core 사용자 ID, FK 아님)',
  PRIMARY KEY (`AUDIT_ID`),
  KEY `IDX_CCKB_AUDITLOG_STANDARD` (`STANDARD_ID`),
  CONSTRAINT `FK_AUDITLOG_STANDARD` FOREIGN KEY (`STANDARD_ID`) REFERENCES `inspection_standard` (`STANDARD_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='CCKB 기준값 변경 감사 로그';

SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;
