-- ============================================================
-- 모듈: module-dailyreport (세부공장일보)
-- 파일: 01_schema.sql
-- 설명: AI 플랫폼 통합형 DDL
--       - 기존 플랫폼 테이블(core_user, core_menu, core_menu_permission) 스텁 포함
--       - 일보 업무 테이블 5개 (daily_report, daily_report_table, daily_report_cell,
--         daily_report_remark, daily_report_image)
--       - ★ 신규: daily_report_cell_auth (셀 단위 접근 권한 관리)
--       - ★★ 신규(2026-08): daily_batchjob (게시판 재업로드 요청 큐)
-- ============================================================
-- 변경이력:
--   v1.0: 최초 작성
--   v2.0: HTML 원본 기준 4개 표 재구성
--   v3.0: AI 플랫폼 통합 — 3계층 권한 체계
--     - 1계층: core_menu_permission → '세부공장일보 입력' 페이지 접근 권한
--     - 2계층: daily_report_cell_auth → 셀 단위 입력 권한 (관리자 설정)
--     - 3계층: core_menu_permission → '세부공장일보 컬럼관리' 관리 페이지 접근 권한
--     ※ 레거시 daily_report_cell_permission 제거 → daily_report_cell_auth로 대체
--   v3.1: core_user/core_menu 스텁을 V2.0.0 운영 스키마로 동기화
--     - core_user: IS_ACTIVE→enabled, DEPARTMENT/POSITION 제거, phone/created_by/updated_by 추가
--     - core_menu: V2.0.0 소문자 컬럼명 + is_visible/allowed_ips 추가
--     - core_menu_permission: 코드 미참조 명시 (CellAuth 기반으로 전환 완료)
--   v3.2 (현재, 2026-08): daily_batchjob 추가 — 오전 8:05 이후 수정 시
--     공장일보/세부공장일보 게시판 재업로드가 필요함을 별도 PC 배치 시스템에
--     알리는 요청 큐 (11_add_daily_batchjob.sql 참고)
-- ============================================================

USE dailyreport_dev;

-- ────────────────────────────────────────────
-- 0-1. core_user 스텁 (플랫폼 기존 테이블)
--      ★ V2.0.0 운영 스키마 기준 (소문자 컬럼명)
--      ※ 운영과 불일치 방지: 이 스텁은 개발/테스트 환경 초기화용
--        DEPARTMENT, POSITION 컬럼은 운영에 없음 (user_profile 테이블로 분리됨)
--        IS_ACTIVE → enabled, ROLE DEFAULT → 'ROLE_USER'
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS core_user (
    user_id     BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '사용자 ID (PK)',
    login_id    VARCHAR(50)     NOT NULL                 COMMENT '로그인 ID',
    password    VARCHAR(255)    NOT NULL                 COMMENT '비밀번호 (BCrypt)',
    user_name   VARCHAR(100)    NOT NULL                 COMMENT '사용자명',
    email       VARCHAR(200)    NULL                     COMMENT '이메일',
    phone       VARCHAR(20)     NULL                     COMMENT '전화번호',
    role        VARCHAR(30)     NOT NULL DEFAULT 'ROLE_USER' COMMENT '역할 (ROLE_ADMIN/ROLE_USER)',
    enabled     TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '활성화 여부',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at  DATETIME        NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    created_by  VARCHAR(50)     NULL                     COMMENT '생성자',
    updated_by  VARCHAR(50)     NULL                     COMMENT '수정자',
    PRIMARY KEY (user_id),
    UNIQUE KEY UK_CORE_USER_LOGIN (login_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[플랫폼 코어] 사용자 마스터 (V2.0.0 운영 스키마 기준)';


-- ────────────────────────────────────────────
-- 0-2. core_menu 스텁 (플랫폼 기존 테이블)
--      ★ V2.0.0 운영 스키마 기준 (소문자 컬럼명)
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS core_menu (
    menu_id      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '메뉴 ID (PK)',
    menu_name    VARCHAR(100) NOT NULL                 COMMENT '메뉴명',
    menu_code    VARCHAR(50)  NOT NULL                 COMMENT '메뉴 코드',
    parent_id    BIGINT       NULL                     COMMENT '상위 메뉴 ID',
    menu_url     VARCHAR(255) NULL                     COMMENT '메뉴 URL',
    icon         VARCHAR(50)  NULL                     COMMENT '아이콘 식별자',
    sort_order   INT          NULL     DEFAULT 0       COMMENT '정렬 순서',
    menu_type    VARCHAR(20)  NULL     DEFAULT 'MENU'  COMMENT '메뉴 유형',
    is_visible   TINYINT(1)   NULL     DEFAULT 1       COMMENT '사이드바 표시 여부',
    is_active    TINYINT(1)   NULL     DEFAULT 1       COMMENT '활성화 여부',
    description  VARCHAR(200) NULL                     COMMENT '설명',
    allowed_ips  VARCHAR(1000) NULL                    COMMENT '허용 IP 목록',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at   DATETIME     NULL     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (menu_id),
    UNIQUE KEY UK_CORE_MENU_CODE (menu_code),
    INDEX IDX_MENU_PARENT (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[플랫폼 코어] 메뉴 마스터 (V2.0.0 운영 스키마 기준)';


-- ────────────────────────────────────────────
-- 0-3. core_menu_permission 스텁 (플랫폼 기존 테이블)
--      ※ Phase 4에서 MenuPermissionService가 이 테이블 의존을 제거함
--        현재 코드에서 참조하지 않음 (CellAuth 기반으로 전환 완료)
--        운영에 이 테이블이 있든 없든 동작에 영향 없음
--        V2.0.0 마이그레이션 대상 아님 — 원본 유지
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS core_menu_permission (
    PERM_ID     BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '권한 ID (PK)',
    USER_ID     BIGINT          NOT NULL                 COMMENT '대상 사용자',
    MENU_ID     BIGINT          NOT NULL                 COMMENT '대상 메뉴',
    CAN_READ    TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '조회 권한',
    CAN_WRITE   TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '쓰기 권한',
    CAN_DELETE  TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '삭제 권한',
    CAN_ADMIN   TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '관리 권한',
    GRANTED_BY  BIGINT                                   COMMENT '권한 부여자',
    CREATED_AT  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT  DATETIME                 ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (PERM_ID),
    UNIQUE KEY UK_MENU_PERM_USER_MENU (USER_ID, MENU_ID),
    INDEX IDX_PERM_USER (USER_ID),
    INDEX IDX_PERM_MENU (MENU_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='[플랫폼 코어] 사용자별 메뉴 접근 권한 (코드 미참조 — 레거시 스텁)';


-- ════════════════════════════════════════════
-- ★★ 이하 모듈 전용 테이블 ★★
-- ════════════════════════════════════════════

-- ────────────────────────────────────────────
-- 1. daily_report — 일보 마스터
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_report (
    REPORT_ID   BIGINT          NOT NULL AUTO_INCREMENT,
    REPORT_DATE DATE            NOT NULL                  COMMENT '일보 대상 날짜',
    TITLE       VARCHAR(200)    NOT NULL                  COMMENT '일보 제목',
    STATUS      VARCHAR(20)     NOT NULL DEFAULT 'DRAFT'  COMMENT 'DRAFT/SUBMITTED/CONFIRMED',
    CREATED_BY  BIGINT          NOT NULL                  COMMENT '최초 작성자 (core_user FK)',
    UPDATED_BY  BIGINT                                    COMMENT '최종 수정자',
    CREATED_AT  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT  DATETIME                 ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (REPORT_ID),
    UNIQUE KEY UK_DAILY_REPORT_DATE (REPORT_DATE),
    INDEX IDX_REPORT_STATUS (STATUS),
    INDEX IDX_REPORT_CREATED (CREATED_AT)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='세부공장일보 마스터';


-- ────────────────────────────────────────────
-- 2. daily_report_table — 표 메타
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_report_table (
    TABLE_ID    BIGINT          NOT NULL AUTO_INCREMENT,
    REPORT_ID   BIGINT          NOT NULL,
    TABLE_CODE  VARCHAR(50)     NOT NULL  COMMENT '표 코드 (TBL_PRODUCTION_INDEX 등)',
    TABLE_NAME  VARCHAR(100)    NOT NULL  COMMENT '표 제목 (주요 생산 지표 현황 등)',
    SORT_ORDER  INT             NOT NULL  COMMENT '정렬 순서 (1~4)',
    ROW_COUNT   INT             NOT NULL  COMMENT '표 행 수',
    COL_COUNT   INT             NOT NULL  COMMENT '표 열 수',
    CREATED_AT  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (TABLE_ID),
    INDEX IDX_RPT_TABLE_REPORT (REPORT_ID),
    CONSTRAINT FK_RPT_TABLE_REPORT
        FOREIGN KEY (REPORT_ID) REFERENCES daily_report (REPORT_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='일보 표 메타';


-- ────────────────────────────────────────────
-- 3. daily_report_cell — 표 셀 데이터
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_report_cell (
    CELL_ID         BIGINT          NOT NULL AUTO_INCREMENT,
    TABLE_ID        BIGINT          NOT NULL,
    ROW_INDEX       INT             NOT NULL  COMMENT '행 인덱스',
    COL_INDEX       INT             NOT NULL  COMMENT '열 인덱스',
    EXCEL_COORD     VARCHAR(10)               COMMENT '엑셀 좌표 (B5, O10 등)',
    CELL_VALUE      VARCHAR(2000)             COMMENT '셀 값',
    CELL_TYPE       VARCHAR(20)     NOT NULL  COMMENT 'HEADER/DATA/FORMULA/READONLY',
    CELL_LABEL      VARCHAR(200)              COMMENT '헤더 라벨',
    DATA_FORMAT     VARCHAR(20)               COMMENT 'TEXT/NUMBER/PERCENT/DATE',
    FORMULA         VARCHAR(500)              COMMENT '수식',
    INPUT_CYCLE     VARCHAR(20)     NOT NULL DEFAULT 'NONE' COMMENT 'DAILY/WEEKLY/MONTHLY/NONE (레거시)',
    FREQ_CODE       VARCHAR(20)               COMMENT 'daily/monthly/yearly/event/none',
    FREQ_LABEL      VARCHAR(50)               COMMENT '매일/매월/매년/발생 시',
    OWNER_IDS       VARCHAR(200)              COMMENT '★ 담당자 로그인ID 목록 (공백 구분, cell_auth 기반 동기화)',
    OWNER_NAMES     VARCHAR(500)              COMMENT '담당자 이름 (표시용)',
    IS_LOCKED       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '잠금 여부',
    ROW_SPAN        INT             DEFAULT 1 COMMENT 'rowspan (병합 행)',
    COL_SPAN        INT             DEFAULT 1 COMMENT 'colspan (병합 열)',
    LAST_EDITOR_ID  BIGINT                    COMMENT '최종 입력자',
    LAST_EDITED_AT  DATETIME                  COMMENT '최종 입력 시각',
    CREATED_AT      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (CELL_ID),
    UNIQUE KEY UK_CELL_POSITION (TABLE_ID, ROW_INDEX, COL_INDEX),
    INDEX IDX_CELL_TABLE (TABLE_ID),
    INDEX IDX_CELL_COORD (EXCEL_COORD),
    INDEX IDX_CELL_OWNER (OWNER_IDS(50)),
    CONSTRAINT FK_CELL_TABLE
        FOREIGN KEY (TABLE_ID) REFERENCES daily_report_table (TABLE_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='일보 표 셀 데이터';


-- ────────────────────────────────────────────
-- 4. ★★ daily_report_cell_auth (신규) — 셀 단위 접근 권한
--    관리자가 '세부공장일보 컬럼관리' 페이지에서 설정
--    → OWNER_IDS / OWNER_NAMES와 동기화
--
--    ★★★ 다중 주기 지원(2026-07, 06_allow_multi_freq_cell_auth.sql 참고):
--    과거에는 (USER_ID, TABLE_CODE)에 UNIQUE 제약이 있어 한 사용자가 같은 표에서
--    서로 다른 주기(예: 매일 담당 셀 + 매년 담당 셀)를 나눠서 담당할 수 없었다.
--    실제로 이런 경우가 존재하므로 UNIQUE 제약을 두지 않는다 — 한 사용자가 같은
--    표에 대해 여러 CellAuth 행(좌표 그룹별 서로 다른 FREQ_CODE)을 가질 수 있다.
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_report_cell_auth (
    AUTH_ID         BIGINT          NOT NULL AUTO_INCREMENT,
    USER_ID         BIGINT          NOT NULL  COMMENT '담당 사용자 (core_user FK)',
    TABLE_CODE      VARCHAR(50)     NOT NULL  COMMENT '대상 표 코드',
    CELL_COORDS     TEXT            NOT NULL  COMMENT '담당 셀 좌표 목록 (JSON배열: ["B7","C7","D7"])',
    FREQ_CODE       VARCHAR(20)     NOT NULL DEFAULT 'daily' COMMENT 'daily/monthly/yearly/event',
    FREQ_LABEL      VARCHAR(50)               COMMENT '주기 라벨 (매일/매월 등)',
    IS_ACTIVE       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '활성 여부',
    GRANTED_BY      BIGINT                    COMMENT '권한 부여자 (관리자)',
    DESCRIPTION     VARCHAR(300)              COMMENT '설명 (예: 수율 담당, 에너지 담당)',
    CREATED_AT      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT      DATETIME                 ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (AUTH_ID),
    INDEX IDX_CELL_AUTH_USER (USER_ID),
    INDEX IDX_CELL_AUTH_TABLE (TABLE_CODE)
    -- ★ UNIQUE KEY UK_CELL_AUTH_USER_TABLE (USER_ID, TABLE_CODE) 제거됨 (2026-07)
    --   한 사용자가 같은 표에서 여러 주기 그룹을 동시에 담당할 수 있도록 허용
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='★ 신규 — 셀 단위 접근 권한 (관리자 설정, 접근권한 페이지에서 관리)';


-- ────────────────────────────────────────────
-- 5. daily_report_remark — 특이사항
-- ────────────────────────────────────────────
-- ★★ 2026-07 개편(08_restructure_special_note_by_division.sql): 리포트당
-- 자유 텍스트 1건이던 구조를 "사업부별 5행"(제지/화장지/패드/사고·안전사고/기타)
-- 구조로 변경. TABLE_CODE='TBL_SPECIAL_NOTE'(가상 표코드), CATEGORY를
-- 사업부 코드(PAPER/TISSUE/PAD/SAFETY/ETC)로 사용. daily_report_cell_auth와
-- 동일한 방식(TABLE_CODE='TBL_SPECIAL_NOTE', CELL_COORDS에 사업부 코드)으로
-- 담당자를 배정하여 "담당자 미배정 사업부 행은 편집 불가"를 셀과 동일하게 적용.
CREATE TABLE IF NOT EXISTS daily_report_remark (
    REMARK_ID   BIGINT          NOT NULL AUTO_INCREMENT,
    REPORT_ID   BIGINT          NOT NULL,
    TABLE_CODE  VARCHAR(50)               COMMENT '관련 표 코드 (TBL_SPECIAL_NOTE=특이사항)',
    CATEGORY    VARCHAR(30)     NOT NULL DEFAULT 'GENERAL' COMMENT '사업부 코드: PAPER/TISSUE/PAD/SAFETY/ETC',
    CONTENT     TEXT            NOT NULL  COMMENT '특이사항 내용',
    SORT_ORDER  INT             NOT NULL DEFAULT 1,
    CREATED_BY  BIGINT          NOT NULL  COMMENT '최초 작성자',
    UPDATED_BY  BIGINT                    COMMENT '최종 수정자 (core_user FK)',
    CREATED_AT  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT  DATETIME                 ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (REMARK_ID),
    INDEX IDX_REMARK_REPORT (REPORT_ID),
    CONSTRAINT FK_REMARK_REPORT
        FOREIGN KEY (REPORT_ID) REFERENCES daily_report (REPORT_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='일보 특이사항 (사업부별 5행: 제지/화장지/패드/사고·안전사고/기타)';


-- ────────────────────────────────────────────
-- 6. daily_report_image — 이미지 첨부
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_report_image (
    IMAGE_ID      BIGINT          NOT NULL AUTO_INCREMENT,
    REPORT_ID     BIGINT          NOT NULL,
    ORIGINAL_NAME VARCHAR(255)    NOT NULL  COMMENT '원본 파일명',
    STORED_PATH   VARCHAR(500)    NOT NULL  COMMENT '저장 경로',
    FILE_SIZE     BIGINT          NOT NULL DEFAULT 0     COMMENT '파일 크기 (bytes)',
    CONTENT_TYPE  VARCHAR(100)    NOT NULL                COMMENT 'MIME 타입 (image/jpeg 등)',
    DESCRIPTION   VARCHAR(500)                            COMMENT '이미지 설명',
    TABLE_CODE    VARCHAR(50)                             COMMENT '관련 표 코드 (NULL=전체)',
    SORT_ORDER    INT             NOT NULL DEFAULT 1      COMMENT '정렬 순서',
    UPLOADED_BY   BIGINT          NOT NULL                COMMENT '업로드 사용자 (core_user FK)',
    CREATED_AT    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    PRIMARY KEY (IMAGE_ID),
    INDEX IDX_IMAGE_REPORT (REPORT_ID),
    CONSTRAINT FK_IMAGE_REPORT
        FOREIGN KEY (REPORT_ID) REFERENCES daily_report (REPORT_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='일보 이미지 첨부';


-- ────────────────────────────────────────────
-- 7. ★★ daily_batchjob (신규, 2026-08) — 게시판 재업로드 요청 큐
--    오전 8:05 이후 사람이 값을 "수정"하면, 별도 PC에서 동작하는 배치
--    시스템이 5초 주기로 이 테이블을 훑어 공장일보/세부공장일보 게시글을
--    다시 게시(재업로드)하도록 요청 행을 남긴다.
--    (11_add_daily_batchjob.sql 참고 — 상세 배경 설명)
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_batchjob (
    SEQ_NO       BIGINT        NOT NULL AUTO_INCREMENT                COMMENT '순번',
    BATCH_DATE   VARCHAR(8)    NOT NULL                                COMMENT '일자 (YYYYMMDD)',
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


-- ────────────────────────────────────────────
-- 검증: 테이블 생성 확인
-- ────────────────────────────────────────────
SELECT TABLE_NAME, TABLE_COMMENT
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'dailyreport_dev'
 ORDER BY TABLE_NAME;

SELECT '=== 01_schema.sql 실행 완료 (10 테이블) ===' AS message;
