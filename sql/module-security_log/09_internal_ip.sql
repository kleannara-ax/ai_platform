-- ============================================================
-- module-security_log : 09_internal_ip.sql
-- 관리 > 내부 IP 설정
--   접속 행위 분석의 '외부 IP' 판정 기준. 여기 등록된 대역(사용 중)에 속하지 않는 IP 를 외부 IP 로 표시한다.
--   (기존에는 사설 대역 10/8, 172.16/12, 192.168/16, 127/8, 169.254/16 이 코드에 고정되어 있었음 → 기본값으로 등록)
--
-- 대상 DB : MariaDB 10.11+ (utf8mb4)
-- 재실행  : 가능 (CREATE TABLE IF NOT EXISTS, INSERT IGNORE + UK)
-- 주의    : DROP / TRUNCATE / DELETE / ALTER DROP COLUMN 없음, core 테이블 FK 없음
-- 선행    : 01 ~ 08
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 12. 내부 IP 대역
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_log_internal_ip (
    INTERNAL_IP_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '내부 IP 대역 ID',
    IP_RANGE         VARCHAR(50)   NOT NULL                COMMENT 'IPv4 대역 (CIDR 예: 10.0.0.0/8, 단일 IP 는 /32 로 저장)',
    START_IP_NUM     BIGINT        NOT NULL                COMMENT '대역 시작 IP (숫자, 조회·정렬용)',
    END_IP_NUM       BIGINT        NOT NULL                COMMENT '대역 끝 IP (숫자)',
    IP_NAME          VARCHAR(100)  NOT NULL                COMMENT '대역 이름 (예: 본사 사내망, VPN 대역)',
    DESCRIPTION      VARCHAR(500)  NULL                    COMMENT '설명',
    USE_YN           CHAR(1)       NOT NULL DEFAULT 'Y'    COMMENT '사용 여부 (Y/N, N 이면 외부 IP 판정에서 제외)',
    SORT_ORDER       INT           NOT NULL DEFAULT 0      COMMENT '정렬 순서',

    CREATED_AT       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CREATED_BY       BIGINT        NULL                    COMMENT '생성자 ID (core 사용자 ID 값, FK 없음)',
    UPDATED_AT       DATETIME      NULL                    COMMENT '수정일시',
    UPDATED_BY       BIGINT        NULL                    COMMENT '수정자 ID',
    DELETED_YN       CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '삭제 여부 (Y/N, 소프트 삭제)',
    DELETED_AT       DATETIME      NULL                    COMMENT '삭제일시',
    DELETED_BY       BIGINT        NULL                    COMMENT '삭제자 ID',

    PRIMARY KEY (INTERNAL_IP_ID),
    CONSTRAINT UK_SEC_LOG_INTERNAL_IP_RANGE UNIQUE (IP_RANGE),
    INDEX IDX_SEC_LOG_INTERNAL_IP_USE (DELETED_YN, USE_YN, SORT_ORDER)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='보안로그 내부 IP 대역 (접속 행위 분석의 외부 IP 판정 기준)';

-- ------------------------------------------------------------
-- 기본값: 기존 코드에 고정되어 있던 사설·예약 대역 (운영자가 이미 수정했으면 덮어쓰지 않음)
-- ------------------------------------------------------------
INSERT IGNORE INTO sec_log_internal_ip
    (IP_RANGE, START_IP_NUM, END_IP_NUM, IP_NAME, DESCRIPTION, USE_YN, SORT_ORDER, CREATED_BY)
VALUES
    ('10.0.0.0/8',     167772160,  184549375, '사설 대역 A',       'RFC 1918 사설 IP',             'Y', 10, NULL),
    ('172.16.0.0/12', 2886729728, 2887778303, '사설 대역 B',       'RFC 1918 사설 IP',             'Y', 20, NULL),
    ('192.168.0.0/16',3232235520, 3232301055, '사설 대역 C',       'RFC 1918 사설 IP',             'Y', 30, NULL),
    ('127.0.0.0/8',   2130706432, 2147483647, '루프백',            '같은 서버 안의 접속',           'Y', 40, NULL),
    ('169.254.0.0/16',2851995648, 2852061183, '링크 로컬',         'DHCP 실패 시 자동 할당 대역',   'Y', 50, NULL),
    ('0.0.0.0/8',              0,   16777215, '예약 대역',         '현재 네트워크(0.x.x.x), 실제 접속지 아님', 'Y', 60, NULL);
