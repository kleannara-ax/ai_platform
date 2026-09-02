-- ============================================================
--  module-safety : 안전작업 매뉴얼 테이블 스키마
--
--  ddl-auto=none 이라 JPA 가 표를 만들지 않으므로 반드시 먼저 실행할 것.
--  신규 설치는 이 파일로 표를 만든 뒤 02_menu.sql, 03_perm_code.sql 을 실행한다.
--
--  테이블:
--   - safety_manual_category      분류(자기참조 트리)
--   - safety_manual                매뉴얼 (원본 엑셀의 시트 1개 = 매뉴얼 1개)
--   - safety_manual_step           매뉴얼 단계(순서, 원본 엑셀의 행 1개 = 1건)
--   - safety_manual_step_photo     단계별 사진 (메타데이터만, 실제 파일은 디스크)
-- ============================================================

SET NAMES utf8mb4;

SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `safety_manual_category` (
  `CATEGORY_ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '분류 ID (PK)',
  `NAME` varchar(100) NOT NULL COMMENT '분류명',
  `PARENT_ID` bigint(20) DEFAULT NULL COMMENT '상위 분류 ID (FK, 최상위는 NULL)',
  `SORT_ORDER` int(11) NOT NULL DEFAULT 0 COMMENT '표시 순서',
  `CREATED_AT` datetime NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `UPDATED_AT` datetime NOT NULL DEFAULT current_timestamp() COMMENT '수정일시',
  `CREATED_BY` varchar(50) DEFAULT NULL COMMENT '생성자(로그인ID)',
  `UPDATED_BY` varchar(50) DEFAULT NULL COMMENT '수정자(로그인ID)',
  `DELETED_YN` varchar(1) NOT NULL DEFAULT 'N' COMMENT '삭제여부(Y/N)',
  `DELETED_AT` datetime DEFAULT NULL COMMENT '삭제일시',
  `DELETED_BY` varchar(50) DEFAULT NULL COMMENT '삭제자(로그인ID)',
  PRIMARY KEY (`CATEGORY_ID`),
  KEY `IDX_SAFETY_CATEGORY_PARENT` (`PARENT_ID`),
  CONSTRAINT `FK_SAFETY_CATEGORY_PARENT` FOREIGN KEY (`PARENT_ID`) REFERENCES `safety_manual_category` (`CATEGORY_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='안전작업 매뉴얼 분류(계층형)';

CREATE TABLE IF NOT EXISTS `safety_manual` (
  `MANUAL_ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '매뉴얼 ID (PK)',
  `CATEGORY_ID` bigint(20) NOT NULL COMMENT '분류 ID (FK)',
  `TITLE` varchar(200) NOT NULL COMMENT '매뉴얼 제목(원본 엑셀의 공정명)',
  `SOURCE_FILE_NAME` varchar(255) DEFAULT NULL COMMENT '엑셀 일괄업로드 시 원본 파일명 (직접 등록 시 NULL)',
  `SOURCE_SHEET_NAME` varchar(100) DEFAULT NULL COMMENT '엑셀 일괄업로드 시 원본 시트명 (직접 등록 시 NULL)',
  `SORT_ORDER` int(11) NOT NULL DEFAULT 0 COMMENT '표시 순서',
  `CREATED_AT` datetime NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `UPDATED_AT` datetime NOT NULL DEFAULT current_timestamp() COMMENT '수정일시',
  `CREATED_BY` varchar(50) DEFAULT NULL COMMENT '생성자(로그인ID)',
  `UPDATED_BY` varchar(50) DEFAULT NULL COMMENT '수정자(로그인ID)',
  `DELETED_YN` varchar(1) NOT NULL DEFAULT 'N' COMMENT '삭제여부(Y/N)',
  `DELETED_AT` datetime DEFAULT NULL COMMENT '삭제일시',
  `DELETED_BY` varchar(50) DEFAULT NULL COMMENT '삭제자(로그인ID)',
  PRIMARY KEY (`MANUAL_ID`),
  KEY `IDX_SAFETY_MANUAL_CATEGORY` (`CATEGORY_ID`),
  CONSTRAINT `FK_SAFETY_MANUAL_CATEGORY` FOREIGN KEY (`CATEGORY_ID`) REFERENCES `safety_manual_category` (`CATEGORY_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='안전작업 매뉴얼 (엑셀 시트 1개 = 매뉴얼 1개)';

CREATE TABLE IF NOT EXISTS `safety_manual_step` (
  `STEP_ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '단계 ID (PK)',
  `MANUAL_ID` bigint(20) NOT NULL COMMENT '매뉴얼 ID (FK)',
  `STEP_NO` int(11) NOT NULL COMMENT '원본 엑셀의 No. (화면 표시용 순번)',
  `DESCRIPTION` text DEFAULT NULL COMMENT '공정 순서(설명)',
  `HAZARD` text DEFAULT NULL COMMENT '위험요인',
  `SAFETY_EQUIPMENT` text DEFAULT NULL COMMENT '안전 보호구',
  `REMARK` text DEFAULT NULL COMMENT '비고(개선사항)',
  `SORT_ORDER` int(11) NOT NULL DEFAULT 0 COMMENT '표시 순서(엑셀 행 순서)',
  `CREATED_AT` datetime NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `UPDATED_AT` datetime NOT NULL DEFAULT current_timestamp() COMMENT '수정일시',
  `CREATED_BY` varchar(50) DEFAULT NULL COMMENT '생성자(로그인ID)',
  `UPDATED_BY` varchar(50) DEFAULT NULL COMMENT '수정자(로그인ID)',
  `DELETED_YN` varchar(1) NOT NULL DEFAULT 'N' COMMENT '삭제여부(Y/N)',
  `DELETED_AT` datetime DEFAULT NULL COMMENT '삭제일시',
  `DELETED_BY` varchar(50) DEFAULT NULL COMMENT '삭제자(로그인ID)',
  PRIMARY KEY (`STEP_ID`),
  KEY `IDX_SAFETY_STEP_MANUAL` (`MANUAL_ID`),
  CONSTRAINT `FK_SAFETY_STEP_MANUAL` FOREIGN KEY (`MANUAL_ID`) REFERENCES `safety_manual` (`MANUAL_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='매뉴얼 단계(순서) - 원본 엑셀의 행 1개';

CREATE TABLE IF NOT EXISTS `safety_manual_step_photo` (
  `PHOTO_ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '사진 ID (PK)',
  `STEP_ID` bigint(20) NOT NULL COMMENT '단계 ID (FK)',
  `ORIGINAL_NAME` varchar(255) NOT NULL COMMENT '원본 파일명(엑셀에서 추출 시 자동 생성명)',
  `STORED_NAME` varchar(255) NOT NULL COMMENT '저장 파일명(UUID)',
  `CONTENT_TYPE` varchar(150) DEFAULT NULL COMMENT 'MIME 타입',
  `FILE_SIZE` bigint(20) NOT NULL DEFAULT 0 COMMENT '파일 크기(byte)',
  `SORT_ORDER` int(11) NOT NULL DEFAULT 0 COMMENT '표시 순서(한 단계에 여러 장인 경우)',
  `CREATED_AT` datetime NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `UPDATED_AT` datetime NOT NULL DEFAULT current_timestamp() COMMENT '수정일시',
  `CREATED_BY` varchar(50) DEFAULT NULL COMMENT '생성자(로그인ID)',
  `UPDATED_BY` varchar(50) DEFAULT NULL COMMENT '수정자(로그인ID)',
  `DELETED_YN` varchar(1) NOT NULL DEFAULT 'N' COMMENT '삭제여부(Y/N)',
  `DELETED_AT` datetime DEFAULT NULL COMMENT '삭제일시',
  `DELETED_BY` varchar(50) DEFAULT NULL COMMENT '삭제자(로그인ID)',
  PRIMARY KEY (`PHOTO_ID`),
  KEY `IDX_SAFETY_PHOTO_STEP` (`STEP_ID`),
  CONSTRAINT `FK_SAFETY_PHOTO_STEP` FOREIGN KEY (`STEP_ID`) REFERENCES `safety_manual_step` (`STEP_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='매뉴얼 단계별 사진(메타데이터, 실파일은 디스크)';

SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;
