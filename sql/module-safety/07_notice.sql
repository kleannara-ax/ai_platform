-- ============================================================
--  07_notice.sql
--  안전작업 매뉴얼 — 공지사항 테이블
--
--  실행: mysql -u platform_user --default-character-set=utf8mb4 platform_db < 07_notice.sql
--
--  화면 좌측(분류 트리 / 방재센터 연락처 아래)에 노출되는 공지 게시물.
--  분류·매뉴얼과 연결되지 않는 독립 테이블이며 FK 가 없다.
--  재실행해도 안전하다 (CREATE TABLE IF NOT EXISTS, DROP/TRUNCATE/DELETE 없음).
-- ============================================================

CREATE TABLE IF NOT EXISTS `safety_notice` (
  `NOTICE_ID`  bigint(20)   NOT NULL AUTO_INCREMENT              COMMENT '공지 ID (PK)',
  `TITLE`      varchar(200) NOT NULL                             COMMENT '공지 제목',
  `CONTENT`    text         DEFAULT NULL                         COMMENT '공지 내용',
  `PINNED_YN`  varchar(1)   NOT NULL DEFAULT 'N'                 COMMENT '상단 고정 여부(Y/N)',

  `CREATED_AT` datetime     NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
  `CREATED_BY` varchar(50)  DEFAULT NULL                         COMMENT '생성자(로그인ID)',
  `UPDATED_AT` datetime     NOT NULL DEFAULT current_timestamp() COMMENT '수정일시',
  `UPDATED_BY` varchar(50)  DEFAULT NULL                         COMMENT '수정자(로그인ID)',
  `DELETED_YN` varchar(1)   NOT NULL DEFAULT 'N'                 COMMENT '삭제여부(Y/N)',
  `DELETED_AT` datetime     DEFAULT NULL                         COMMENT '삭제일시',
  `DELETED_BY` varchar(50)  DEFAULT NULL                         COMMENT '삭제자(로그인ID)',

  PRIMARY KEY (`NOTICE_ID`),
  KEY `IDX_SAFETY_NOTICE_LIST` (`DELETED_YN`, `PINNED_YN`, `CREATED_AT`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='안전작업 매뉴얼 공지사항';

SELECT '--- safety_notice 준비 완료 ---' AS '';
