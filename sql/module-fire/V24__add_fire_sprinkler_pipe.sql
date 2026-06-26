SET NAMES utf8mb4;

-- ============================================================
-- V24: 신규 소방설비 "스프링쿨러 배관" 추가
--   - 스프링쿨러 배관 마스터/점검 이력 테이블 생성
--   - 소방설비 하위 메뉴 FIRE_SPRINKLER_PIPE 등록
--   - 관리자/시설관리자/소방시설관리자 접근권한 부여
-- ============================================================

-- -----------------------------------------------------------------------
-- 스프링쿨러 배관
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_sprinkler_pipe (
    SPRINKLER_PIPE_ID   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '스프링쿨러 배관 ID (PK)',
    SERIAL_NUMBER       VARCHAR(50)  NOT NULL                COMMENT '일련번호 (SPP-000001)',
    BUILDING_NAME       VARCHAR(200) NOT NULL                COMMENT '건물명',
    FLOOR_ID            BIGINT       NOT NULL                COMMENT '층 FK',
    X                   DECIMAL(5,2)                         COMMENT '도면 X 좌표 (%)',
    Y                   DECIMAL(5,2)                         COMMENT '도면 Y 좌표 (%)',
    LOCATION_DESCRIPTION VARCHAR(200)                        COMMENT '위치 설명',
    NOTE                VARCHAR(500)                         COMMENT '비고',
    IMAGE_PATH          VARCHAR(600)                         COMMENT '대표 이미지 경로',
    QR_KEY              VARCHAR(100) NOT NULL                COMMENT 'QR 고정 키 (UUID)',
    IS_ACTIVE           TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '활성 여부',
    CREATED_AT          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (SPRINKLER_PIPE_ID),
    CONSTRAINT UK_SPRINKLER_PIPE_SERIAL UNIQUE (SERIAL_NUMBER),
    CONSTRAINT UK_SPRINKLER_PIPE_QR_KEY UNIQUE (QR_KEY),
    CONSTRAINT FK_SPRINKLER_PIPE_FLOOR FOREIGN KEY (FLOOR_ID) REFERENCES floor(FLOOR_ID) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='스프링쿨러 배관';

-- -----------------------------------------------------------------------
-- 스프링쿨러 배관 점검 이력
-- 점검 방식: INSPECTION_STATUS(상태값) + 체크리스트 이미지 기반 개별 항목별 상태 컬럼
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fire_sprinkler_pipe_inspection (
    INSPECTION_ID              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '점검 ID (PK)',
    SPRINKLER_PIPE_ID          BIGINT        NOT NULL                COMMENT '스프링쿨러 배관 FK',
    INSPECTION_DATE            DATE          NOT NULL                COMMENT '점검일',
    INSPECTION_TIME            TIME                                  COMMENT '점검 시각',
    INSPECTION_STATUS          VARCHAR(30)   NOT NULL DEFAULT 'NORMAL' COMMENT '점검 상태 (NORMAL/MAINTENANCE/FAULTY)',
    CHECKLIST_JSON             LONGTEXT                              COMMENT '체크리스트 JSON',
    IMAGE_PATH                 VARCHAR(600)                          COMMENT '점검 이미지 경로',
    NOTE                       VARCHAR(1000)                         COMMENT '비고',
    PIPE_DAMAGE_STATUS         VARCHAR(30)                           COMMENT '배관 파손여부 확인',
    PIPE_CONNECTION_STATUS     VARCHAR(30)                           COMMENT '배관 연결부 상태 확인',
    PIPE_SUPPORT_STATUS        VARCHAR(30)                           COMMENT '배관 지지대 상태 확인',
    DRAIN_VALVE_STATUS         VARCHAR(30)                           COMMENT '드레인 밸브 누수 상태 확인',
    DRAIN_PIPE_SEALING_STATUS  VARCHAR(30)                           COMMENT '드레인 배관 실리콘 마감상태 확인',
    HEAD_REFLECTOR_STATUS      VARCHAR(30)                           COMMENT '헤드 반사판 탈락여부 확인',
    PRODUCT_CLEARANCE_STATUS   VARCHAR(30)                           COMMENT '헤드로부터 제품 이격거리 60cm 확보 여부',
    INSPECTED_BY_USER_ID       BIGINT                                COMMENT '점검자 ID',
    INSPECTED_BY_NAME          VARCHAR(200)                          COMMENT '점검자 표시명',
    CREATED_AT                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',

    PRIMARY KEY (INSPECTION_ID),
    CONSTRAINT UK_SPRINKLER_PIPE_INSPECTION_DATE UNIQUE (SPRINKLER_PIPE_ID, INSPECTION_DATE),
    CONSTRAINT FK_SPRINKLER_PIPE_INSP_PIPE FOREIGN KEY (SPRINKLER_PIPE_ID) REFERENCES fire_sprinkler_pipe(SPRINKLER_PIPE_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='스프링쿨러 배관 점검 이력';

-- 조회/정렬 보조 인덱스
CREATE INDEX IF NOT EXISTS IDX_SPRINKLER_PIPE_ACTIVE ON fire_sprinkler_pipe(IS_ACTIVE);
CREATE INDEX IF NOT EXISTS IDX_SPRINKLER_PIPE_FLOOR ON fire_sprinkler_pipe(FLOOR_ID);
CREATE INDEX IF NOT EXISTS IDX_SPRINKLER_PIPE_INSP_DATE ON fire_sprinkler_pipe_inspection(INSPECTION_DATE);

-- -----------------------------------------------------------------------
-- 메뉴 등록
-- -----------------------------------------------------------------------
SET @fire_group_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_EQUIPMENT_GROUP' LIMIT 1);
SET @fire_parent_id = (SELECT MENU_ID FROM core_menu WHERE MENU_CODE = 'FIRE_MGMT' LIMIT 1);
SET @sprinkler_pipe_parent_id = COALESCE(@fire_group_id, @fire_parent_id);

INSERT INTO core_menu (MENU_CODE, MENU_NAME, PARENT_ID, MENU_URL, ICON, MENU_TYPE, SORT_ORDER, DESCRIPTION, IS_VISIBLE, IS_ACTIVE, ALLOWED_IPS, CREATED_AT, UPDATED_AT)
SELECT 'FIRE_SPRINKLER_PIPE', '스프링쿨러 배관 목록', @sprinkler_pipe_parent_id, '/fire/sprinkler-pipes', 'sprinkler_pipe', 'MENU', 7, '스프링쿨러 배관 목록 및 점검 관리', 1, 1, NULL, NOW(), NOW()
WHERE @sprinkler_pipe_parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM core_menu WHERE MENU_CODE = 'FIRE_SPRINKLER_PIPE');

UPDATE core_menu
SET MENU_NAME = '스프링쿨러 배관 목록',
    PARENT_ID = @sprinkler_pipe_parent_id,
    MENU_URL = '/fire/sprinkler-pipes',
    ICON = 'sprinkler_pipe',
    MENU_TYPE = 'MENU',
    SORT_ORDER = 7,
    DESCRIPTION = '스프링쿨러 배관 목록 및 점검 관리',
    IS_VISIBLE = 1,
    IS_ACTIVE = 1,
    UPDATED_AT = NOW()
WHERE @sprinkler_pipe_parent_id IS NOT NULL
  AND MENU_CODE = 'FIRE_SPRINKLER_PIPE';

-- 기존 소방설비 하위 메뉴 정렬 보정: 스프링쿨러 배관은 소방펌프 다음, QR은 마지막
UPDATE core_menu SET SORT_ORDER = 1, UPDATED_AT = NOW() WHERE MENU_CODE = 'FIRE_EXTINGUISHER';
UPDATE core_menu SET SORT_ORDER = 2, UPDATED_AT = NOW() WHERE MENU_CODE = 'FIRE_HYDRANT';
UPDATE core_menu SET SORT_ORDER = 3, UPDATED_AT = NOW() WHERE MENU_CODE = 'FIRE_RECEIVER';
UPDATE core_menu SET SORT_ORDER = 4, UPDATED_AT = NOW() WHERE MENU_CODE = 'FIRE_PUMP';
UPDATE core_menu SET SORT_ORDER = 5, UPDATED_AT = NOW() WHERE MENU_CODE = 'FIRE_SPRINKLER_PIPE';
UPDATE core_menu SET SORT_ORDER = 6, UPDATED_AT = NOW() WHERE MENU_CODE = 'FIRE_QR';

-- -----------------------------------------------------------------------
-- 역할별 접근권한 등록
-- -----------------------------------------------------------------------
INSERT INTO core_role_menu (ROLE, MENU_ID, CREATED_AT)
SELECT r.ROLE, m.MENU_ID, NOW()
FROM (
    SELECT 'ROLE_ADMIN' AS ROLE
    UNION ALL SELECT 'ROLE_FACILITY_MANAGER'
    UNION ALL SELECT 'ROLE_FIRE_MANAGER'
) r
JOIN core_menu m ON m.MENU_CODE = 'FIRE_SPRINKLER_PIPE'
ON DUPLICATE KEY UPDATE core_role_menu.CREATED_AT = core_role_menu.CREATED_AT;
