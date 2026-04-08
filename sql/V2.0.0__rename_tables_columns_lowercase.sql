-- ============================================================
--  V2.0.0 : 테이블명·컬럼명 소문자 변환 마이그레이션
--  MariaDB 10.11+ (utf8mb4)
--  최종 갱신: 2026-04-08
--
--  대상 테이블:
--    CORE_USER → core_user
--    CORE_MENU → core_menu
--    CORE_PERMISSION → core_permission
--    CORE_ROLE_MENU → core_role_menu
--    CORE_ROLE_PERMISSION → core_role_permission
--    MOD_CODE_DETAIL → code_detail
--    MOD_CODE_GROUP → code_group
--    MOD_USER_PROFILE → user_profile
--
--  참고:
--    - MariaDB에서 컬럼명은 기본적으로 대소문자 구분하지 않으므로
--      ALTER TABLE ... CHANGE COLUMN 으로 컬럼명을 소문자로 변경합니다.
--    - 테이블명은 lower_case_table_names 설정에 따라 다를 수 있습니다.
--      lower_case_table_names=0 (Linux 기본): 대소문자 구분 → RENAME 필요
--      lower_case_table_names=1: 이미 소문자로 저장됨 → RENAME 불필요
--    - 이 스크립트는 lower_case_table_names=0 환경 기준으로 작성되었습니다.
-- ============================================================

-- ── 1. 테이블명 변경 (RENAME TABLE) ──
-- 주의: FK 참조가 있으므로 순서를 주의해야 합니다
-- FK 의존 순서: profile → user, dept → dept(self), role_menu → menu, role_perm → perm

-- 외래키 체크 임시 해제
SET FOREIGN_KEY_CHECKS = 0;

-- CORE 테이블
RENAME TABLE CORE_USER TO core_user;
RENAME TABLE CORE_MENU TO core_menu;
RENAME TABLE CORE_PERMISSION TO core_permission;
RENAME TABLE CORE_ROLE_MENU TO core_role_menu;
RENAME TABLE CORE_ROLE_PERMISSION TO core_role_permission;

-- MODULE 테이블
RENAME TABLE MOD_CODE_GROUP TO code_group;
RENAME TABLE MOD_CODE_DETAIL TO code_detail;
RENAME TABLE MOD_USER_PROFILE TO user_profile;

-- 외래키 체크 복구
SET FOREIGN_KEY_CHECKS = 1;


-- ── 2. core_user 컬럼명 변경 ──
ALTER TABLE core_user
    CHANGE COLUMN USER_ID       user_id       BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '사용자 ID (PK)',
    CHANGE COLUMN LOGIN_ID      login_id      VARCHAR(50)  NOT NULL                 COMMENT '로그인 ID',
    CHANGE COLUMN PASSWORD      password      VARCHAR(255) NOT NULL                 COMMENT '비밀번호 (BCrypt)',
    CHANGE COLUMN USER_NAME     user_name     VARCHAR(100) NOT NULL                 COMMENT '사용자명',
    CHANGE COLUMN EMAIL         email         VARCHAR(200) NULL                     COMMENT '이메일',
    CHANGE COLUMN PHONE         phone         VARCHAR(20)  NULL                     COMMENT '전화번호',
    CHANGE COLUMN ROLE          role          VARCHAR(30)  NOT NULL DEFAULT 'ROLE_USER' COMMENT '역할',
    CHANGE COLUMN ENABLED       enabled       TINYINT(1)   NOT NULL DEFAULT 1       COMMENT '활성화 여부',
    CHANGE COLUMN CREATED_AT    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CHANGE COLUMN UPDATED_AT    updated_at    DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    CHANGE COLUMN CREATED_BY    created_by    VARCHAR(50)  NULL                     COMMENT '생성자',
    CHANGE COLUMN UPDATED_BY    updated_by    VARCHAR(50)  NULL                     COMMENT '수정자';


-- ── 3. core_menu 컬럼명 변경 ──
ALTER TABLE core_menu
    CHANGE COLUMN MENU_ID      menu_id      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '메뉴 ID (PK)',
    CHANGE COLUMN MENU_NAME    menu_name    VARCHAR(100) NOT NULL                 COMMENT '메뉴명',
    CHANGE COLUMN MENU_CODE    menu_code    VARCHAR(50)  NOT NULL                 COMMENT '메뉴 코드',
    CHANGE COLUMN PARENT_ID    parent_id    BIGINT       NULL                     COMMENT '상위 메뉴 ID',
    CHANGE COLUMN MENU_URL     menu_url     VARCHAR(255) NULL                     COMMENT '메뉴 URL',
    CHANGE COLUMN ICON         icon         VARCHAR(50)  NULL                     COMMENT '아이콘 식별자',
    CHANGE COLUMN SORT_ORDER   sort_order   INT          NULL     DEFAULT 0       COMMENT '정렬 순서',
    CHANGE COLUMN MENU_TYPE    menu_type    VARCHAR(20)  NULL     DEFAULT 'MENU'  COMMENT '메뉴 유형',
    CHANGE COLUMN IS_VISIBLE   is_visible   TINYINT(1)   NULL     DEFAULT 1       COMMENT '사이드바 표시 여부',
    CHANGE COLUMN IS_ACTIVE    is_active    TINYINT(1)   NULL     DEFAULT 1       COMMENT '활성화 여부',
    CHANGE COLUMN DESCRIPTION  description  VARCHAR(200) NULL                     COMMENT '설명',
    CHANGE COLUMN ALLOWED_IPS  allowed_ips  VARCHAR(1000) NULL                    COMMENT '허용 IP 목록',
    CHANGE COLUMN CREATED_AT   created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CHANGE COLUMN UPDATED_AT   updated_at   DATETIME     NULL     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시';


-- ── 4. core_permission 컬럼명 변경 ──
ALTER TABLE core_permission
    CHANGE COLUMN PERM_ID      perm_id      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '권한 ID (PK)',
    CHANGE COLUMN PERM_CODE    perm_code    VARCHAR(50)  NOT NULL                 COMMENT '권한 코드',
    CHANGE COLUMN PERM_NAME    perm_name    VARCHAR(100) NOT NULL                 COMMENT '권한명',
    CHANGE COLUMN DESCRIPTION  description  VARCHAR(200) NULL                     COMMENT '설명',
    CHANGE COLUMN IS_ACTIVE    is_active    TINYINT(1)   NULL     DEFAULT 1       COMMENT '활성화 여부',
    CHANGE COLUMN CREATED_AT   created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CHANGE COLUMN UPDATED_AT   updated_at   DATETIME     NULL     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시';


-- ── 5. core_role_menu 컬럼명 변경 ──
ALTER TABLE core_role_menu
    CHANGE COLUMN ROLE_MENU_ID role_menu_id BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'PK',
    CHANGE COLUMN ROLE         role         VARCHAR(30)  NOT NULL                 COMMENT '역할',
    CHANGE COLUMN MENU_ID      menu_id      BIGINT       NOT NULL                 COMMENT '메뉴 ID',
    CHANGE COLUMN CREATED_AT   created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시';


-- ── 6. core_role_permission 컬럼명 변경 ──
ALTER TABLE core_role_permission
    CHANGE COLUMN ROLE_PERM_ID role_perm_id BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'PK',
    CHANGE COLUMN ROLE         role         VARCHAR(30)  NOT NULL                 COMMENT '역할',
    CHANGE COLUMN PERM_ID      perm_id      BIGINT       NOT NULL                 COMMENT '권한 ID',
    CHANGE COLUMN CREATED_AT   created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시';


-- ── 7. code_group 컬럼명 변경 ──
ALTER TABLE code_group
    CHANGE COLUMN GROUP_ID     group_id     BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '그룹 ID (PK)',
    CHANGE COLUMN GROUP_CODE   group_code   VARCHAR(50)  NOT NULL                 COMMENT '그룹 코드',
    CHANGE COLUMN GROUP_NAME   group_name   VARCHAR(100) NOT NULL                 COMMENT '그룹명',
    CHANGE COLUMN DESCRIPTION  description  VARCHAR(200) NULL                     COMMENT '설명',
    CHANGE COLUMN IS_ACTIVE    is_active    TINYINT(1)   NOT NULL DEFAULT 1       COMMENT '활성 여부',
    CHANGE COLUMN SORT_ORDER   sort_order   INT          NOT NULL DEFAULT 0       COMMENT '정렬 순서',
    CHANGE COLUMN CREATED_AT   created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CHANGE COLUMN UPDATED_AT   updated_at   DATETIME     NULL     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    CHANGE COLUMN CREATED_BY   created_by   VARCHAR(50)  NULL                     COMMENT '생성자',
    CHANGE COLUMN UPDATED_BY   updated_by   VARCHAR(50)  NULL                     COMMENT '수정자';


-- ── 8. code_detail 컬럼명 변경 ──
ALTER TABLE code_detail
    CHANGE COLUMN CODE_ID      code_id      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '코드 ID (PK)',
    CHANGE COLUMN GROUP_ID     group_id     BIGINT       NOT NULL                 COMMENT '그룹 ID (FK)',
    CHANGE COLUMN CODE         code         VARCHAR(50)  NOT NULL                 COMMENT '코드값',
    CHANGE COLUMN CODE_NAME    code_name    VARCHAR(100) NOT NULL                 COMMENT '코드명',
    CHANGE COLUMN DESCRIPTION  description  VARCHAR(200) NULL                     COMMENT '설명',
    CHANGE COLUMN EXTRA_VALUE1 extra_value1 VARCHAR(200) NULL                     COMMENT '부가값1',
    CHANGE COLUMN EXTRA_VALUE2 extra_value2 VARCHAR(200) NULL                     COMMENT '부가값2',
    CHANGE COLUMN IS_ACTIVE    is_active    TINYINT(1)   NOT NULL DEFAULT 1       COMMENT '활성 여부',
    CHANGE COLUMN SORT_ORDER   sort_order   INT          NOT NULL DEFAULT 0       COMMENT '정렬 순서',
    CHANGE COLUMN CREATED_AT   created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CHANGE COLUMN UPDATED_AT   updated_at   DATETIME     NULL     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시';


-- ── 9. user_profile 컬럼명 변경 ──
ALTER TABLE user_profile
    CHANGE COLUMN PROFILE_ID   profile_id   BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '프로필 ID (PK)',
    CHANGE COLUMN USER_ID      user_id      BIGINT       NOT NULL                 COMMENT '사용자 ID',
    CHANGE COLUMN DEPT_CODE    dept_code    VARCHAR(50)  NULL                     COMMENT '부서 코드',
    CHANGE COLUMN POSITION     position     VARCHAR(50)  NULL                     COMMENT '직위',
    CHANGE COLUMN JOB_TITLE    job_title    VARCHAR(100) NULL                     COMMENT '직책',
    CHANGE COLUMN EMPLOYEE_NO  employee_no  VARCHAR(20)  NULL                     COMMENT '사번',
    CHANGE COLUMN JOIN_DATE    join_date    DATE         NULL                     COMMENT '입사일',
    CHANGE COLUMN OFFICE_PHONE office_phone VARCHAR(20)  NULL                     COMMENT '사무실 전화번호',
    CHANGE COLUMN INTERNAL_EXT internal_ext VARCHAR(10)  NULL                     COMMENT '내선번호',
    CHANGE COLUMN CREATED_AT   created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    CHANGE COLUMN UPDATED_AT   updated_at   DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시';


-- ============================================================
--  완료 확인
-- ============================================================
-- SELECT TABLE_NAME, COLUMN_NAME
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN ('core_user', 'core_menu', 'core_permission',
--                      'core_role_menu', 'core_role_permission',
--                      'code_group', 'code_detail', 'user_profile')
-- ORDER BY TABLE_NAME, ORDINAL_POSITION;
